package com.example.galaxy_sim.web;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.simulation.Simulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * REST controller for the galaxy simulation web interface.
 * Provides endpoints for simulation control and data retrieval.
 */
@Controller
public class SimulationController {

    private final Simulator simulator;
    private boolean isRunning = false;
    private Thread simulationThread;

    @Autowired
    public SimulationController(Simulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Serves the main simulation page
     */
    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    /**
     * Gets current simulation state including particles and metadata
     */
    @GetMapping("/api/state")
    @ResponseBody
    public Map<String, Object> getSimulationState() {
        Map<String, Object> state = new HashMap<>();

        // Get current particles
        List<Particle> particles = simulator.getParticles();

        // Convert particles to simplified format for visualization
        List<Map<String, Object>> particleData = particles.stream()
            .map(this::particleToMap)
            .toList();

        state.put("particles", particleData);
        state.put("particleCount", particles.size());
        state.put("currentTime", simulator.getCurrentTime());
        state.put("timeScale", simulator.getTimeScale());
        state.put("energy", simulator.getCurrentEnergy());
        state.put("energyDrift", simulator.getEnergyDrift());
        state.put("isRunning", isRunning);
        state.put("fastForward", simulator.isFastForward());

        // Calculate statistics for UI
        double avgVelocity = particles.stream()
            .mapToDouble(p -> Math.sqrt(p.vx() * p.vx() + p.vy() * p.vy()))
            .average().orElse(0.0);

        double avgDistanceToCenter = particles.stream()
            .mapToDouble(p -> Math.sqrt(p.x() * p.x() + p.y() * p.y()))
            .average().orElse(0.0);

        state.put("avgVelocity", avgVelocity);
        state.put("avgDistanceToCenter", avgDistanceToCenter);

        return state;
    }

    /**
     * Converts a particle to a map for JSON serialization
     */
    private Map<String, Object> particleToMap(Particle particle) {
        Map<String, Object> map = new HashMap<>();
        map.put("x", particle.x());
        map.put("y", particle.y());
        map.put("vx", particle.vx());
        map.put("vy", particle.vy());
        map.put("mass", particle.mass());
        map.put("type", particle.stellarType().toString());
        map.put("age", particle.age());
        map.put("metallicity", particle.metallicity());
        map.put("distanceToSMBH", particle.distanceToSMBH());
        return map;
    }

    /**
     * Starts the simulation
     */
    @PostMapping("/api/start")
    @ResponseBody
    public Map<String, Object> startSimulation() {
        if (!isRunning) {
            isRunning = true;
            simulationThread = new Thread(this::runSimulation);
            simulationThread.start();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("isRunning", isRunning);
        return response;
    }

    /**
     * Pauses the simulation
     */
    @PostMapping("/api/pause")
    @ResponseBody
    public Map<String, Object> pauseSimulation() {
        isRunning = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "paused");
        response.put("isRunning", isRunning);
        return response;
    }

    /**
     * Resets the simulation to initial state
     */
    @PostMapping("/api/reset")
    @ResponseBody
    public Map<String, Object> resetSimulation() {
        isRunning = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
        }

        simulator.reset();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "reset");
        response.put("isRunning", isRunning);
        return response;
    }

    /**
     * Sets the time scale
     */
    @PostMapping("/api/timeScale")
    @ResponseBody
    public Map<String, Object> setTimeScale(@RequestBody Map<String, Double> request) {
        double timeScale = request.getOrDefault("timeScale", 100.0);
        simulator.setTimeScale(timeScale);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("timeScale", simulator.getTimeScale());
        return response;
    }

    /**
     * Toggles fast-forward mode
     */
    @PostMapping("/api/fastForward")
    @ResponseBody
    public Map<String, Object> toggleFastForward(@RequestBody Map<String, Boolean> request) {
        boolean fastForward = request.getOrDefault("enabled", false);
        simulator.setFastForward(fastForward);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("fastForward", simulator.isFastForward());
        response.put("timeScale", simulator.getTimeScale());
        return response;
    }

    /**
     * Toggles bar potential
     */
    @PostMapping("/api/barPotential")
    @ResponseBody
    public Map<String, Object> toggleBarPotential(@RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", false);
        simulator.setBarEnabled(enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("barEnabled", enabled);
        return response;
    }

    /**
     * Sets the number of particles
     */
    @PostMapping("/api/particleCount")
    @ResponseBody
    public Map<String, Object> setParticleCount(@RequestBody Map<String, Integer> request) {
        int count = request.getOrDefault("count", 1000);
        count = Math.max(100, Math.min(50000, count)); // Limit range

        // Pause simulation during particle count change
        boolean wasRunning = isRunning;
        if (isRunning) {
            pauseSimulation();
        }

        simulator.setParticleCount(count);

        // Resume if it was running
        if (wasRunning) {
            startSimulation();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("particleCount", simulator.getParticleCount());
        return response;
    }

    /**
     * Gets simulation statistics
     */
    @GetMapping("/stats")
    @ResponseBody
    public Map<String, Object> getSimulationStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Particle> particles = simulator.getParticles();

        // Calculate statistics
        double avgVelocity = particles.stream()
            .mapToDouble(p -> Math.sqrt(p.vx() * p.vx() + p.vy() * p.vy()))
            .average().orElse(0.0);

        double avgDistanceToCenter = particles.stream()
            .mapToDouble(p -> Math.sqrt(p.x() * p.x() + p.y() * p.y()))
            .average().orElse(0.0);

        long diskParticles = particles.stream()
            .filter(p -> p.stellarType() == Particle.StellarType.MAIN_SEQUENCE)
            .count();

        long bulgeParticles = particles.stream()
            .filter(p -> p.stellarType() == Particle.StellarType.GIANT)
            .count();

        stats.put("avgVelocity", avgVelocity);
        stats.put("avgDistanceToCenter", avgDistanceToCenter);
        stats.put("diskParticles", diskParticles);
        stats.put("bulgeParticles", bulgeParticles);
        stats.put("totalEnergy", simulator.getCurrentEnergy());
        stats.put("energyDrift", simulator.getEnergyDrift());

        return stats;
    }

	/**
	 * Main simulation loop
	 */
	private void runSimulation() {
		long lastRealTime = System.nanoTime(); // Use nanoTime for better precision

		while (isRunning && !Thread.currentThread().isInterrupted()) {
			try {
				long currentRealTime = System.nanoTime();
				// Real time elapsed in seconds
				double realWorldDeltaTime = (currentRealTime - lastRealTime) / 1_000_000_000.0;
				lastRealTime = currentRealTime;

				// Clamp delta time to avoid huge simulation jumps if the thread was paused
				if (realWorldDeltaTime > 0.1) {
					realWorldDeltaTime = 0.1;
				}

				// Get the time scale (e.g., 100,000 years/sec)
				double timeScale = simulator.getTimeScale();

				// Calculate the amount of simulation time to advance in this frame
				double simulationDeltaTime = realWorldDeltaTime * timeScale;

				// Advance the simulation by that amount of time.
				// We assume step(double years) exists for stable physics.
				simulator.step(simulationDeltaTime);

				// Sleep to yield CPU and target a reasonable backend update rate (~60Hz)
				Thread.sleep(16);

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				System.err.println("Simulation error: " + e.getMessage());
				e.printStackTrace();
			}
		}

		isRunning = false;
	}}
