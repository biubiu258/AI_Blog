package com.aiblog.security;

import com.aiblog.common.ApiResponse;
import com.aiblog.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 无状态 JWT 安全链：公开接口白名单放行，其余一律需要认证。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步派发：首次请求已鉴权，容器在 emitter.complete() 后
                        // 会再走一次 ASYNC 派发，此时 SecurityContext 已清理，
                        // 不放开会被 AccessDenied 打断，导致响应被强制中断（前端读流出错）
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        // 注册、登录、登录验证码、邮箱验证与找回密码公开
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/captcha",
                                "/api/auth/password/reset-code", "/api/auth/password/reset").permitAll()
                        // “我的文章”必须登录（声明在通配符之前）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/articles/mine")
                        .authenticated()
                        // 公开读接口：文章列表/详情/阅读计数、分类与标签、评论、用户主页
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/articles", "/api/articles/*", "/api/articles/*/comments",
                                "/api/articles/*/view", "/api/categories",
                                "/api/tags", "/api/tags/*/articles",
                                "/api/users/*", "/api/users/*/articles",
                                "/api/users/*/followers", "/api/users/*/following").permitAll()
                        // 静态资源：上传的图片；SEO 收录文件
                        .requestMatchers("/uploads/**", "/error", "/sitemap.xml", "/robots.txt")
                        .permitAll()
                        // 健康探针与接口文档（生产环境通过配置关闭文档）
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 其余接口一律认证（含 /api/articles/mine、AI 网关、上传等）
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, HttpStatus.UNAUTHORIZED, ApiResponse.error(ErrorCode.UNAUTHORIZED)))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, HttpStatus.FORBIDDEN, ApiResponse.error(ErrorCode.FORBIDDEN))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 开发环境跨域；生产由 Nginx 同源部署，无需 CORS。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, ApiResponse<?> body)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
