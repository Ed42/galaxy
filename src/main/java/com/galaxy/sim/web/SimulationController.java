package com.galaxy.sim.web;

import com.galaxy.sim.core.Simulator;
import com.galaxy.sim.core.SimulationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for simulation control
 */
@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {
    
    private final Simulator simulator;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimulationService simulationService;
    
    @Autowired
    public SimulationController(Simulator simulator, 
                               SimpMessagingTemplate messagingTemplate,
                               SimulationService simulationService) {
        this.simulator = simulator;
        this.messagingTemplate = messagingTemplate;
        this.simulationService = simulationService;
    }
    
    /**
     * Initialize simulation with specified number of particles
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initialize(@RequestParam(defaultValue = "1000") int particleCount) {
        simulator.initializeParticles(particleCount);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("particleCount", particleCount);
        response.put("message", "Simulation initialized with " + particleCount + " particles");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Start the simulation
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        simulator.start();
        simulationService.startSimulation();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "running");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Pause the simulation
     */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause() {
        simulator.pause();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "paused");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Resume the simulation
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume() {
        simulator.resume();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "running");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop the simulation
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        simulationService.stopSimulation();
        simulator.stop();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "stopped");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset the simulation
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        simulator.reset();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "reset");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Set time scale
     */
    @PostMapping("/timescale")
    public ResponseEntity<Map<String, Object>> setTimeScale(@RequestParam double scale) {
        simulator.setTimeScale(scale);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("timeScale", scale);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Toggle fast forward mode
     */
    @PostMapping("/fastforward")
    public ResponseEntity<Map<String, Object>> toggleFastForward(@RequestParam boolean enabled) {
        simulator.setFastForward(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("fastForward", enabled);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Toggle bar potential
     */
    @PostMapping("/bar")
    public ResponseEntity<Map<String, Object>> toggleBar(@RequestParam boolean enabled) {
        simulator.setBarEnabled(enabled);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("barEnabled", enabled);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current simulation state
     */
    @GetMapping("/state")
    public ResponseEntity<SimulationState> getState() {
        return ResponseEntity.ok(simulator.getState());
    }
    
    /**
     * Get simulation status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        SimulationState state = simulator.getState();
        
        Map<String, Object> status = new HashMap<>();
        status.put("running", state.running());
        status.put("paused", state.paused());
        status.put("time", state.getFormattedTime());
        status.put("timeScale", state.getFormattedTimeScale());
        status.put("particleCount", state.particles().size());
        status.put("energyDrift", state.getEnergyDriftPercent());
        status.put("fastForward", state.fastForward());
        status.put("barEnabled", state.barEnabled());
        
        return ResponseEntity.ok(status);
    }
}
