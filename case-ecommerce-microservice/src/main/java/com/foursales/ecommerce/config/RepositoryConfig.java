package com.foursales.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.foursales.ecommerce.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.foursales.ecommerce.repository.search")
public class RepositoryConfig {
}