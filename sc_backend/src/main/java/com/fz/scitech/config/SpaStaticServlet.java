package com.fz.scitech.config;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * SPA 前端静态资源伺服(Task29 演示包):挂在内嵌 Tomcat 的 ROOT 上下文,
 * 与主应用(context-path=/api)分属两个 Context,互不相扰——Spring MVC、
 * AuthInterceptor、CORS 等对这里的请求完全不生效,静态资源天然公开可访问。
 *
 * 规则(sc.static-dir 指向前端 npm run build 的 dist 目录):
 * - GET 命中目录内真实文件 → 按扩展名给出 MIME 并流式输出;
 * - 未命中(前端路由深链,如 /my/projects)→ 一律回退 index.html(HTTP 200,
 *   由浏览器端 vue-router 接管),保证刷新深链不 404;
 * - 路径穿越(../ 解到根目录之外)→ 404,不回退。
 * MIME 自带小表而不依赖 Files.probeContentType(Windows 下后者受注册表影响不可靠)。
 */
public class SpaStaticServlet extends HttpServlet {

    /** 常见前端构建产物扩展名 → MIME(文本类显式 UTF-8,与 Vite 产物编码一致) */
    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("html", "text/html;charset=UTF-8"),
            Map.entry("htm", "text/html;charset=UTF-8"),
            Map.entry("js", "text/javascript;charset=UTF-8"),
            Map.entry("mjs", "text/javascript;charset=UTF-8"),
            Map.entry("css", "text/css;charset=UTF-8"),
            Map.entry("json", "application/json;charset=UTF-8"),
            Map.entry("map", "application/json;charset=UTF-8"),
            Map.entry("webmanifest", "application/manifest+json;charset=UTF-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            Map.entry("txt", "text/plain;charset=UTF-8"),
            Map.entry("csv", "text/csv;charset=UTF-8"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"));

    private final Path root;

    public SpaStaticServlet(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // context-path 为空串,URI 即资源路径;先补掉 '+'(URLDecoder 会当空格)再解码
        String raw = req.getRequestURI().substring(req.getContextPath().length());
        String decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        if (decoded.contains("\\") || decoded.contains("\0")) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path target = root.resolve(decoded.substring(1)).normalize();
        if (!target.startsWith(root)) {
            // 路径穿越(../ 解到根外):404,绝不回退(避免给扫描器回 index 混淆语义)
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!Files.isRegularFile(target)) {
            // 未命中真实文件 → SPA 回退 index.html(不存在说明目录配错,按 404 报)
            target = root.resolve("index.html");
            if (!Files.isRegularFile(target)) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        res.setContentType(mime(target));
        res.setContentLengthLong(Files.size(target));
        try (InputStream in = Files.newInputStream(target)) {
            in.transferTo(res.getOutputStream());
        }
    }

    /** HEAD 复用 GET(HttpServlet 默认包装会丢弃响应体,保留头) */
    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse res) throws IOException {
        doGet(req, res);
    }

    private String mime(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return MIME.getOrDefault(ext, "application/octet-stream");
    }
}
