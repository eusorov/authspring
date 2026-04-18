package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimiterFilterTest {

    private ApiRateLimiterFilter filter;
    private RateLimiterRegistry registry;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build();
        Map<String, RateLimiterConfig> configs = new HashMap<>();
        configs.put("default", RateLimiterConfig.ofDefaults());
        configs.put(ApiRateLimiterFilter.RATE_LIMITER_NAME, config);
        registry = RateLimiterRegistry.of(configs);
        filter = new ApiRateLimiterFilter(registry);
    }

    @Test
    void rateLimiterRegistryAllowsOneThenDeniesSecond() {
        RateLimiter r = registry.rateLimiter(ApiRateLimiterFilter.RATE_LIMITER_NAME, ApiRateLimiterFilter.RATE_LIMITER_NAME);
        assertTrue(r.acquirePermission());
        assertFalse(r.acquirePermission());
    }

    @Test
    void secondRequestToApiReturns429() throws ServletException, IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(apiRequest(), res, chain);
        assertEquals(200, res.getStatus());

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        MockFilterChain chain2 = new MockFilterChain();
        filter.doFilter(apiRequest(), res2, chain2);
        assertEquals(429, res2.getStatus());
        assertEquals("{\"message\":\"Too Many Attempts.\"}", res2.getContentAsString());
    }

    @Test
    void nonApiPathNotLimited() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/health");
        MockHttpServletResponse res = new MockHttpServletResponse();

        for (int i = 0; i < 5; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/test");
        return req;
    }
}
