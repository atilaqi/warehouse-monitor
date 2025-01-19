package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitoring")
public record CentralMonitoringProperties(
        String kafkaTopic,
        double temperatureThreshold,
        double humidityThreshold
) {}
