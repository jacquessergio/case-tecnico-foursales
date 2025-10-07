package com.foursales.eventconsumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.foursales.eventconsumer.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.foursales.eventconsumer.repository.search")
public class RepositoryConfig {
}