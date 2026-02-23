package com.minibank.fraud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.minibank.fraud.repository")
public class ElasticsearchConfig {
    // Spring Boot auto-configures the Elasticsearch client based on application.yml
    // This class enables Elasticsearch repository scanning
}
