package com.xowns.celfeed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Profile("dev")
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient( ) {
        return RestClient.builder()
                .baseUrl("http://localhost:8081/internal/notifications")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
