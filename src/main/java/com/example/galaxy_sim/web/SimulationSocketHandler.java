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

	/**
	 * Broadcasts the simulation state to all connected clients.
	 * This method is synchronized to prevent multiple threads from sending messages
	 * concurrently, which would cause an IllegalStateException.
	 * @param state The simulation state to broadcast.
	 */
	public synchronized void broadcast(Map<String, Object> state) {
		try {
			String message = objectMapper.writeValueAsString(state);
			for (WebSocketSession session : sessions) {
				if (session.isOpen()) {
					try {
						session.sendMessage(new TextMessage(message));
					} catch (IOException e) {
						// Handle exceptions for a single failed send if necessary
						System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Failed to serialize simulation state: " + e.getMessage());
		}
	}
}
