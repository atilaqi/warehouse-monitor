package com.example.service;

import com.example.config.WarehouseProperties;
import com.example.data.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Value("${spring.kafka.topic}")
    private String topic = "test-topic";

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(properties, kafkaSender, temperatureServer, humidityServer);
        when(properties.id()).thenReturn("warehouse-1");
    }

    @Test
    void testStartSuccessfulInitialization() {
        // Given
        Flux<String> temperatureFlux = Flux.just("sensorId=temp1;value=25.5");
        Flux<String> humidityFlux = Flux.just("sensorId=hum1;value=60.0");

        when(temperatureServer.getMessageFlux()).thenReturn(temperatureFlux);
        when(humidityServer.getMessageFlux()).thenReturn(humidityFlux);
        when(kafkaSender.send(any())).thenReturn(Flux.empty());

        // When
        warehouseService.start();

        // Then
        verify(temperatureServer).start();
        verify(humidityServer).start();
        verify(kafkaSender).send(any());
    }

    @Test
    void testParseReadingValidTemperatureMessage() {
        // Given
        String message = "sensorId=temp1;value=25.5";

        // When
        SensorData result = ReflectionTestUtils.invokeMethod(
                warehouseService,
                "parseReading",
                message,
                SensorData.SensorType.TEMPERATURE
        );

        // Then
        assertNotNull(result);
        assertEquals("warehouse-1", result.warehouseId());
        assertEquals("temp1", result.sensorId());
        assertEquals(25.5, result.value());
        assertEquals(SensorData.SensorType.TEMPERATURE, result.type());
    }

    @Test
    void testParseReadingValidHumidityMessage() {
        // Given
        String message = "sensorId=hum1;value=60.0";

        // When
        SensorData result = ReflectionTestUtils.invokeMethod(
                warehouseService,
                "parseReading",
                message,
                SensorData.SensorType.HUMIDITY
        );

        // Then
        assertNotNull(result);
        assertEquals("warehouse-1", result.warehouseId());
        assertEquals("hum1", result.sensorId());
        assertEquals(60.0, result.value());
        assertEquals(SensorData.SensorType.HUMIDITY, result.type());
    }

    @Test
    void testParseReadingInvalidMessage() {
        // Given
        String invalidMessage = "invalid message format";

        // When/Then
        assertThrows(RuntimeException.class, () ->
                ReflectionTestUtils.invokeMethod(
                        warehouseService,
                        "parseReading",
                        invalidMessage,
                        SensorData.SensorType.TEMPERATURE
                )
        );
    }

    @Test
    void testSendToKafkaSuccessfulSend() {
        // Given
        SensorData sensorData = new SensorData("warehouse-1", "sensor1", 25.5, SensorData.SensorType.TEMPERATURE);
        Flux<SensorData> readings = Flux.just(sensorData);
        when(kafkaSender.send(any())).thenReturn(Flux.empty());

        // When
        Flux<SenderResult<Void>> result = ReflectionTestUtils.invokeMethod(
                warehouseService,
                "sendToKafka",
                readings
        );

        // Then
        assertNotNull(result);
        verify(kafkaSender).send(any());

//        StepVerifier.create(warehouseService.sendToKafka(readings))
//                .expectNext(result)
//                .verifyComplete();
    }

    @Test
    void testShutdown() {
        // When
        warehouseService.shutdown();

        // Then
        verify(temperatureServer).shutdown();
        verify(humidityServer).shutdown();
        verify(kafkaSender).close();
    }

    @Test
    void testErrorHandlingBadTemperatureMessage() {
        // Given
        Flux<String> temperatureFlux = Flux.just("invalid message");
        Flux<String> humidityFlux = Flux.empty();

        when(temperatureServer.getMessageFlux()).thenReturn(temperatureFlux);
        when(humidityServer.getMessageFlux()).thenReturn(humidityFlux);
        when(kafkaSender.send(any())).thenReturn(Flux.empty());

        // When
        warehouseService.start();

        // Then
        verify(temperatureServer).start();
        verify(humidityServer).start();

        // Verify that the warning was logged (if you have a logging framework in your test)
        // You can also use an ArgumentCaptor to verify the exact Kafka sender interactions
        ArgumentCaptor<Publisher<SenderRecord<String, SensorData, Void>>> captor =
                ArgumentCaptor.forClass(Publisher.class);
        verify(kafkaSender).send(captor.capture());

        // Verify the captured Publisher behavior
        Publisher<SenderRecord<String, SensorData, Void>> capturedPublisher = captor.getValue();
        StepVerifier.create(Flux.from(capturedPublisher))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}