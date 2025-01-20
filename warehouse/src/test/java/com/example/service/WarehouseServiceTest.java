package com.example.service;

import com.example.config.WarehouseProperties;
import com.example.data.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {
    @Mock
    private UDPServer temperatureServer;

    @Mock
    private UDPServer humidityServer;

    @Mock
    private KafkaSender<String, SensorData> kafkaSender;

    @Mock
    private WarehouseProperties properties;

//    @InjectMocks
    private WarehouseService warehouseService;

    @Value("${spring.kafka.topic}")
    private String topic = "test-topic";

    @BeforeEach
    void setUp() {
//        MockitoAnnotations.openMocks(this);
        when(properties.id()).thenReturn("Warehouse-1");
        when(temperatureServer.getMessageFlux()).thenReturn(Flux.just("sensorId=Temp1; value=25.5"));
        when(humidityServer.getMessageFlux()).thenReturn(Flux.just("sensorId=Hum1; value=60.0"));
        when(kafkaSender.send(any())).thenReturn(Flux.empty()); // Mock Kafka sender to return empty Flux

        warehouseService = new WarehouseService(properties, kafkaSender, temperatureServer, humidityServer);
    }

    @Test
    void testParseReading() {
        String message = "sensorId=123;value=25.5";
        SensorData result = warehouseService.parseReading(message, SensorData.SensorType.TEMPERATURE);

        assertEquals("Warehouse-1", result.warehouseId());
        assertEquals("123", result.sensorId());
        assertEquals(25.5, result.value());
        assertEquals(SensorData.SensorType.TEMPERATURE, result.type());
    }

    @Test
    void testStartAndMessageProcessing() {
        Flux<String> temperatureMessages = Flux.just("sensorId=123;value=22.5");
        Flux<String> humidityMessages = Flux.just("sensorId=456;value=55.0");

        when(temperatureServer.getMessageFlux()).thenReturn(temperatureMessages);
        when(humidityServer.getMessageFlux()).thenReturn(humidityMessages);

        when(kafkaSender.send(any(Flux.class)))
                .thenReturn(Flux.just(mock(SenderResult.class)));

        warehouseService.start();

        verify(temperatureServer).start();
        verify(humidityServer).start();
    }

    @Test
    void testSendToKafka() {
        SensorData sensorData = new SensorData("Warehouse-1", "123", 22.5, SensorData.SensorType.TEMPERATURE);
        Flux<SensorData> readings = Flux.just(sensorData);

        SenderResult<Void> senderResult = mock(SenderResult.class);
        when(kafkaSender.send(any(Flux.class))).thenReturn(Flux.just(senderResult));

        StepVerifier.create(warehouseService.sendToKafka(readings))
                .expectNext(senderResult)
                .verifyComplete();
    }

    @Test
    void testShutdown() {
        warehouseService.shutdown();

        verify(temperatureServer).shutdown();
        verify(humidityServer).shutdown();
        verify(kafkaSender).close();
    }
}

