package com.urlshortener.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS filter that adds CORS headers to every response and short-circuits
 * pre-flight OPTIONS requests. Works with any JAX-RS runtime (no Jetty
 * internals required).
 */
@Provider
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        // Return 200 immediately for pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            request.abortWith(Response.ok().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
        response.getHeaders().add("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        response.getHeaders().add("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Requested-With, Content-Length, Accept, Origin");
        response.getHeaders().add("Access-Control-Max-Age", "86400");
    }
}
