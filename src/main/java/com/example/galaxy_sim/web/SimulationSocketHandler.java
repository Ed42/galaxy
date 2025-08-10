package com.example.galaxy_sim.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SimulationSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    public void broadcast(Map<String, Object> state) {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String stateJson = objectMapper.writeValueAsString(state);
            TextMessage message = new TextMessage(stateJson);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        // Handle exceptions, e.g., client closed connection abruptly
                        sessions.remove(session);
                    }
                }
            }
        } catch (IOException e) {
            // Handle JSON processing exception
            e.printStackTrace();
        }
    }
}
