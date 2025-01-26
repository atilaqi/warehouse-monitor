package com.example.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
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

    private String topic = "test-topic";

    private WarehouseService warehouseService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        // Setup logger capture
        Logger logger = (Logger) LoggerFactory.getLogger(WarehouseService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        warehouseService = new WarehouseService(properties, kafkaSender, temperatureServer, humidityServer, topic);
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
        String invalidMsg = "invalid message";
        // Given: Simulating an invalid message in the temperature stream
        Flux<String> temperatureFlux = Flux.just(invalidMsg);
        Flux<String> humidityFlux = Flux.empty(); // No humidity data

        when(temperatureServer.getMessageFlux()).thenReturn(temperatureFlux);
        when(humidityServer.getMessageFlux()).thenReturn(humidityFlux);
        when(kafkaSender.send(any())).thenReturn(Flux.empty()); // No valid messages sent to Kafka

        // When
        warehouseService.start();

        // Then
        verify(temperatureServer).start();
        verify(humidityServer).start();

        // Capture Kafka messages
        ArgumentCaptor<Publisher<SenderRecord<String, SensorData, Void>>> captor =
                ArgumentCaptor.forClass(Publisher.class);
        verify(kafkaSender).send(captor.capture());

        // Validate that Kafka does not receive any valid messages
        StepVerifier.create(Flux.from(captor.getValue()))
                .expectSubscription()
                .expectNextCount(0)
                .expectComplete() // Stream should complete without sending anything
                .verify(Duration.ofSeconds(5));

        // Verify that a warning log was generated for the invalid message
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains(String.format(
                        "Skipping bad message: %s", invalidMsg
                ));

    }


    @Test
    void testValidHumidityMessageSentToKafka() {
        String validHumidityMsg = "sensorId=hum1;value=60.0";
        String expectedTopic = "test-topic"; // Match the actual topic being used

        // Given: Simulating a valid humidity message in the humidity stream
        Flux<String> temperatureFlux = Flux.empty();
        Flux<String> humidityFlux = Flux.just(validHumidityMsg);

        when(temperatureServer.getMessageFlux()).thenReturn(temperatureFlux);
        when(humidityServer.getMessageFlux()).thenReturn(humidityFlux);
        when(kafkaSender.send(any())).thenReturn(Flux.empty());

        // When
        warehouseService.start();

        // Then
        verify(temperatureServer).start();
        verify(humidityServer).start();

        // Capture Kafka messages
        ArgumentCaptor<Publisher<SenderRecord<String, SensorData, Void>>> captor =
                ArgumentCaptor.forClass(Publisher.class);
        verify(kafkaSender).send(captor.capture());

        // Extract and print messages for debugging
        Flux.from(captor.getValue()).doOnNext(record -> {
            System.out.println("DEBUG: Captured Record -> Topic: " + record.topic() + ", Data: " + record.value());
        }).blockLast();

        // Validate that Kafka receives a valid humidity message with a topic
        StepVerifier.create(Flux.from(captor.getValue()))
                .expectSubscription()
                .expectNextMatches(record -> {
                    SensorData data = record.value();
                    String topic = record.topic();
                    System.out.println("DEBUG: Validating Record - Topic: " + topic + ", Data: " + data);

                    return topic != null && topic.equals(expectedTopic) &&
                            data != null && SensorData.SensorType.HUMIDITY.equals(data.type()) &&
                            data.value() == 60.0 &&
                            "warehouse-1".equals(data.warehouseId()); // If required
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

}
