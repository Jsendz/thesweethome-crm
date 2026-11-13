package com.sweethomecompany.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * With a plain CookieCsrfTokenRepository, the XSRF-TOKEN cookie is only
 * written once something actually reads the deferred CsrfToken. Without
 * this filter the cookie would never appear on an otherwise-unauthenticated
 * GET (e.g. /api/auth/me before login), leaving the frontend with no token
 * to echo back on the login POST itself.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
