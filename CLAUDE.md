# 项目:科研管理平台(scitech)

> 前身:`D:\纺专\横向项目\科技处\`(纵向统计系统,阶段0-4b,已停用待 tag `archive-v1-final` 归档只读)。本项目按 35 条决策重起:横向项目流程为首个业务模块,平台化架构支撑后续 6+ 模块。设计文档:**`科技处/docs/superpowers/specs/2026-10-05-横向项目流程与PDF产出-design.md`**(必读);执行计划:`科技处/docs/superpowers/plans/2026-10-07-横向平台-N1底座建库-N2PDF子系统.md`

## 概述
- **本期**:平台底座+横向项目全流程(填报→待签PDF→线下盖章→回扫→科技处确认)+PDF 子系统(LibreOffice 便携版+poi-tl+JODConverter)+归档
- 系统不代签不代章;纸面意见留空;流程留痕走 rp_hx_flow_record
- 模块化架构(决策#32):平台层(system/auth/template/backup/notify/attachment)与业务层(hx)分包,业务模块不得互相 import;表前缀 sys_/rp_{module}_;字典 dict_type 带模块前缀
- kind=1 纵向仅预留,纵向模块需求确认后独立立项(决策#31)

## 技术栈
Java 17 + Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + MySQL 8 + jjwt 0.12.6 + poi-tl 1.12.2 + JODConverter 4.4.7 + poi 5.2.5 + pdfbox;Vue 3 + Vite + Element Plus + Pinia

## 环境(均在 D 盘)
- JDK 17.0.20:`D:\dev\jdk-17`(JAVA_HOME);Maven 3.9.16:`D:\dev\maven`(阿里云镜像,仓库 D:\dev\maven-repo)
- MySQL 8.0.46:服务 MySQL80,root 密码 `Research@2026`,项目库 **scitech_db**(utf8mb4)
- npm 源 npmmirror(安装加 `--registry=https://registry.npmmirror.com`)
- 旧仓库 `D:\纺专\横向项目\科技处\` = 只读搬运源+文档源,**不修改**

## 启动方式
```bash
# 后端:命令行一律用 package+java -jar(见下方踩坑①);java 必须在仓库根执行——office-home/upload-dir/backup-dir 均 ./ 相对 cwd,在 sc_backend/ 下启动会「LibreOffice 未找到,PDF 降级」
cd sc_backend && D:/dev/maven/bin/mvn package -DskipTests -q && cd .. && D:/dev/jdk-17/bin/java -jar sc_backend/target/sc-backend-0.1.0.jar
# 接口 http://localhost:8080/api(context-path=/api);dev 建号 admin/admin123、teacher/teacher123

# 前端
cd sc_frontend && npm run dev   # http://localhost:5173,代理 /api→8080

# prod 启动(全环境变量,零代码改动)
DB_URL='jdbc:mysql://...' DB_USER=.. DB_PASSWORD=.. SC_JWT_SECRET='≥32字符' SC_CORS_ORIGIN='https://...' java -jar sc-backend-0.1.0.jar --spring.profiles.active=prod
```

## 数据库
- 建表/初值走 `spring.sql.init`(每次启动执行;**encoding 已显式 UTF-8**,勿删):schema.sql 带 IF NOT EXISTS 幂等;data.sql=**首启种子**口径(Task26 起)——sys_dict 固定 id INSERT IGNORE(管理页停用/调序/改名重启不重置),sys_config 分两类:代码所有值型开关(如 deliver_pdf_visible)先删后插重置,admin 可改业务参数(如 hx_mgmt_fee_rate 管理费比例)INSERT IGNORE 不重置
- 14 表:sys_user/dept/dict/config/notify/template + rp_project(15列主表)/rp_hx_project/rp_hx_flow/rp_hx_flow_record/rp_member/rp_budget/rp_attachment/rp_funding_record
- 金额一律 DECIMAL(14,2) 元(决策#25);逻辑删除 deleted;状态迁移一律条件更新 WHERE status=旧值(决策#34-②)
- 快照导出位置:`docs/db/scitech_db_full.sql`(N5 时 mysqldump)

## ⚠️ 踩坑记录(本项目实测)
1. **`mvn spring-boot:run` 在本机中文路径下必挂**(fork 子进程 classpath 编码损坏→ClassNotFoundException,cmd 包装也救不了)。命令行一律 `mvn package -DskipTests + java -jar`;IDE 开发用 VS Code F5(不受影响)
2. **spring.sql.init 读脚本默认平台编码(GBK)**:UTF-8 脚本写入即乱码(EFBFFD 替换字符)。`spring.sql.init.encoding: UTF-8` 必须显式配置(已配,注意它在 spring: 块下,别插错层级)
3. **jjwt 弱密钥 fail-fast**:HMAC secret <32 字符直接启动失败(WeakKeyException 240bits)。上线清单:SC_JWT_SECRET 必须 ≥32 字符
4. el-input 原生 input 无 type attribute(仅 DOM property):浏览器自动化选择器用 `input[placeholder=...]` 而非 `input[type=text]`
5. **poi-tl 行循环(LoopRowTableRenderPolicy)在 vMerge 大表上删行会引发 XmlValueDisconnectedException** 殃及后续文本标签——模板一律用固定行占位符(m1name..m6duty),不做行循环
6. **勾选符号字形坑**:☑(U+2611) 宋体无字形→LibreOffice 豆腐块;■(U+25A0) 可用但用户要求打勾样式——**最终方案 √(U+221A,GB2312/宋体全覆盖)+□(U+25A1)**,拼法参照 SampleData.checks;换符号后必重跑渲染冒烟人工核对
7. **原 .doc 制式表在 LibreOffice 下空表即 3 页**(Word 排版更松):已做版式压缩(边距900/600+表格pct100%+空段清理+简介进度行高2000),四模板 2/2/2/1 页;模板改版后必须重跑 RenderSmokeTest 验页数。Task18 恢复原版空行数后登记/验收实测 4 页,maxPages 已放宽至 4
8. **VS Code JDT 语言服务(redhat.java)与命令行 mvn 混写 target/classes**:mvn 增量编译可能捡到 JDT 的失败编译桩(运行时报 `Unresolved compilation problems` 而 mvn 编译"无错")——遇到先 `mvn clean` 重跑再看真实错误
9. **git-bash 在中文路径下后台派生 Windows 子进程,argv/cwd 全废(前台正常)**:`( ... & )` 后台 job 的含中文参数被截成盘符根、重定向与 $! PID 映射也丢——脚本里起 mysqld/java 一律 PowerShell Start-Process,路径走 ASCII junction(如 D:\scitech-demo-stage→dist-demo),进程 PID 用文件交接(Out-File)不走 `$( )` 命令替换管道
10. **生成 .bat 必须 GBK+CRLF 双条件**(iconv -t GBK 后 sed 补 \r):LF 行尾令 cmd 括号块/标签整体错乱(碎片当命令执行);bat 内等待一律 `powershell -NoProfile -Command "Start-Sleep ..."`(经 bash 起 cmd 时 PATH 里 GNU timeout/ping 会遮蔽 System32 同名);mysqld 不带 --console 会自带守护父子进程(杀不掉/复活占端口),启动命令行必须带 --console
11. **原版表单红色提示样式经克隆链污染渲染值**:原版 .doc 空格/示例的段落标记 rPr 带 color=FF0000(验收单成员表 41 处),Task16 克隆参照链(格内文字 run→段落标记 rPr)忠实把红色带给占位符 run→poi-tl 渲染值整块红字。修复=make_templates.py save() 末道工序 strip_red_colors 文档级清红(run/段落标记/空 run 一律删 FF0000)+check_fidelity 新增 R4(全文档零 FF0000)+RenderModelBuilderTest 四表渲染断言;标题深灰 1B1B1B 等非红格式不受影响

## LibreOffice 部署包(652MB,决策#37;N6 起横向四表停用渲染,仅 platform 模板管理页使用——裁剪/移除时机待模板模块整体退役时另议)
- 版本 26.2.6.3:官方 MSI(USTC 镜像下载)+ `msiexec /a` 管理解包(免安装) + `scripts/trim_libreoffice.sh` 裁剪(1537→652MB,渲染与裁剪前逐字一致,冷热启动均验)
- 裁剪守则:只裁他语UI/图标/词典/剪贴画;核心dll/Fonts/icudt/soffice.cfg 绝不动;升级版本后重跑裁剪+冷启动验证
- 模板字体=宋体(Windows 系统自带);**Linux 部署前需确认中文字体**(无宋体时评估保留 Noto CJK 或系统装字体)
- 手动 soffice 转换(preview/调试)与运行中的应用进程池会抢 2002 端口——并发时偶发转换失败,错开执行

## 会话安全(决策#35,开发期已全部落地)
token 带 uid claim;AuthInterceptor 每请求按 uid 查库(停用立即 401、role 取库中最新);secret 环境变量化;dev 建号/prod 零默认账号;连错5次锁10分钟;登录接口外全部鉴权。**上线只剩配置**:真 secret/HTTPS(Nginx 终止,8080 仅 127.0.0.1)/正式账号强密/内网收紧/CORS 改实际域名

## 进度
- [x] N1-Task1 后端骨架(pom/yml三件/启动类) 2026-10-07
- [x] N1-Task2 建库 14 表+字典 7 类 31 行(幂等验证 3 次启动) 2026-10-07
- [x] N1-Task3 common/config 搬运+CORS 配置化(sc.cors-origin) 2026-10-07
- [x] N1-Task4 system 五模块搬运(SysUserService 拆纯查询,登录归 auth) 2026-10-07
- [x] N1-Task5 JWT 会话改造:JwtUtilTest 3/3 + scripts/test_auth.py 4/4 2026-10-07
- [x] N1-Task6 前端骨架搬运(纵向页面全删,Login/Layout/Notify+角色守卫) 2026-10-07
- [x] N1-Task7 验收:prod 环境变量启动零默认账号✅ 停用即失效✅ 锁定✅ 前端登录✅ 2026-10-07
- [x] N2-Task8 LibreOffice 26.2.6.3 便携版(MSI 管理解包)+JODConverter 集成(PdfSmokeTest 过) 2026-10-07
- [x] N2-Task9 RmbCapitalUtil 人民币大写(TDD 3/3) 2026-10-07
- [x] N2-Task10 4张docx模板(占位符+版式压缩 2/2/2/1 页;RenderSmokeTest 4/4) 2026-10-07
- [x] N2-Task11 模板管理接口(校验链:docx/占位符双向/试渲染/页数;test_template.py 5/5) 2026-10-07
- [x] N2-Task12 PdfRenderService 渲染管线(render 落盘+renderPreview 分屏预览;RenderPipelineTest 5/5+test_render.py 8/8) 2026-10-07
- [x] N2-Task13 模板管理前端页(上传/版本/预览弹窗 blob→iframe) 2026-10-07
- [x] N2-Task14 验收:JUnit 16/16+脚本 13/13 全绿;部署包演练 716MB→8081 端口全通;**样例 PDF 人工核对待用户确认** 2026-10-07
- [x] Task15 模板净版改造:示例/意见预填/表尾注全清+勾选 ■→√(踩坑#6 定稿);提示前移方案落 templates-src/填写提示.md;新模板重入库;JUnit 16/16+脚本 13/13+4 张样例 PDF 目检 2026-10-07
- [x] Task16 基于用户纯净版重制 4 张模板(用户手工清提示+提供权威提示文案→填写提示.md);make_* 全改为"标签保留/空格插占位符"路线;修复标题被 9pt 误压;**格式克隆定稿**:插占位符一律克隆参考 rPr(格内文字run→段落标记rPr,不造裸run/不硬编码字体),标签+选项同段用 options_to_placeholder(标签run不动,占位符继承选项run格式),报告20空段全保留;新增 check_fidelity.py 保真自检(R1格式不发明/R2结构不动/R3文本只增占位符)4/4 PASS;页数上限放宽登记/验收=3(LibreOffice比Word排版松,Word里仍2页);strip_checkbox_syms 清 Wingdings 空选框 sym 符号(文字比对不可见,不清会与值的√/□叠加成串小方框);带数据渲染 3/2/3/2 页,JUnit 16/16 2026-10-07
- [x] Task17 直出 Word:render 双出(docx 主交付,返回其附件 id,biz_type hx_docx/hx_final_docx + PDF 副产物 hx_pdf/hx_final_pdf 落盘不受开关影响,前端 PDF 显隐=sys_config.deliver_pdf_visible 默认 false)、renderDocxStream(N3 分屏预览同口径,纯 poi-tl 不转换不落盘)、/template/preview-docx 样例直出、ConfigController /config/{key}(登录可读,仅值型展示配置)+data.sql 开关种子;JUnit 17/17(新增双出测试)+脚本 auth4/template5/render13 全绿。**踩坑:被中断的 mvn test 会在 %TEMP% 留 render-* 残留、target/test-upload 留 pending_* 文件对,致 countTempResidue/双出计数断言误报红(删残留即绿);后端 java 必须仓库根 cwd 启动,否则 LibreOffice 找不到 PDF 降级(启动方式已改)** 2026-10-07
- [x] Task18 动态行恢复原版排版+超员克隆扩行:登记成员 m1-m6→**m1-m10**、验收学生 s1-s4→**s1-s5**(原版附件实测口径,空行保留=默认与纯净模板排版/字体一致);超预置条数渲染前扩行(TemplateService.expandDynamicRows:克隆组末行 CTRow 深拷贝插组末+占位符改号,XmlCursor 仅原地改文本规避踩坑#5;**契约:数据条数按 {prefix}{i}name 从 1 起连续非空计数,调用方连续平铺**);maxPages 登记/验收 3→4(10 空行实测);SampleData/TemplatePlaceholders/占位符清单同步;check_fidelity 4/4+JUnit 18/18(dynamicRows 用例:预置不增删行/12条扩2行列宽一致/扩行过 LibreOffice 转换)+脚本 template5/render13/auth4 全绿;新模板已重传入库,样例在 docs/samples/(未提交,人工目检用) 2026-10-07
- [x] Task19-23 N3 教师端三流程(hx 实体+RenderModelBuilder/登记+变更+验收后端/前端五页面+docx-preview 分屏;test_hx_register 17+test_hx_change_accept 8+JUnit 23+build 全绿;dev 库含测试残留数据) 2026-10-08
- [x] N3 教师端三流程:新建 hx 业务包(七实体(含 RpFundingRecord)+Mapper+HxFlowStatus+RenderModelBuilder 勾选组/大写/万元换算/成员连续非空平铺;实证 poi-tl 1.12.2 List<String> 的 {{this}} 不渲染,每段包 Map.of("this",段) 修复,SampleData 同款);登记/变更/验收后端=状态机条件更新(0草稿→1已提交→2已回扫)+提交即双出待签 docx+pdf(验收单 acceptance+report 双渲染,biz_type 扩维 hx_report_*)+回扫上传+renderDocxStream 分屏预览+越权隔离(ownedFlow/ownedProject)+在途唯一+登记确认字段锁定+草稿编辑/删除竞态占位+撤回留痕;前端五页面(MyProjects/RegisterWizard/ChangeForm/AcceptanceForm/FlowDetail)+docx-preview 分屏+交付下载/扫描件上传+附件下载接口;字段提示文案=templates-src/填写提示.md;已知边界:变更/验收 submit 的 audit_status=2 强校验在 N3 无 admin 确认接口可达(resubmit 4→1 与全迁移归 N4);样例 docs/samples/ 8 件入库 2026-10-08
- [x] N3 终审修复(F1-F4+登记补充):变更/验收/登记三条草稿编辑路径改 LambdaUpdateWrapper 显式 .set 置空(MP NOT_NULL 策略下 updateById 对 DTO null 字段旧值残留,违"整体覆盖"语义;登记含 rp_hx_project 全部表单字段+rp_project 三字段,可选字段清空残留会渲进待签件污染交付物;hxRegisterDate/verifiedAmount 非表单字段不覆盖)+changeType/outcomeForm split 逐项 trim(校验与勾选命中,RenderModelBuilder.splitTrim/HxService 同口径)+test_render.py docx 报告正文存在性断言(防 {{this}} 渲空旧缺陷复发)+test_hx_change_accept 2 用例(10/10)+test_hx_register 1 用例(18/18);JUnit 23/23+register18+change_accept10+render13 全绿 2026-10-08
- [x] Task24 审批后端(HxAuditService):通过=2→3 条件迁移+三流程副作用(登记编号 HX{年}-{序} 入台账立项/变更按 change_type 改写+负责人易主/验收结题+核定额+终版两张 finalVersion 渲染)+退回(1/2 均可、意见必填、预检标记)+教师 resubmit(4→1,旧待签+旧扫描件作废);test_hx_audit.py 23 项 2026-10-08
- [x] Task25 到账管理+横向项目列表+合同附件(FundingService):admin 录入/删到账(自动通知教师)+教师只读明细+累计实时 SUM(不回写);GET /hx/projects 聚合(到账累计/执行状态推导);合同附件 uploadApply(biz_type=hx_apply,扩展名白名单生而带);test_hx_funding.py 23 项 2026-10-08
- [x] Task26 科技处端六页面+教师端补漏(横向审批/项目管理/字典管理/用户管理/备份页/通知)+终版下载 deliver-final+newLeaderName 回显+字典种子首启化(data.sql 改 INSERT IGNORE,重启不重置管理页改动);test_admin_pages.py 39 项 2026-10-08
- [x] Task27 备份子系统:@Scheduled 周备份+admin 手动备份(mysqldump 全库+upload/ 打 zip 落 backup/,保留 8 份删最旧);selfCheck 流挂死返工(后台排空+关 stdin);test_backup.py 8 项 2026-10-08
- [x] Task28 N4 验收收紧:/hx/leaders viewer 拒+LIKE 通配转义(%/_/反斜杠,teacher/admin 仍可用);uploadScan 扩展名白名单(pdf/png/jpg/jpeg,输入校验先于状态机);渲染件/扫描件/合同附件文件名 ts 秒级→**毫秒级**(同秒重渲/重传物理覆盖收口,三处同口径);replaceMembers members=null=不动成员表(显式空列表才清空,N3 终审挂账);test 补强(hx_audit 28 项:并发通过两连发恰一成功+before_json camelCase 键齐全+leaders 收紧;register 22;change_accept 12);JUnit 23+九脚本 154 项+前端 build 全绿 2026-10-08
- [x] Task29 N4 演示包:后端新增 `sc.static-dir`(env SC_STATIC_DIR,空=关闭行为不变)——指向前端 dist 时内嵌 Tomcat 追加 ROOT 上下文由 SpaStaticServlet 直出(文件命中按 MIME 出流/未命中回退 index.html/穿越 404;与 context-path=/api 主上下文按最长前缀分流互不劫持,SPA 深链刷新不 404;JUnit 30/30 含 StaticSiteFactoryTest 端口级验证);`scripts/build_demo_package.sh` 组装 `dist-demo/`≈1.37GB(jar+libreoffice 652MB+jdk17 剔 jmods+mysql zip 精简 bin/share+component_reference_cache.dll+web dist+upload/backup+start.bat/stop.bat/my.ini/README-演示.md),现场起 3307 临时库经 API 种子(项目A 全流程结题+终版四件、项目B 在研+到账2笔、合同/扫描件=脚本生成的合法单页PDF、模板四张、院部种子)后干净停机固化 mysql/data 快照;账号沿用 dev 口径(admin/admin123、teacher/teacher123,种子期 dev profile 首启建号);验收=robocopy 至 D:/scitech-demo-test 双击 start.bat 一键起,`test_demo_package.py` 13/13(首页/assets MIME/深链回退/任意未知路径回退/api 不被劫持/台账含 A B/teacher 当场登记提交→待签 docx 15976B+pdf 137KB 可下/终版可下)+stop.bat 停净 8080/3307/2002;新踩坑#9/#10 见上 2026-10-08
- [x] N4 终审修复(全分支终审 With fixes 逐条收口):验收核定额补正数校验(负数/0 拒+setScale(2) 量级收口,与 FundingService.addFunding 对称;test_hx_audit 30 项)+科技处 AuditDetail/教师 FlowDetail 补合同扫描件(hx_apply)调阅节(复用 viewAtt/downAtt)+SysUserAdminController 建号 uk_username DuplicateKeyException 兜底(并发双击友好拒,test_admin_pages 建号重复断言补强)+CLAUDE.md 测试口径更新(JUnit 30/30)+.gitignore 补 scripts/cache/;JUnit 30/30+九脚本 156 项+前端 build 全绿 2026-10-08
- [x] 修复:验收单「项目完成情况」区块红字(踩坑#11)——原版红色示例样式经克隆链带入 m/s 占位符与渲染值;make_templates.py save() 文档级清红(四模板重生成,check_fidelity R4 全文档零 FF0000)+RenderModelBuilderTest 四表渲染无红断言;四模板重入库(register v31/change v18/acceptance v17/report v18,校验链全过);docs/samples 八件样例重出(docx 程序化断言零 FF0000,PDF 待用户目检);JUnit 30/30 2026-10-08
- [x] 修复:验收报告正文落书写区(用户反馈两问题)——①正文多段曾全挤区块对所在段连排成坨(三标签同段=poi-tl 段内 run 连排,换行丢失),改**三段独占跨段写法**({{?}}段/{{this}}段/{{/}}段)触发 poi-tl 按段克隆:每段正文独立成段、不足 20 段剩余空行保留原版书写区高度、超出自然增段推挤落款(页数不限);②「正文开始前超长空格」=原版书写区空段段落格式带 firstLineChars=2800(28 字巨缩进,空段无字不显、正文段首行几乎顶行尾),统一改为正文标准首行缩进 2 字(200/420);新测试 reportBodyFillsWritingAreaPerParagraph(2 段相邻成段+空段保留+25 段超员各自成段+落款后仅空段);报告模板重入库 v19,样例重出;JUnit 31/31+check_fidelity 4/4;**测试坑:DOM 断言 docx 必须 DocumentBuilderFactory.setNamespaceAware(true),默认 false 时 getElementsByTagNameNS 匹配不到 w: 前缀元素(静默空列表)** 2026-10-08
- [x] 修复:教师端「我的项目」列表不显示退回态——退回只迁 rp_hx_flow.status=4(项目级 audit_status 不动),而列表状态列 stateOf 只按项目级推导(登记中/变更中/验收中),无 flow.status=4 分支;详情页 FlowDetail 本有完整退回显示。修复=stateOf 加最高优先级「XX单被退回(danger)」分支(与 FlowDetail 同口径,flows[].status 数据 myProjects 本就返回),状态列宽 100→130;纯前端展示层,后端零改动;前端 build 过,待用户刷新目检 2026-10-08
- [x] HTML 打印方案演示稿·填写提示屏显层:templates-src/html/ 四页(演示稿本体=10-13 上午会话产物:三张审批单网页版式+index,方向=浏览器打印出 A4/另存为 PDF 替代 LibreOffice 渲染,是否正式立项改造渲染管线待定);本次按 填写提示.md 全套文案落**屏显提示层**——短句灰字常显+长文 details 折叠(简介/进度/费用科目/变更说明/完成总结)+意见栏参考措辞+工具条「显示填写提示」总开关(body.nohints 切换);打印隐藏与 .btoolbar 合并一条规则 `.btoolbar,.hint{display:none!important}`,意见栏/书写区 min-height 保证打印版面与改前一致;提示一律灰字无红(踩坑#11 语义);新增 scripts/check_html_hints.py 结构自检(print 规则覆盖/无漏挂 hint 的 details/数量口径/总开关/无红)4 页 PASS;人工验收待用户浏览器目检+Ctrl+P 打印预览示例全隐 2026-10-13
- [x] HTML 演示稿批次2(信笺/水印/蓝主题/预算勾选/管理费比例/成员分组表头):assets/logo.png(LOGO定稿PDF 为矢量无嵌入图,PyMuPDF 300dpi alpha 渲染→透明通道 bbox 裁剪→600px)+assets/watermark.png(796² 真镂空)三页齐上;信笺式页眉(Logo 左上 68px,标题居中)、水印 fixed 居中(屏显 opacity .12/打印 .14、每页重复,随打印输出——用户确认);灰区换 rgb(62,173,229) 蓝阶(标签格 #E9F6FD/列表头 #C9E9F9/组头与原信息区 #DDF2FC·#F0FAFE+深蓝字 #14719F,边框灰不变,链接/折叠标题 #1791CC,index 同步);登记表 colgroup 6→7 列重构(13/10/12/13/10/10/auto)——成员表校内(工号/姓名/所在单位/职称/专业/任务)校外(姓名 colspan2/所在单位/职务/专业/任务)分组独立表头,样例补工号、校外职务=技术部经理;预算六科目行各加屏显勾选「合同约定不受限」(勾选→金额输入框隐藏、原位显示口径文字且随打印输出,金额=无边框输入框;合计行不联动=样例静态)+管理费比例换算(屏显输入默认2%×合同经费355000→7100.00 实时重算,打印只出金额);屏显控件类 .scr 并入打印隐藏规则;填写提示.md 两处口径同步(不设比例→勾选显示/管理费比例换算);check_html_hints.py 扩展(资产存在/.hlogo/.watermark/旧灰底清零/新蓝底/7列/工号/职务/6勾选框/mrate默认2)4 页 PASS;人工验收待目检 2026-10-19
- [x] HTML 演示稿批次3:①「项目组成员及分工」竖排(td.vlbl=writing-mode:vertical-rl+text-orientation:upright+letter-spacing,td 双轴居中);②列宽审计:登记表 c4 所在单位 13%→15%(「智能制造与装备学院」9 字单行放下)、任务列 32%→30% 让宽,其余逐列核对内容量无过宽过窄(工号 10% 容 8 位/职务 10% 容 5 字/长标签「项目所在单位负责人意见」两行=设计内);③全部勾选组改**可点击**(span.opt 点击切 √/□、hover 变蓝,打印输出当前状态):登记表 项目类型/性质/设备场地/管理费 4 组单选 12 项,变更表 项目类别 单选+变更内容 **multi 多选**,验收表 项目类型 单选+成果形式 **multi 多选**(技术报告+专利双选样例);JS 三页同款 .opt 处理器;check_html_hints.py 扩展(opt 计数 12/8/8+multi 组+vlbl 竖排)4 页 PASS 2026-10-19
- [x] HTML 演示稿批次4:①表内字体全黑——组头(校内/校外人员)与变更表原信息区标题文字 #14719F→#000(蓝色只留底色/悬停/屏显辅助层);②成员表列宽再调:姓名 12%→**8%**、所在单位 15%→**18%**、任务列 30%→**32%**(专业 10→9 匀出);③**预算合计实时计算**(id=btotal,calcBudget=各科目金额求和,金额输入/勾选不受限即重算,不受限行不计入合计) 2026-10-19
- [x] 修复:水印看不见——根因=用户素材是**白笔画+Alpha 的镂空图**(实测非透明像素平均亮度 238,白底页面任何透明度都不可见),非 CSS 问题;修复=以 Alpha 通道为蒙版把笔画重烤为中性灰 RGB(140,140,140)(assets/watermark.png 原位重写),三页 watermark opacity .12/.14→**.30**(≈传统水印 13% 墨量);check_html_hints.py 加水印回归断言(PIL 采样非透明像素平均亮度<200+opacity=.30) 2026-10-19
- [x] HTML 演示稿批次5:工具条(提示开关/导PDF提示/打印)从页脚移**页面右上**——置于 .page 顶部(标题区之前)右对齐、随页面文档流滚动,打印仍隐藏;padding 6/20→2/6 去页脚撑高;check_html_hints.py 加断言(btoolbar 文档序先于 toparea)4 页 PASS 2026-10-19
- [x] HTML 演示稿批次6:工具条改**悬浮跟随浏览器窗口**——position:fixed 钉视口右上(top10/right12,z-100),白底 92%+主题蓝描边圆角胶囊+微阴影,滚动常驻;打印仍隐藏;check 断言加 position:fixed 4 页 PASS 2026-10-19
- [x] HTML 演示稿批次7:盖章签字区加大空白——三页 .sign-block min-height 19/20mm→**26mm**(签字行仍右下,章盖上方空白;登记表 2 处/变更表 3 处/验收表 4 处全部生效);check 断言 26mm 4 页 PASS;注意:页面可能被撑到接近/超过两页,Ctrl+P 时留意 2026-10-19
- [x] 修复:变更/验收表「字粘在一起」——根因两个悬浮层与内容叠字:①悬浮工具条(视口右上 ~480px 宽)与居中长标题右端叠 ~200px(变更表标题 21 字最长故最显眼);②水印 z-index:90 盖在表格文字之上,30% 灰笔画糊住单屏表中段。修复=①.page 屏显顶部预留 42px 带(padding 52px 6px 0,打印归零)工具条不再压标题;②水印 z-index:-1 移到内容之下(白格透出/蓝底格遮挡,经典水印层次,不糊字);顺手补验收表「性质」勾选组漏转 .opt(上批漏网,opt 计数 8→13);check 断言同步(z-index:-1/顶部预留带/opt13)4 页 PASS 2026-10-19
- [x] 修复:验收表科技处意见签字行压「核定人签字」行——根因=**td 的 min-height 被 Chrome 忽略**+签字行绝对定位不占流内高度,该格流内无内容(唯一无灰字提示的签字格)单元格塌成缝,bottom 锚文字上溢 ~18px 压进上一行右侧。修复=三页 .sign-line 统一改**流内 flex 纵向底对齐**(height:26mm 由内容真实撑起,去绝对定位,多行签字内容纵向堆叠右对齐不变);check 断言加 sign-line flex 规则 4 页 PASS;**HTML 布局教训:td 不吃 min-height,撑高单元格必须靠流内子元素高度** 2026-10-19
- [x] HTML 演示稿批次8:变更表「项目原信息」重构为**逐项对照表**(用户确认方案)——原信息区改「变更项目|原信息(系统带出)|变更后(本次申请)」五行(经费/周期/负责人/委托内容/其他约定,原值蓝底只读、新值白底可填),与「变更内容」multi 勾选 **data-key 联动**(syncCmp:勾选点亮该行可填+cmk 记号同步,未勾灰置显示"—(本次不变更)"),打印输出原→新全表;顶部识别区补回委托单位;原「原项目负责人(本次未变更…)」条件长注删除(对照结构自然表达);index 卡片描述同步;check 断言(cmp×5/data-key×10/syncCmp/委托单位/subhdr)4 页 PASS 2026-10-19
- [x] HTML 演示稿批次9:变更表按用户定稿**推翻对照表**(批次8作废)——①勾选组行改「变更事项」;②「变更内容说明」改「变更内容」:格内 .cmpshow 由 JS 按勾选注入对应项**原信息行**(ORIG 映射,浅蓝底「· 项目经费(原,系统带出):…」,未勾选显示灰提示),下方保留自由填写区(样例=变更后内容文字);③对照表 5 行/原信息区/origin CSS 全删,顶部识别区保留委托单位;hint 折叠标题同步改「变更内容怎么写」;check 断言重写(data-key×5+ORIG+syncCmp+变更事项/变更内容+无残留)4 页 PASS 2026-10-19
- [x] HTML 演示稿批次10:新增 **hx_report.html(附件4 验收报告)**——从 templates-src/附件4 docx 提取结构(python-docx,中文路径走 glob 免 GBK 污染):表头 3 行(编号/名称、是否按期完成(√是□否 单选)+项目类型(单选)、成果形式(可多选 multi))+正文区(原版提示语「包括项目成果内容提要…页数不限」+屏显写作大纲折叠提示(填写提示.md 七章大纲)+七章样例正文(text-indent 2em 自由段落,页数不限)+26mm 手写落款);同壳四件套(水印/信笺Logo/蓝主题/悬浮工具条/提示层打印隐藏);index 加第 4 张卡片+方案口径改「附件4 同出网页版式」;check 扩 PAGES/OPTS(hint≥1、opt=10)5 页 PASS 2026-10-19
- [x] 数据字典落地(docs/四表单数据字典分析报告.md 实施):①**新增平台级职称字典 title**(id 1081-1094 四序列+其他 14 项,不联动模板);②**决策#32 前缀对齐改名**:client_nature→hx_client_nature、change_type→hx_change_type、member_type→hx_member_type、budget_subject_hx→hx_budget_subject——存量库迁移=data.sql 文末 4 条 UPDATE(幂等,新库空跑),种子行同步改名,sys_dict uk_type_value 唯一键保证无冲突;③代码引用同步:SysDictController TEMPLATE_LINKED、RenderModelBuilder dictLabels×3、RenderModelBuilderTest givenDict×2、schema.sql/实体注释×9、前端 DictManage KNOWN_TYPES(7→8 类)+RegisterWizard listDict×4、ChangeForm×1、test_admin_pages.py member_type×9;④登记向导成员**职称改字典下拉**(el-select filterable allow-create 可选可输,容纳校外头衔;验收表职称只读不动);验证=mvn clean test 31/31(踩坑#8 复发,clean 即愈)+前端 build+起后端实测 /dicts 新名有值旧名归零 9 项+admin39/register22/change_accept12 全绿 2026-10-19
- [x] 管理费比例收归科技处配置:教师登记「收取管理费=是」时金额=**合同经费×科技处设定比例**自动换算,教师不可设——①新增 `hx.MgmtFeePolicy`(ratePct 读 sys_config.hx_mgmt_fee_rate,缺/非法兜底 2%+warn;compute=经费×比例 setScale(2) HALF_UP,收取=0→null)+MgmtFeePolicyTest 6 用例(TDD);②HxService 保存草稿/previewDocx register 路径 copyHx 后统一换算(忽略客户端传值),RegisterSaveDTO 删 mgmtFeeAmount 字段收口;**口径=每次保存重算、提交即快照,变更通过改经费按当时比例重算**(HxAuditService.approveChange 经费分支同步 set);③ConfigController 加 PUT /config/{key}(admin-only+键白名单仅 hx_mgmt_fee_rate+0~100 两位小数校验;平台层不 import 业务包,键名三处同串=data.sql/白名单/CONFIG_KEY,脚本用例兜底漂移);data.sql 新键走 **INSERT IGNORE 首启种子**(区别于 deliver_pdf_visible 先删后插——admin 可改的配置重启不得重置,注释两类口径已写明);④前端:RegisterWizard 金额框=只读换算显示(computed mgmtFeePreview,payload 不再带该字段)+比例说明灰字;admin 新增「系统参数」页 SysParams.vue(比例输入+保存,后续值型配置挂这)+路由+菜单;⑤HTML 演示稿同步:hx_register 的 mrate 由可编辑 input 改只读 span(2%,文案「比例由科技处在系统参数中统一设定」),check_html_hints 断言同步;填写提示.md 口径同步;seed_demo_data 删 mgmtFeeAmount 传值(自动换算)。验证:JUnit 37/37(31+6)+九脚本 170 项(admin_pages 41→50 含配置接口 9 用例、register 23→24、audit 30→31 含变更联动 8400)+前端 build+check_html_hints 5 页 PASS;**踩坑#8 再发(java -jar 报 ClassFormatError Invalid method Code length 0=JDT 坏桩混进 jar,mvn clean package 即愈)** 2026-10-20
- [x] **N6 HTML 表单转正(四页演示稿→系统填报/交付界面)**——四决策(2026-10-20 用户拍板):①**HTML 即交付**(提交后教师浏览器打印 A4/另存 PDF 作待签件,后端不再生成 docx;template 平台模块代码保留、hx 停调;回扫/审批不变)②**教师审批前可改**({0,4}→{0,1,2,4},通过锁定)③**admin 审批前任意字段代改**(status∈{1,2},留痕 admin_edit+通知教师;后台项仍审批时写)④**金额硬校验**(R1 预算非不受限合计=合同经费/R3 比例合计=100%/R4 金额合计=到账(系统 SUM 只读带出)/R5 使用≤到账/R6 无到账拒;draft 允许半成品,提交与 admin 代改强制,后端为准)。**后端**:schema 3 列幂等 ALTER(rp_member.employee_no 工号/rp_budget.unrestricted 不受限/rp_hx_flow.finish_date 完成时间,CREATE TABLE 同步带列)+DTO/实体/接线;工号查人 GET /hx/member-lookup(精确 username+启用,回 name/deptName/title,查无 null,viewer 拒);HxService 大重构=三条保存抽 applyXxxEdit/applyXxxData 内核(教师/admin 共用)+validateRegister(+经费>0+lenCheck 字数硬校验)/validateRegisterBudgets/validateAcceptanceRequired/validateAcceptanceAmounts/validateChangeTexts(可包级);门槛放宽 guardEditable/editableDraftFlow/saveRegisterDraft→editableBeforeApprove(≠3)+1/2 态保存显式触碰 update_time(滞后判定=updateTime>scanTime 秒级);渲染退役=renderDeliverables/previewDocx/renderFinalDeliverables 删+四调用点停调(resubmit 的 invalidateOld scan 保留=重交作废旧盖章件)/hx/preview 删/deliver 系接口保留(旧附件可下);admin 代改 POST /hx/{flowId}/admin-save(HxAuditService.adminSave:ObjectMapper convertValue 按 flowType 分派+提交同强度校验+ACT_ADMIN_EDIT 留痕+notifyTeacher);FlowDetailVO+receivedTotal(验收);/users/me 扩 realName/phone(登记表联系电话)。**前端**:「一套 Sheet 组件四种形态」=CheckGroup(√/□ 勾选组)/AutoTextarea(自动撑高+计数)+RegisterSheet/ChangeSheet/AcceptanceSheet/ReportSheet(演示稿四页转正,hx-sheet.css 全局版式+public/sheet-assets);RegisterPage/ChangePage/AcceptancePage 替换旧向导(表格即填单页,审批前可改,提交跳 FlowDetail);PrintPage=/print/:flowType/:flowId?doc=hx_report(Layout 外新标签只读 Sheet 自动弹打印);ReportSheet 正文=单文本+章节快捷插入+打印按行识别「一、…」渲染 p.sub(与旧 report_content \n\n 分段完全兼容);sheetModel composable=detail→Sheet 模型映射(PrintPage/AuditDetail 共口径)+collectSheetDto(admin 代改提交);FlowDetail=打印主入口+旧 docx 降级「下载历史 Word」+滞后红条+编辑门槛 {0,1,2,4};AuditDetail=DocxPane→只读 Sheet+「修正填报」edit 模式(admin-save);MyProjects 入口门槛同步;删 RegisterWizard/ChangeForm/AcceptanceForm/DocxPane。验证:JUnit 37/37+**九脚本 182 项**(register 33 含 R1/R2/工号查人/1、2 态可改/滞后/admin 代改/通过锁定;audit 36 含 R3-R6 负例+代改验收;admin_pages 47;funding 23;change_accept 13)+前端 build;dev 全流程脚本走通(登记→打印路由→回扫→admin 代改→审批→通过锁定) 2026-10-21
- [x] **N6 首轮+二轮目检修复**(`ce60c6a`/`0202775`):首轮=审批页渲染崩(d 赋值瞬间空 model 崩子树→sheetReady 守卫)/成员区竖排标签 rowspan 硬编码错位(动态公式化+添加行打印隐藏 tr.addrow)/打印水印只在末页(fixed 打印不每页重复→@media print 改 html 画布背景每页居中+编辑页 Ctrl+P 隐藏 Layout 外壳)/工具条悬浮/提交确认弹窗(提交后仍可修改,但打印版必须为最终修改版)。二轮=**发起变更/验收入口报错+原信息无回显(根因:GET /hx/my 行无顶层 flowId/receivedTotal,两页却拿 p.flowId 调 flowDetail→从 flows[] 找 register 单修复;验收到账改 teacherFunding;顺修 ChangeSheet ORIG 数组当对象取的笔误)**+文案「原,系统带出」→「原:」/联系电话可输入(rp_hx_project.leader_phone 新列,默认带出本人手机号存量优先)/工具条改按钮形式钉**窗口左上**(原 CSS 选择器带 .hx-sheet 前缀而工具条在页面壳顶层=static 不悬浮——放宽全局 .btoolbar)/MyProjects 四处裸 link 补 primary 统一蓝色。验证:build+浏览器实测三类入口全通+register 33/audit 36 回归绿 2026-10-23
- [x] **N6 三轮目检+列表增强**(`affb60f`→`db755be` 五笔):①工具条左缘对齐表单容器(max(12px,calc(50%-500px)),宽屏贴表单左上窄屏退视口);②**SheetToolbar 组件化**(提示开关+动作按钮 slot,四表单页+打印页统一);③审批页登记表预算区错位——打印态 budgets 来自库中实存行(可能只存部分科目)而预算标签 rowspan=8 按六科目硬编码→RegisterSheet.budgetRows 打印态按 meta.dictBudgetSubject 六科目骨架合并实存值(与演示稿口径一致,buildSheetModel 补字典加载);④**三个列表页统一查询栏**(关键字+状态下拉,前端实时过滤+重置:横向项目=名称/编号/负责人+七态;横向审批=补名称/负责人/院部;项目管理=名称/编号/负责人/委托单位+执行状态);⑤**三页统一分页**(el-pagination 默认 20/页可调 10/30/50,filtered 之上切页,条件变化回页 1、行数缩减钳制越界、空表隐藏);⑥**清空全部测试数据后空库全业务回归**(业务八表+sys_notify 清+upload/hx 删,九脚本 182 项全绿,现库=新一轮全流程样例数据)。验证:build+浏览器实测(MyProjects 8→1/ProjectManage 9→3 实时过滤、分页「共 8 条」、审批页六科目对齐) 2026-10-23
- [ ] N5 整体验收(依赖 N1-N4 实际形态另行编写计划;N5 注意:prod 上线清单——DB_URL **禁加 useAffectedRows=true**(竞态守卫依赖条件更新 matched 行数,加则同值 SET 误判 0 行)、SC_JWT_SECRET≥32 字符、HTTPS(Nginx 终止,8080 仅 127.0.0.1)、正式账号强密、CORS 改实际域名、Linux 中文字体确认;遗留小口(评估是否 N5 收口):SysUserAdminController /users?q= LIKE 未转义(admin-only 面)、replaceBudgets budgets=null 仍清空预算行(与 members 口径不一)、viewer 在其余 /hx 教师端点无显式 role 拒——技术上可起草登记)

## N2 关键交付物速查
- 渲染管线:`PdfRenderService.render(RenderContext,model)→docx附件id`(双出落盘 upload/hx/{pid}/{ftype}/{fid}/pending_|final_{ts}_{tplType}.{docx,pdf},**ts=毫秒级 yyyyMMdd_HHmmss_SSS**——Task28 起,同秒重渲不物理覆盖;文件名含 tplType 段防验收双单同秒互覆;扫描件 scan_{ts} 与合同附件 apply_{ts} 同口径;biz_type 按 tplType 扩维:报告件 hx_report_docx/hx_report_pdf、其余 hx_docx/hx_pdf、终版 hx_final_docx/hx_final_pdf);超预置成员 `TemplateService.expandDynamicRows` 克隆组末行扩行(契约 m{i}name 从 1 起连续非空);`renderPreview(tplType,model)→(pdf字节,页数)`(决策#36);`renderDocxStream(tplType,model)→docx字节`(N3 填报分屏预览同口径,不转换不落盘)
- 备份子系统(Task27):`BackupService` @Scheduled(sc.backup-cron,默认周一 02:30)+ admin 手动 `/backup/run`,流程=mysqldump 全库+upload/ 完整目录打 zip 落 backup/(保留 sc.backup-keep=8 份,超删最旧);仅 admin;部署包携带 mysqldump.exe,启动自检缺失则 available=false 页面置灰;zip 删除=物理删(区别于附件逻辑删)
- 模板:templates-src/hx_{register,change,acceptance,report}.docx(2026-10-07 净版:无示例/意见预填/表尾注)+ make_templates.py(从原始.doc重生成,幂等)+占位符清单.md+填写提示.md(迁出提示文案,N3 前端用);动态行预置=原版空行数(登记成员 m1-m10/验收成员 m1-m8、学生 s1-s5),超员渲染前克隆扩行(格式与源行一致);勾选组 √/□
- 测试全绿口径:mvn test -Drender.smoke=true -Dpdf.smoke=true(37/37,含 MgmtFeePolicyTest 6);九脚本 **182 项**(N6 HTML 转正后新口径)——test_auth.py(4)+test_template.py(5)+test_render.py(13,platform 直测;hx 渲染断言已随决策①退役)+test_hx_register.py(33,含管理费换算+R1/R2 预算校验+工号查人+1/2 态可改+滞后判定+admin 代改/锁定+members=null 防御+扫描件白名单)+test_hx_change_accept.py(13,含工号/不受限/finishDate 回显+字数拒+验收 members 口径)+test_hx_audit.py(36,含 R3-R6 负例+admin 代改验收留痕+变更通过管理费随经费重算+并发通过两连发+核定额校验)+test_hx_funding.py(23)+test_admin_pages.py(47,含管理费比例配置接口+终版退役口径)+test_backup.py(8);演示包另计 test_demo_package.py(13,**待按新口径重写——dist-demo 仍为旧版固化快照**)
- 部署包=sc-backend.jar+libreoffice/(652MB 裁剪版)+upload/+backup/+mysqldump.exe ≈716MB,任意目录可起(相对路径cwd=包根)
- 演示包(Task29):`bash scripts/build_demo_package.sh [--skip-build|--files-only]` → `dist-demo/`(git-ignored ≈1.37GB:部署包全部内容+mysql 精简版(自带 mysqld,3307 仅回环,root=demo-root-2026,data=演示数据快照)+jdk17+web/前端 dist+start.bat/stop.bat/my.ini/README-演示.md);一键 start.bat(起库→起应用→开浏览器;--sc.mysqldump-path 指向包内 mysql/bin);前端由后端 `sc.static-dir` 直出;演示账号 admin/admin123、teacher/teacher123、teacher2/teacher2123;验收 `python scripts/test_demo_package.py`(13 项);种子 `scripts/seed_demo_data.py`(经 API 造项目A 全流程/项目B 在研+到账2笔)

## 约定
- 所有回复、注释、文档中文;数据表 sys_/rp_ 前缀;仅按用户要求 commit(已授权逐 Task commit 模式)
- 验证策略(2026-09-03 沿用):业务逻辑脚本批量接口测试;UI 渲染+接口连通即止(截图两张),交互用户手动验收
- 每阶段可运行验收后进下一阶段;会话结束前更新本文件进度

## Git
本仓库为新项目独立仓库(main 分支)。上游设计文档在旧仓库 docs/superpowers/ 下,引用路径见文首
