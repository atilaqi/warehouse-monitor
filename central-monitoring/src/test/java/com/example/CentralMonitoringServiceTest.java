package com.example;

import com.example.config.CentralMonitoringProperties;
import com.example.data.SensorData;
import com.example.service.CentralMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CentralMonitoringServiceTest {

    @Mock
    private KafkaReceiver<String, SensorData> kafkaReceiver;

    @Mock
    private CentralMonitoringProperties properties;

    private CentralMonitoringService monitoringService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        // Setup logger capture
        Logger logger = (Logger) LoggerFactory.getLogger(CentralMonitoringService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        monitoringService = new CentralMonitoringService(kafkaReceiver, properties);
    }

    @Test
    void shouldStartAndLogInitialization() {
        // Arrange
        when(kafkaReceiver.receive()).thenReturn(Flux.empty());

        // Act
        monitoringService.start();

        // Assert
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getMessage)
                .contains("Central monitoring service started");
    }

    @Test
    void shouldTriggerTemperatureAlarmWhenThresholdExceeded() {
        // Arrange
        double threshold = 30.0;
        double currentTemp = 35.0;
        String warehouseId = "WH-001";
        String sensorId = "123";

        when(properties.temperatureThreshold()).thenReturn(threshold);
        ReceiverRecord<String, SensorData> record = createMockRecord(
                new SensorData(warehouseId, sensorId, currentTemp, SensorData.SensorType.TEMPERATURE)
        );
        when(kafkaReceiver.receive()).thenReturn(Flux.just(record));

        // Act
        monitoringService.start();

        // Assert
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains(String.format(
                        "ALARM: Temperature threshold exceeded in warehouse %s! Current: %s, Threshold: %s",
                        warehouseId, currentTemp, threshold
                ));

        verify(record.receiverOffset()).acknowledge();
    }

    @Test
    void shouldTriggerHumidityAlarmWhenThresholdExceeded() {
        // Arrange
        double threshold = 60.0;
        double currentHumidity = 65.0;
        String warehouseId = "WH-001";
        String sensorId = "456";

        when(properties.humidityThreshold()).thenReturn(threshold);
        ReceiverRecord<String, SensorData> record = createMockRecord(
                new SensorData(warehouseId,sensorId, currentHumidity, SensorData.SensorType.HUMIDITY)
        );
        when(kafkaReceiver.receive()).thenReturn(Flux.just(record));

        // Act
        monitoringService.start();

        // Assert
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains(String.format(
                        "ALARM: Humidity threshold exceeded in warehouse %s! Current: %s, Threshold: %s",
                        warehouseId, currentHumidity, threshold
                ));

        verify(record.receiverOffset()).acknowledge();
    }

    @Test
    void shouldNotTriggerAlarmWhenBelowThreshold() {
        // Arrange
        double threshold = 30.0;
        double currentTemp = 25.0;
        String warehouseId = "WH-001";
        String sensorId = "123";

        when(properties.temperatureThreshold()).thenReturn(threshold);
        ReceiverRecord<String, SensorData> record = createMockRecord(
                new SensorData(warehouseId, sensorId, currentTemp, SensorData.SensorType.TEMPERATURE)
        );
        when(kafkaReceiver.receive()).thenReturn(Flux.just(record));

        // Act
        monitoringService.start();

        // Assert
        assertThat(logAppender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .isEmpty();

        verify(record.receiverOffset()).acknowledge();
    }

    private ReceiverRecord<String, SensorData> createMockRecord(SensorData sensorData) {
        ReceiverRecord<String, SensorData> record = mock(ReceiverRecord.class);
        ReceiverOffset offset = mock(ReceiverOffset.class);

        when(record.value()).thenReturn(sensorData);
        when(record.receiverOffset()).thenReturn(offset);

        return record;
    }
}
