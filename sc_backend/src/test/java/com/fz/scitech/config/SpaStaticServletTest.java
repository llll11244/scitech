package com.fz.scitech.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpaStaticServlet 单元测试(Task29):文件命中/MIME、SPA 回退、路径穿越 404。
 * 真实端口级的 /api 不劫持与深链回退见 StaticSiteFactoryTest。
 */
class SpaStaticServletTest {

    @TempDir
    Path web;

    private SpaStaticServlet servlet;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(web.resolve("index.html"), "<!DOCTYPE html><html><body><div id=\"app\">SPA</div></body></html>");
        Files.createDirectories(web.resolve("assets"));
        Files.writeString(web.resolve("assets/app-Ab12Cd.js"), "console.log(1)", StandardCharsets.ISO_8859_1);
        servlet = new SpaStaticServlet(web);
    }

    private MockHttpServletResponse get(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        servlet.doGet(req, res);
        return res;
    }

    @Test
    void rootReturnsIndexHtml() throws Exception {
        MockHttpServletResponse res = get("/");
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("id=\"app\""), res.getContentAsString());
        assertTrue(res.getContentType().startsWith("text/html"));
    }

    @Test
    void realFileServedWithMime() throws Exception {
        MockHttpServletResponse res = get("/assets/app-Ab12Cd.js");
        assertEquals(200, res.getStatus());
        assertEquals("console.log(1)", res.getContentAsString());
        assertTrue(res.getContentType().startsWith("text/javascript"), res.getContentType());
    }

    @Test
    void unknownPathFallsBackToIndex() throws Exception {
        MockHttpServletResponse res = get("/my/projects");
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("id=\"app\""));
    }

    @Test
    void traversalRejectedWith404() throws Exception {
        assertEquals(404, get("/../secret.txt").getStatus());
        assertEquals(404, get("/a/../../secret.txt").getStatus());
        // %2e%2e 编码形态同样拒绝
        assertEquals(404, get("/%2e%2e/secret.txt").getStatus());
        // 反斜杠一律拒绝
        assertEquals(404, get("/a%5Cb.txt").getStatus());
    }

    @Test
    void plusInNameNotEatenByDecoder() throws Exception {
        Files.writeString(web.resolve("assets/a+b.js"), "ok", StandardCharsets.ISO_8859_1);
        MockHttpServletResponse res = get("/assets/a%2Bb.js");
        assertEquals(200, res.getStatus());
        assertEquals("ok", res.getContentAsString());
    }
}
