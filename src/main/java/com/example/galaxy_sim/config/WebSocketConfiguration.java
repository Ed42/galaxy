package com.example.galaxy_sim.config;

import com.example.galaxy_sim.web.SimulationSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simulationSocketHandler(), "/simulation-socket").setAllowedOrigins("*");
    }

    @Bean
    public SimulationSocketHandler simulationSocketHandler() {
        return new SimulationSocketHandler();
    }
}
