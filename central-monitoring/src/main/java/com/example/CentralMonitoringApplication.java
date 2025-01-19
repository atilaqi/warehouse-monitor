package com.example;

import com.example.config.CentralMonitoringProperties;
import com.example.config.KafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({CentralMonitoringProperties.class, KafkaProperties.class})
public class CentralMonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(CentralMonitoringApplication.class, args);
    }
}
