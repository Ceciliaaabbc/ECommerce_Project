// package com.ecommerce.config;

// import jakarta.servlet.FilterChain;
// import jakarta.servlet.ServletException;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;

// import org.springframework.stereotype.Component;
// import org.springframework.web.filter.OncePerRequestFilter;

// import java.io.IOException;

// @Component
// public class PreflightCorsFilter extends OncePerRequestFilter {

//     private static final String FRONTEND_URL =
//             "https://ecommerce-frontend-one-theta.vercel.app";

//     @Override
//     protected void doFilterInternal(
//             HttpServletRequest request,
//             HttpServletResponse response,
//             FilterChain filterChain
//     ) throws ServletException, IOException {

//         response.setHeader("Access-Control-Allow-Origin", FRONTEND_URL);
//         response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
//         response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin");
//         response.setHeader("Access-Control-Max-Age", "3600");

//         if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
//             response.setStatus(HttpServletResponse.SC_OK);
//             return;
//         }

//         filterChain.doFilter(request, response);
//     }
// }