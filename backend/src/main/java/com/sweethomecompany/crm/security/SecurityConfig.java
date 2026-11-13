package com.sweethomecompany.crm.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            AuditingAuthenticationSuccessHandler successHandler,
            AuditingAuthenticationFailureHandler failureHandler,
            SessionRegistry sessionRegistry) throws Exception {
        http
            // CorsConfig (a WebMvcConfigurer) only applies once Spring MVC dispatches
            // to a handler. formLogin's success/failure handlers, the 401 entry point,
            // and CSRF rejections are all written to the response from inside this
            // filter chain, before MVC ever sees the request — without this, none of
            // those responses would carry Access-Control-Allow-* headers, and the
            // browser would block the frontend from reading them even though the
            // request succeeded server-side. No explicit CorsConfigurationSource bean
            // is needed: Spring Security auto-detects CorsConfig's mapping via
            // HandlerMappingIntrospector.
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                // Plain (non-Xor) handler: CookieCsrfTokenRepository stores the raw
                // token value directly in the cookie, so the SPA reads that raw value
                // and echoes it back verbatim in X-XSRF-TOKEN. The default Xor handler
                // expects a BREACH-masked value (meant for server-rendered forms) and
                // rejects the raw cookie value as an invalid token.
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Without this, formLogin() leaves the default entry point in place,
            // which redirects (302 to /login) instead of returning 401 for an
            // unauthenticated request to a protected API endpoint.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // /error must be open too: Boot forwards rejected requests (e.g. a
                // CSRF 403) to /error internally, and if that forwarded request were
                // itself blocked as unauthenticated, the original status would be
                // lost behind the entry point above instead of reaching the client.
                .requestMatchers("/actuator/**", "/api/auth/login", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_OK)))
            // Not a real cap (10,000 is effectively unlimited) - this only exists to
            // activate Spring Security's concurrent-session tracking, which populates
            // sessionRegistry on every login so UserController can look up and expire
            // a specific user's session when they're deactivated.
            .sessionManagement(session -> session
                .maximumSessions(10_000)
                .sessionRegistry(sessionRegistry)
                // Without this, an expired session's next request gets Spring
                // Security's default expired-session response (200 OK with a plain
                // text body) instead of the 401 every other unauthenticated request
                // on this API returns.
                .expiredSessionStrategy(event -> event.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }
}
