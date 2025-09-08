package com.galaxy.sim.web;

import com.galaxy.sim.core.Simulator;
import com.galaxy.sim.core.SimulationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service to manage simulation execution and broadcast updates
 */
@Service
public class SimulationService {
    private static final Logger logger = Logger.getLogger(SimulationService.class.getName());
    
    private final Simulator simulator;
    private final SimpMessagingTemplate messagingTemplate;
    private ScheduledExecutorService executorService;
    private volatile boolean simulationRunning = false;
    
    @Autowired
    public SimulationService(Simulator simulator, SimpMessagingTemplate messagingTemplate) {
        this.simulator = simulator;
        this.messagingTemplate = messagingTemplate;
        this.executorService = Executors.newScheduledThreadPool(2);
    }
    
    @PostConstruct
    public void init() {
        // Initialize simulation with default particle count
        simulator.initializeParticles(1000);
        logger.info("SimulationService initialized");
    }
    
    @PreDestroy
    public void cleanup() {
        stopSimulation();
        simulator.shutdown();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        logger.info("SimulationService cleaned up");
    }
    
    /**
     * Start the simulation loop
     */
    public void startSimulation() {
        if (simulationRunning) return;
        
        simulationRunning = true;
        
        // Run simulation steps at 60 Hz
        executorService.scheduleAtFixedRate(() -> {
            if (simulationRunning && simulator.isRunning() && !simulator.isPaused()) {
                try {
                    simulator.step();
                } catch (Exception e) {
                    logger.severe("Error in simulation step: " + e.getMessage());
                }
            }
        }, 0, 16, TimeUnit.MILLISECONDS);  // ~60 FPS
        
        // Broadcast state updates at 10 Hz for visualization
        executorService.scheduleAtFixedRate(() -> {
            if (simulationRunning) {
                try {
                    broadcastState();
                } catch (Exception e) {
                    logger.severe("Error broadcasting state: " + e.getMessage());
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);  // 10 FPS for network updates
        
        logger.info("Simulation loop started");
    }
    
    /**
     * Stop the simulation loop
     */
    public void stopSimulation() {
        simulationRunning = false;
        logger.info("Simulation loop stopped");
    }
    
    /**
     * Broadcast current simulation state via WebSocket
     */
    private void broadcastState() {
        SimulationState state = simulator.getState();
        
        // Create a simplified state for frequent updates
        SimulationStateDTO stateDTO = new SimulationStateDTO(state);
        
        // Broadcast to all connected clients
        messagingTemplate.convertAndSend("/topic/simulation", stateDTO);
    }
    
    
    /**
     * DTO for efficient state transmission
     */
    public static class SimulationStateDTO {
        public final ParticleData[] particles;
        public final String time;
        public final String timeScale;
        public final double energyDrift;
        public final boolean running;
        public final boolean paused;
        public final boolean fastForward;
        public final boolean barEnabled;
        public final double[][] quadtreeLines;
        public final int diskStars;
        public final int bulgeStars;
        public final int habitableRegions;
        
        public SimulationStateDTO(SimulationState state) {
            // Convert particles to lightweight format
            this.particles = state.particles().stream()
                .map(p -> new ParticleData(
                    p.position().x(),
                    p.position().y(),
                    p.type().ordinal(),
                    p.habitable()
                ))
                .toArray(ParticleData[]::new);
            
            this.time = state.getFormattedTime();
            this.timeScale = state.getFormattedTimeScale();
            this.energyDrift = state.getEnergyDriftPercent();
            this.running = state.running();
            this.paused = state.paused();
            this.fastForward = state.fastForward();
            this.barEnabled = state.barEnabled();
            
            // Convert quadtree lines
            this.quadtreeLines = state.quadtreeLines().toArray(new double[0][]);
            
            // Get particle statistics
            var stats = state.getParticleStats();
            this.diskStars = stats.diskStars();
            this.bulgeStars = stats.bulgeStars();
            this.habitableRegions = stats.habitableRegions();
        }
    }
    
    /**
     * Lightweight particle data for transmission
     */
    public static class ParticleData {
        public final double x;
        public final double y;
        public final int type;
        public final boolean habitable;
        
        public ParticleData(double x, double y, int type, boolean habitable) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.habitable = habitable;
        }
    }
}
