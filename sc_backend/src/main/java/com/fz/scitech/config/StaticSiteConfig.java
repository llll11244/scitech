package com.fz.scitech.config;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 前端静态资源伺服开关(Task29 演示包,单机一键形态:后端直出前端,零新依赖)。
 *
 * sc.static-dir 为空(默认)= 关闭:不注册任何额外上下文,行为与历史版本完全一致
 * (开发期 npm run dev 5173 代理 /api 到 8080,互不影响)。
 * sc.static-dir 指向目录(演示包 start.bat 传 ./web,即 npm run build 的 dist)时:
 * 在同一内嵌 Tomcat 上追加一个 ROOT 上下文(""),由 {@link SpaStaticServlet} 伺服——
 * 主应用仍在 context-path=/api 的上下文里,/api/** 请求永远不会落到静态上下文
 * (Tomcat 按最长前缀匹配 context,不存在互相劫持);非 /api 的 GET 未命中静态文件
 * 即回退 index.html(SPA 路由)。auto-config 的 TomcatServletWebServerFactory 被
 * 本类 Bean 替代,server.* 属性仍经 WebServerFactoryCustomizer 正常生效。
 */
@Configuration
public class StaticSiteConfig {

    private static final Logger log = LoggerFactory.getLogger(StaticSiteConfig.class);

    @Bean
    public TomcatServletWebServerFactory servletWebServerFactory(
            @Value("${sc.static-dir:}") String staticDir) {
        Path root = null;
        if (staticDir != null && !staticDir.isBlank()) {
            root = Path.of(staticDir).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("sc.static-dir 指向的目录不存在: " + root);
            }
        }
        final Path staticRoot = root;
        return new TomcatServletWebServerFactory() {
            @Override
            protected TomcatWebServer getTomcatWebServer(Tomcat tomcat) {
                if (staticRoot != null) {
                    Context ctx = new StandardContext();
                    ctx.setName("");
                    ctx.setPath("");
                    ctx.setDocBase(staticRoot.toString());
                    // 纯程序化上下文(非 war/webapp):无 ContextConfig 走 web.xml 解析,
                    // CONFIGURE_START 阶段需自行置位 configured,否则启动即判"未正确配置"
                    ctx.addLifecycleListener(event -> {
                        if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                            ((Context) event.getLifecycle()).setConfigured(true);
                        }
                    });
                    Wrapper wrapper = ctx.createWrapper();
                    wrapper.setName("spa-static");
                    wrapper.setServlet(new SpaStaticServlet(staticRoot));
                    wrapper.setLoadOnStartup(1);
                    ctx.addChild(wrapper);
                    // 映射 "/" = 该上下文的默认入口,所有路径都由本 servlet 处理
                    ctx.addServletMappingDecoded("/", "spa-static");
                    tomcat.getHost().addChild(ctx);
                    log.info("前端静态资源已挂载(ROOT 上下文,目录={})", staticRoot);
                }
                return super.getTomcatWebServer(tomcat);
            }
        };
    }
}
