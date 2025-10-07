package com.foursales.ecommerce.config;

import com.foursales.ecommerce.ratelimit.RateLimitService;
import com.foursales.ecommerce.repository.search.ProductElasticsearchRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@TestConfiguration
public class TestConfig {

    @MockBean
    private ProductElasticsearchRepository productSearchRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @Bean
    @Primary
    public RateLimitService rateLimitService() {
        RateLimitService mock = Mockito.mock(RateLimitService.class);
        Mockito.when(mock.tryConsume(anyString(), any())).thenReturn(true);
        Mockito.when(mock.getRemainingTokens(anyString(), any())).thenReturn(1000L);
        return mock;
    }
}
