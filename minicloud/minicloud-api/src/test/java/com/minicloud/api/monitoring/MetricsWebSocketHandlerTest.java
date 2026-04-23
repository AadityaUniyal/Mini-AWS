package com.minicloud.api.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsWebSocketHandler.
 * Verifies WebSocket connection lifecycle and message broadcasting.
 */
@ExtendWith(MockitoExtension.class)
class MetricsWebSocketHandlerTest {

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private ObjectMapper objectMapper;
    private MetricsWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new MetricsWebSocketHandler(objectMapper);
    }

    @Test
    void testAfterConnectionEstablished_AddsSession() {
        // Arrange
        when(session1.getId()).thenReturn("session-1");

        // Act
        handler.afterConnectionEstablished(session1);

        // Assert
        assertEquals(1, handler.getActiveSessionCount());
    }

    @Test
    void testAfterConnectionClosed_RemovesSession() {
        // Arrange
        when(session1.getId()).thenReturn("session-1");
        handler.afterConnectionEstablished(session1);

        // Act
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // Assert
        assertEquals(0, handler.getActiveSessionCount());
    }

    @Test
    void testBroadcast_SendsMessageToAllSessions() throws Exception {
        // Arrange
        when(session1.getId()).thenReturn("session-1");
        when(session2.getId()).thenReturn("session-2");
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        TestMetricData data = new TestMetricData("cpu", 50.0);

        // Act
        handler.broadcast(data);

        // Assert
        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void testBroadcast_SkipsClosedSessions() throws Exception {
        // Arrange
        when(session1.getId()).thenReturn("session-1");
        when(session2.getId()).thenReturn("session-2");
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        TestMetricData data = new TestMetricData("cpu", 50.0);

        // Act
        handler.broadcast(data);

        // Assert
        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void testBroadcast_WithNoSessions_DoesNothing() {
        // Arrange
        TestMetricData data = new TestMetricData("cpu", 50.0);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> handler.broadcast(data));
    }

    @Test
    void testHandleTransportError_RemovesSession() {
        // Arrange
        when(session1.getId()).thenReturn("session-1");
        handler.afterConnectionEstablished(session1);

        // Act
        handler.handleTransportError(session1, new RuntimeException("Test error"));

        // Assert
        assertEquals(0, handler.getActiveSessionCount());
    }

    @Test
    void testGetActiveSessionCount_ReturnsCorrectCount() {
        // Arrange
        when(session1.getId()).thenReturn("session-1");
        when(session2.getId()).thenReturn("session-2");

        // Act & Assert
        assertEquals(0, handler.getActiveSessionCount());

        handler.afterConnectionEstablished(session1);
        assertEquals(1, handler.getActiveSessionCount());

        handler.afterConnectionEstablished(session2);
        assertEquals(2, handler.getActiveSessionCount());

        handler.afterConnectionClosed(session1, CloseStatus.NORMAL);
        assertEquals(1, handler.getActiveSessionCount());
    }

    // Test data class
    private static class TestMetricData {
        public String metric;
        public double value;

        public TestMetricData(String metric, double value) {
            this.metric = metric;
            this.value = value;
        }
    }
}
