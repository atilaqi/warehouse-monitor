package com.example.service;

import com.example.data.SensorData;
import com.example.config.CentralMonitoringProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.KafkaReceiver;

@Slf4j
@Service
public class CentralMonitoringService {
    private final KafkaReceiver<String, SensorData> kafkaReceiver;
    private final CentralMonitoringProperties properties;

    public CentralMonitoringService(
            KafkaReceiver<String, SensorData> kafkaReceiver,
            CentralMonitoringProperties properties
    ) {
        this.kafkaReceiver = kafkaReceiver;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        kafkaReceiver.receive()
                .subscribe(record -> {
                    SensorData reading = record.value();
                    checkThreshold(reading);
                    record.receiverOffset().acknowledge();
                });

        log.info("Central monitoring service started");
    }

    private void checkThreshold(SensorData reading) {
        if (reading.type() == SensorData.SensorType.TEMPERATURE &&
                reading.value() > properties.temperatureThreshold()) {
            log.error("ALARM: Temperature threshold exceeded in warehouse {}! Current: {}, Threshold: {}",
                    reading.warehouseId(), reading.value(), properties.temperatureThreshold());
        } else if (reading.type() == SensorData.SensorType.HUMIDITY &&
                reading.value() > properties.humidityThreshold()) {
            log.error("ALARM: Humidity threshold exceeded in warehouse {}! Current: {}, Threshold: {}",
                    reading.warehouseId(), reading.value(), properties.humidityThreshold());
        }
    }
}
