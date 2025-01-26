package com.example.service;

import com.example.config.WarehouseProperties;
import com.example.data.SensorData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

@Service
@Slf4j
public class WarehouseService {
    private final UDPServer temperatureServer;
    private final UDPServer humidityServer;
    private final KafkaSender<String, SensorData> kafkaSender;
    private final WarehouseProperties properties;


    private String topic;

    public WarehouseService(
            WarehouseProperties properties,
            KafkaSender<String, SensorData> kafkaSender,
            @Qualifier("temperatureServer") UDPServer temperatureServer,
            @Qualifier("humidityServer") UDPServer humidityServer,
            @Value("${spring.kafka.topic}") String topic

    ) {
        this.properties = properties;
        this.kafkaSender = kafkaSender;
        this.temperatureServer = temperatureServer;
        this.humidityServer = humidityServer;
        this.topic = topic;
    }

    @PostConstruct
    public void start() {
        temperatureServer.start();
        humidityServer.start();

        Flux.merge(
                        temperatureServer.getMessageFlux()
                                .map(msg -> parseReading(msg, SensorData.SensorType.TEMPERATURE))
                                .onErrorContinue((ex, msg) -> log.warn("Skipping bad message: {}", msg, ex)),
                        humidityServer.getMessageFlux()
                                .map(msg -> parseReading(msg, SensorData.SensorType.HUMIDITY))
                                .onErrorContinue((ex, msg) -> log.warn("Skipping bad message: {}", msg, ex))
                )
                .transform(this::sendToKafka)
                .subscribe();

        log.info("Warehouse service started. Warehouse ID: {}", properties.id());
    }

     private SensorData parseReading(String message, SensorData.SensorType type) {
        System.out.println("message "+ message);
        String[] parts = message.split(";");
        String sensorId = parts[0].split("=")[1].trim();
        double value = Double.parseDouble(parts[1].split("=")[1].trim());
        return new SensorData(properties.id(), sensorId, value, type);
    }

     private Flux<SenderResult<Void>> sendToKafka(Flux<SensorData> readings) {
        return readings
                .doOnNext(reading -> log.info("Sending reading: {}", reading))
                .map(reading -> SenderRecord.<String, SensorData, Void>create(
                        topic,
                        null,
                        System.currentTimeMillis(),
                        reading.sensorId(),
                        reading,
                        null
                ))
                .as(kafkaSender::send); // Use .as() instead of .transform()
    }

    @PreDestroy
    public void shutdown() {
        temperatureServer.shutdown();
        humidityServer.shutdown();
        kafkaSender.close();
    }
}
