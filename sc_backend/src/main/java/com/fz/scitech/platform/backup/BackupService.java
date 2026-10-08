package com.fz.scitech.platform.backup;

import com.fz.scitech.common.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份子系统(spec §八/决策#34-①):
 * 流程 = mysqldump 全库 → 与 upload/ 目录一起 zip → backup/backup_yyyyMMdd_HHmmss.zip → 保留 keep 份删最旧。
 * 双入口:定时(@Scheduled cron 可配,默认每周一 02:30)与手动(异步,立即返回);
 * 两入口共用同一 synchronized 核心方法 + AtomicBoolean 快速返回,防并发重入。
 *
 * 安全要点:
 * ① DB 口令经临时 --defaults-extra-file 传给 mysqldump(选 cnf 方案而非 MYSQL_PWD 环境变量:
 *    环境变量同机进程可嗅探),不进命令行/日志/异常消息,用后即删;
 * ② dump 带 --default-character-set=utf8mb4(踩坑②同源,防乱码)+ --single-transaction
 *    (InnoDB 一致性快照,不锁表)+ --routines --triggers;
 * ③ SQL 直落临时文件(--result-file),不走进程管道,规避 Windows 管道编码坑;
 * ④ 备份文件名只认 backup_yyyyMMdd_HHmmss.zip 白名单(正则无路径分隔符),下载/删除前
 *    再做 canonical 路径二次校验,防路径遍历。
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 备份文件名白名单(无路径分隔符,天然防遍历) */
    private static final Pattern NAME_OK = Pattern.compile("backup_\\d{8}_\\d{6}\\.zip");
    /** jdbc:mysql://host[:port]/db?params */
    private static final Pattern JDBC_URL = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?/]+)");
    private static final long DUMP_TIMEOUT_MINUTES = 10;

    private final Environment env;
    private final String mysqldumpPath;
    private final String backupDir;
    private final String uploadDir;
    private final String cron;
    private final int keep;

    /** 备份执行中标记(手动快速返回"进行中"+定时跳过) */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 手动触发线程(单线程池:与定时共用 running 标记,不会排队堆积) */
    private final ExecutorService manualExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-manual");
        t.setDaemon(true);
        return t;
    });

    /** 启动自检结果:true=mysqldump 可执行 */
    private volatile boolean available;
    private volatile String selfCheckMsg;

    public BackupService(Environment env,
                         @Value("${sc.mysqldump-path:./mysqldump.exe}") String mysqldumpPath,
                         @Value("${sc.backup-dir:./backup}") String backupDir,
                         @Value("${sc.upload-dir:./upload}") String uploadDir,
                         @Value("${sc.backup-cron:0 30 2 * * MON}") String cron,
                         @Value("${sc.backup-keep:8}") int keep) {
        this.env = env;
        this.mysqldumpPath = mysqldumpPath;
        this.backupDir = backupDir;
        this.uploadDir = uploadDir;
        this.cron = cron;
        this.keep = keep;
    }

    /**
     * 启动自检:mysqldump --version 探测;失败仅置灰功能,不阻断应用启动(spec §八)。
     * 输出由后台守护线程排空(同 runMysqldump 惯用法):若 mysqldump-path 误指到
     * 等待 stdin 的可执行文件,主线程同步 readAllBytes 会永久阻塞 @PostConstruct,
     * 整个应用起不来——故 waitFor 超时必须真正可达;并主动关闭子进程 stdin,
     * 让这类进程尽快收到 EOF 自行退出而非干等超时。
     */
    @PostConstruct
    void selfCheck() {
        try {
            Process p = new ProcessBuilder(mysqldumpPath, "--version").start();
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outDrain = drainAsync(p.getInputStream(), out);
            Thread errDrain = drainAsync(p.getErrorStream(), err);
            p.getOutputStream().close();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("探测超时");
            }
            outDrain.join(5000);
            errDrain.join(5000);
            if (p.exitValue() != 0) {
                throw new IllegalStateException("退出码 " + p.exitValue() + ":" + firstLine(out.toString() + err.toString()));
            }
            available = true;
            selfCheckMsg = firstLine(out.toString() + err.toString());
            log.info("备份子系统自检通过:mysqldump={},{}", mysqldumpPath, selfCheckMsg);
        } catch (Exception e) {
            available = false;
            selfCheckMsg = "找不到 mysqldump 可执行文件:" + mysqldumpPath
                    + ",请配置 sc.mysqldump-path 指向正确路径,或将 mysqldump.exe 放入程序根目录";
            log.warn("备份子系统自检失败,功能置灰:{}", e.getMessage());
        }
    }

    /** 后台守护线程排空进程输出:主线程不同步读流(EOF 不到会永久阻塞),超时逻辑才可达 */
    private Thread drainAsync(InputStream in, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try {
                sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignore) {
                // 进程被超时强杀时流随之关闭,忽略
            }
        }, "backup-proc-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 状态(前端按钮禁用与提示依据);含 running 供手动触发后轮询 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("message", available ? "就绪(" + selfCheckMsg + ")" : selfCheckMsg);
        m.put("cron", cron);
        m.put("keep", keep);
        m.put("running", running.get());
        return m;
    }

    /**
     * 手动触发(仅 admin 调用):异步执行立即返回。
     * true=已开始;false=已有备份进行中(不排队,前端轮询列表看结果)。
     */
    public boolean triggerManual() {
        if (!available) {
            throw new BizException("备份组件不可用:" + selfCheckMsg);
        }
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        manualExecutor.submit(() -> {
            try {
                backupOnce("手动");
            } catch (Exception e) {
                log.error("手动备份失败", e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** 定时备份(cron 可配);进行中/自检失败则本次跳过 */
    @Scheduled(cron = "${sc.backup-cron:0 30 2 * * MON}")
    public void scheduledBackup() {
        if (!available) {
            log.warn("定时备份跳过:{}", selfCheckMsg);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("定时备份跳过:已有备份进行中");
            return;
        }
        try {
            backupOnce("定时");
        } catch (Exception e) {
            log.error("定时备份失败", e);
        } finally {
            running.set(false);
        }
    }

    /**
     * 备份核心(定时与手动共用,synchronized 加锁防并发重入):
     * mysqldump → 临时 sql → 连同 upload/ 打 zip(先写 .part 再原子改名,列表只见成品)→ 保留策略。
     */
    private synchronized void backupOnce(String trigger) throws Exception {
        File dir = new File(backupDir);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new BizException("备份目录创建失败:" + backupDir);
        }
        Path tmp = Files.createTempDirectory("sc-backup-");
        String zipName = "backup_" + LocalDateTime.now().format(TS) + ".zip";
        try {
            Path cnf = tmp.resolve("dump.cnf");
            Path sql = tmp.resolve("data.sql");
            writeDefaultsCnf(cnf);
            runMysqldump(cnf, sql);

            Path part = dir.toPath().resolve(zipName + ".part");
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(part)), StandardCharsets.UTF_8)) {
                zos.putNextEntry(new ZipEntry("data.sql"));
                Files.copy(sql, zos);
                zos.closeEntry();
                zipUploadDir(zos);
            }
            Files.move(part, dir.toPath().resolve(zipName), StandardCopyOption.ATOMIC_MOVE);
            log.info("[{}] 备份完成:{}/{}(含全库 SQL+upload)", trigger, backupDir, zipName);
        } finally {
            deleteRecursively(tmp); // cnf 内含口令,必须清理
        }
        retainOld(dir);
    }

    /** 解析 spring.datasource.url/username/password → 临时 [client] cnf(host/port/user/password) */
    private void writeDefaultsCnf(Path cnf) throws IOException {
        String url = env.getProperty("spring.datasource.url", "");
        Matcher m = JDBC_URL.matcher(url);
        if (!m.find()) {
            throw new BizException("无法解析数据源 URL(spring.datasource.url)");
        }
        String host = m.group(1);
        String port = m.group(2) != null ? m.group(2) : "3306";
        String user = env.getProperty("spring.datasource.username", "root");
        String password = env.getProperty("spring.datasource.password", "");
        StringBuilder sb = new StringBuilder();
        sb.append("[client]\n")
          .append("host=").append(host).append("\n")
          .append("port=").append(port).append("\n")
          .append("user=").append(user).append("\n");
        if (!password.isEmpty()) {
            // 口令加引号并转义 \ 与 ",防 # 空格等字符截断;内容不落日志
            sb.append("password=\"").append(password.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
        }
        Files.writeString(cnf, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 执行 mysqldump(注意:--defaults-extra-file 必须是第一个选项才会被读取;
     * --result-file 直落文件,绕开进程管道的编码坑)。
     * stderr 用 drainAsync 后台排空(不排空会撑爆管道缓冲区卡死进程),主线程不同步读流——
     * 否则进程挂起(如数据库网络停滞)时 EOF 永不到来,waitFor 超时永远执行不到,
     * running 标记永久为 true,备份功能锁死至重启。
     * 失败时异常只带退出码与 mysqldump 自身输出(不含口令)。
     */
    private void runMysqldump(Path cnf, Path sql) throws Exception {
        String db = dbName();
        List<String> cmd = Arrays.asList(
                mysqldumpPath,
                "--defaults-extra-file=" + cnf,
                "--default-character-set=utf8mb4",
                "--single-transaction",
                "--routines",
                "--triggers",
                "--result-file=" + sql,
                db);
        Process p = new ProcessBuilder(cmd).start();
        StringBuilder err = new StringBuilder();
        Thread errDrain = drainAsync(p.getErrorStream(), err);
        if (!p.waitFor(DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new BizException("mysqldump 执行超时(>" + DUMP_TIMEOUT_MINUTES + " 分钟),已终止");
        }
        errDrain.join(5000);
        if (p.exitValue() != 0) {
            throw new BizException("mysqldump 失败(退出码 " + p.exitValue() + "):" + firstLine(err.toString()));
        }
        if (!Files.exists(sql) || Files.size(sql) == 0) {
            throw new BizException("mysqldump 未产出 SQL 文件");
        }
    }

    private String dbName() {
        Matcher m = JDBC_URL.matcher(env.getProperty("spring.datasource.url", ""));
        if (!m.find()) {
            throw new BizException("无法解析数据源 URL(spring.datasource.url)");
        }
        return m.group(3);
    }

    /** upload/ 整目录递归入 zip(条目名 upload/相对路径,正斜杠;中文文件名由 UTF-8 zip 旗标保障) */
    private void zipUploadDir(ZipOutputStream zos) throws IOException {
        Path root = new File(uploadDir).toPath();
        if (!Files.isDirectory(root)) {
            return; // upload 尚未产生(全新部署),zip 只含 SQL
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted().toList();
        }
        for (Path p : paths) {
            String name = "upload/" + root.relativize(p).toString().replace('\\', '/');
            if (Files.isDirectory(p)) {
                if (!name.endsWith("/")) {
                    zos.putNextEntry(new ZipEntry(name + "/"));
                    zos.closeEntry();
                }
            } else {
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }

    /** 保留 keep 份:按文件名排序(=时间序)删最旧(spec §八:默认 8 份) */
    private void retainOld(File dir) {
        File[] zips = dir.listFiles((d, n) -> NAME_OK.matcher(n).matches());
        if (zips == null || zips.length <= keep) {
            return;
        }
        List<File> sorted = Arrays.stream(zips).sorted(Comparator.comparing(File::getName)).toList();
        for (int i = 0; i + keep < sorted.size(); i++) {
            File old = sorted.get(i);
            log.info("备份保留 {} 份,删除最旧:{}({})", keep, old.getName(), old.delete() ? "成功" : "失败");
        }
    }

    /** 备份列表(新→旧),前端展示文件名/时间/大小 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        File dir = new File(backupDir);
        File[] zips = dir.isDirectory() ? dir.listFiles((d, n) -> NAME_OK.matcher(n).matches()) : null;
        if (zips == null) {
            return out;
        }
        Arrays.sort(zips, Comparator.comparing(File::getName).reversed());
        for (File f : zips) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            m.put("size", f.length());
            m.put("time", LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()),
                    ZoneId.systemDefault()).format(SHOW));
            out.add(m);
        }
        return out;
    }

    /** 供下载/删除用文件定位:白名单正则 + canonical 二次校验(仅 backup/ 目录直下 zip) */
    public File backupFile(String name) {
        if (name == null || !NAME_OK.matcher(name).matches()) {
            throw new BizException("非法文件名,仅允许 backup_yyyyMMdd_HHmmss.zip");
        }
        File dir = new File(backupDir);
        File f = new File(dir, name);
        try {
            // 双保险:规范化后必须仍位于备份目录直下(防路径遍历,如 ../data.sql)
            if (!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) {
                throw new BizException("非法文件名");
            }
        } catch (IOException e) {
            throw new BizException("文件路径解析失败");
        }
        return f;
    }

    /** 删除备份(物理删,区别于附件逻辑删——spec §八);不存在视为失败给用户明确提示 */
    public void delete(String name) {
        File f = backupFile(name);
        if (!f.isFile()) {
            throw new BizException("备份文件不存在");
        }
        if (!f.delete()) {
            throw new BizException("备份文件删除失败");
        }
        log.info("备份已删除:{}", f.getName());
    }

    private void deleteRecursively(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("备份临时目录清理失败:{}", root);
        }
    }

    private String firstLine(String s) {
        return s == null ? "" : s.strip().lines().findFirst().orElse("");
    }

    @PreDestroy
    void shutdown() {
        manualExecutor.shutdownNow();
    }
}
