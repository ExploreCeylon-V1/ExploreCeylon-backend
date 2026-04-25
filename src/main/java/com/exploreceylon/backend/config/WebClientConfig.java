package com.exploreceylon.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${rapidapi.hotel.baseurl}")
    private String hotelBaseUrl;

    @Value("${rapidapi.vehicle.baseurl}")
    private String vehicleBaseUrl;

    @Bean(name = "hotelWebClient")
    public WebClient hotelWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(hotelBaseUrl)
                .build();
    }

    @Bean(name = "vehicleWebClient")
    public WebClient vehicleWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(vehicleBaseUrl)
                .build();
    }

    @Bean(name = "aiWebClient")
    public WebClient aiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://localhost:8000")
                .build();
    }
}