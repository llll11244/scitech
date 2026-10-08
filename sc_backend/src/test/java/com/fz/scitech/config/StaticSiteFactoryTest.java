package com.fz.scitech.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态站点挂载的端口级验证(Task29,真内嵌 Tomcat,不起 Spring 上下文/不连库):
 * ① sc.static-dir 指向目录:/ 返回 index.html、assets 命中带正确 MIME、
 *   未知深链回退 index.html;
 * ② /api/** 归主上下文(context-path=/api,此处无任何控制器→404),证明
 *   静态上下文绝不劫持接口路径;
 * ③ sc.static-dir 为空(默认关闭):不挂 ROOT 上下文,/ 保持 404,行为与历史一致。
 */
class StaticSiteFactoryTest {

    private static final String INDEX = "<!DOCTYPE html><html><body><div id=\"app\">SPA</div></body></html>";

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void staticDirServesSpaWithoutHijackingApi(@TempDir Path web) throws Exception {
        Files.writeString(web.resolve("index.html"), INDEX);
        Files.createDirectories(web.resolve("assets"));
        Files.writeString(web.resolve("assets/index-Ab12Cd.js"), "console.log(1)", StandardCharsets.ISO_8859_1);

        WebServer server = startServer(web.toString());
        try {
            int port = ((TomcatWebServer) server).getPort();

            HttpResponse<String> root = get(port, "/");
            assertEquals(200, root.statusCode());
            assertTrue(root.body().contains("id=\"app\""), root.body());
            assertTrue(root.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));

            HttpResponse<String> asset = get(port, "/assets/index-Ab12Cd.js");
            assertEquals(200, asset.statusCode());
            assertEquals("console.log(1)", asset.body());
            assertTrue(asset.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));

            // 深链(前端路由路径,服务器无对应文件)→ 回退 index.html,刷新不 404
            HttpResponse<String> deep = get(port, "/my/projects");
            assertEquals(200, deep.statusCode());
            assertTrue(deep.body().contains("id=\"app\""));

            // /api/** 走主上下文(本测试未注册任何控制器→404),绝不被静态回退劫持
            HttpResponse<String> api = get(port, "/api/auth/login");
            assertEquals(404, api.statusCode());
            assertFalse(api.body().contains("id=\"app\""), api.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void emptyStaticDirKeepsHistoricalBehavior(@TempDir Path unused) throws Exception {
        WebServer server = startServer("");
        try {
            int port = ((TomcatWebServer) server).getPort();
            assertEquals(404, get(port, "/").statusCode());
            assertEquals(404, get(port, "/some/deep/link").statusCode());
        } finally {
            server.stop();
        }
    }

    private WebServer startServer(String staticDir) {
        TomcatServletWebServerFactory factory = new StaticSiteConfig().servletWebServerFactory(staticDir);
        // 真实应用的 context-path=/api 由 server.* 属性注入;裸工厂需显式对齐,
        // 否则主上下文即 ""(ROOT),与静态上下文同名冲突(也反证生产形态下无冲突)
        factory.setContextPath("/api");
        factory.setPort(0);
        WebServer server = factory.getWebServer();
        server.start();
        return server;
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
