package com.ecommerce.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<Filter> corsFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();

        bean.setFilter(new Filter() {
            @Override
            public void doFilter(
                    ServletRequest servletRequest,
                    ServletResponse servletResponse,
                    FilterChain filterChain
            ) throws IOException, ServletException {

                HttpServletRequest request = (HttpServletRequest) servletRequest;
                HttpServletResponse response = (HttpServletResponse) servletResponse;

                String origin = request.getHeader("Origin");

                if (origin != null) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                }

                response.setHeader("Vary", "Origin");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin");
                response.setHeader("Access-Control-Max-Age", "3600");

                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

                filterChain.doFilter(servletRequest, servletResponse);
            }
        });

        bean.addUrlPatterns("/*");
        bean.setOrder(Integer.MIN_VALUE);

        return bean;
    }
}