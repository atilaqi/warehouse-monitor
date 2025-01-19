package com.example.config;

import com.example.service.UDPServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UDPServerConfig {

    @Bean(name = "temperatureServer")
    public UDPServer temperatureServer(WarehouseProperties properties) {
        return new UDPServer(properties.temperaturePort());
    }

    @Bean(name = "humidityServer")
    public UDPServer humidityServer(WarehouseProperties properties) {
        return new UDPServer(properties.humidityPort());
    }
}

