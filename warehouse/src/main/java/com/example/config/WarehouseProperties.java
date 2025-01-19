package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warehouse")
public record WarehouseProperties(
        String id,
        int temperaturePort,
        int humidityPort,
        String kafkaTopic
) {}