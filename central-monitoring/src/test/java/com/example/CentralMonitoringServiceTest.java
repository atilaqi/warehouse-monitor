package com.example;

import com.example.data.SensorData;
import com.example.config.CentralMonitoringProperties;
import com.example.service.CentralMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CentralMonitoringServiceTest {

    @Mock
    private KafkaReceiver<String, SensorData> kafkaReceiver;

    @Mock
    private CentralMonitoringProperties properties;

    @InjectMocks
    private CentralMonitoringService centralMonitoringService;

    @Mock
    private ReceiverRecord<String, SensorData> mockRecord;

    @Mock
    private ReceiverOffset mockOffset;

    @BeforeEach
    void setUp() {
        when(mockRecord.value()).thenReturn(new SensorData("Warehouse-1", "123", 80.0, SensorData.SensorType.TEMPERATURE));
        when(mockRecord.receiverOffset()).thenReturn(mockOffset);
        when(properties.temperatureThreshold()).thenReturn(75.0);
        when(kafkaReceiver.receive()).thenReturn(Flux.just(mockRecord));
    }

    @Test
    void testStartThresholdExceeded() {
        centralMonitoringService.start();
        verify(mockOffset, timeout(1000)).acknowledge();
    }
}

