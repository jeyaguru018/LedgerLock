package com.ledgerlock.config;

import com.ledgerlock.interceptor.LoggingInterceptor;
import com.ledgerlock.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final LoggingInterceptor loggingInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor, LoggingInterceptor loggingInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.loggingInterceptor = loggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Logging interceptor should be first to assign MDC context before anything else runs
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**");
                
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }
}
