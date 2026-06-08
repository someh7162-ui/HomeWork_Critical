package com.papercritical.config;

import com.papercritical.common.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final JwtUtil jwtUtil;

    public WebConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(System.getProperty("user.dir"))
                .resolve("uploads").toAbsolutePath().toString();
        log.info("Register upload resource path: {}", uploadPath);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + File.separator);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                String path = request.getRequestURI();
                if (path.startsWith("/uploads/")) {
                    log.info("Upload resource requested: method={}, uri={}, query={}",
                            request.getMethod(), request.getRequestURI(), request.getQueryString());
                    return true;
                }
                if (path.endsWith("/api/auth/login")
                        || path.endsWith("/api/auth/register")
                        || path.endsWith("/api/auth/wechat-login")
                        || path.endsWith("/api/auth/student-login")) {
                    return true;
                }
                if (path.equals("/api/admin/login")) {
                    return true;
                }
                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    if (jwtUtil.validateToken(token)) {
                        Long subjectId = jwtUtil.getSubjectId(token);
                        String role = jwtUtil.getRole(token);
                        if ("STUDENT".equals(role)) {
                            if (!path.startsWith("/api/student/")) {
                                response.setStatus(403);
                                return false;
                            }
                            request.setAttribute("studentId", subjectId);
                            request.setAttribute("role", role);
                            return true;
                        }
                        if (path.startsWith("/api/admin/") && subjectId != 0L) {
                            response.setStatus(403);
                            return false;
                        }
                        request.setAttribute("teacherId", subjectId);
                        request.setAttribute("role", role);
                        return true;
                    }
                }
                response.setStatus(401);
                return false;
            }
        }).addPathPatterns("/api/**", "/uploads/**");
    }
}
