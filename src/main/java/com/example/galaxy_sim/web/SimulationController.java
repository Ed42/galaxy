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

	@GetMapping("/")
	public String index(Model model) {
		return "index";
	}

	@GetMapping("/api/state")
	@ResponseBody
	public Map<String, Object> getSimulationState() {
		Map<String, Object> state = new HashMap<>();
		List<Particle> particles = simulator.getParticles();
		List<Map<String, Object>> particleData = particles.stream().map(this::particleToMap).toList();

		state.put("particles", particleData);
		state.put("particleCount", particles.size());
		state.put("currentTime", simulator.getCurrentTime());
		state.put("timeScale", simulator.getTimeScale());
		state.put("energy", simulator.getCurrentEnergy());
		state.put("energyDrift", simulator.getEnergyDrift());
		state.put("isRunning", isRunning);
		state.put("fastForward", simulator.isFastForward());

		if (simulator.isQuadtreeOverlayEnabled()) {
			state.put("quadtreeBounds", simulator.getQuadtreeBounds());
		}

		double avgVelocity = particles.stream().mapToDouble(p -> Math.sqrt(p.vx() * p.vx() + p.vy() * p.vy())).average().orElse(0.0);
		double avgDistanceToCenter = particles.stream().mapToDouble(Particle::distanceToSMBH).average().orElse(0.0);
		state.put("avgVelocity", avgVelocity);
		state.put("avgDistanceToCenter", avgDistanceToCenter);

		return state;
	}

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

	@PostMapping("/api/start")
	@ResponseBody
	public Map<String, Object> startSimulation() {
		if (!isRunning) {
			isRunning = true;
			simulationThread = new Thread(this::runSimulation);
			simulationThread.start();
		}
		return Map.of("status", "started", "isRunning", isRunning);
	}

	@PostMapping("/api/pause")
	@ResponseBody
	public Map<String, Object> pauseSimulation() {
		isRunning = false;
		if (simulationThread != null) {
			simulationThread.interrupt();
		}
		return Map.of("status", "paused", "isRunning", isRunning);
	}

	@PostMapping("/api/reset")
	@ResponseBody
	public Map<String, Object> resetSimulation() {
		boolean wasRunning = isRunning;
		isRunning = false;
		if (simulationThread != null) {
			simulationThread.interrupt();
		}
		simulator.reset();
		if (wasRunning) {
			startSimulation();
		}
		return Map.of("status", "reset", "isRunning", isRunning);
	}

	@PostMapping("/api/timeScale")
	@ResponseBody
	public Map<String, Object> setTimeScale(@RequestBody Map<String, Double> request) {
		double timeScale = request.getOrDefault("timeScale", 100.0);
		simulator.setTimeScale(timeScale);
		return Map.of("status", "updated", "timeScale", simulator.getTimeScale());
	}

	@PostMapping("/api/fastForward")
	@ResponseBody
	public Map<String, Object> toggleFastForward(@RequestBody Map<String, Boolean> request) {
		boolean fastForward = request.getOrDefault("enabled", false);
		simulator.setFastForward(fastForward);
		return Map.of("status", "updated", "fastForward", simulator.isFastForward());
	}

	@PostMapping("/api/barPotential")
	@ResponseBody
	public Map<String, Object> toggleBarPotential(@RequestBody Map<String, Boolean> request) {
		boolean enabled = request.getOrDefault("enabled", false);
		simulator.setBarEnabled(enabled);
		return Map.of("status", "updated", "barEnabled", enabled);
	}

	@PostMapping("/api/quadtree")
	@ResponseBody
	public Map<String, Object> setQuadtreeSettings(@RequestBody Map<String, Object> request) {
		boolean enabled = (boolean) request.getOrDefault("enabled", false);
		int depth = ((Number) request.getOrDefault("depth", 8)).intValue();
		simulator.setQuadtreeOverlayEnabled(enabled);
		simulator.setQuadtreeMaxDepth(depth);
		return Map.of("status", "updated", "quadtreeEnabled", enabled, "quadtreeDepth", depth);
	}

	@PostMapping("/api/particleCount")
	@ResponseBody
	public Map<String, Object> setParticleCount(@RequestBody Map<String, Integer> request) {
		int count = request.getOrDefault("count", 1000);
		simulator.setParticleCount(count);
		return Map.of("status", "updated", "particleCount", simulator.getParticleCount());
	}

	private void runSimulation() {
		long lastRealTime = System.nanoTime();
		final double MAX_STABLE_DT_YEARS = 10000.0;
		final int MAX_SUB_STEPS_PER_FRAME = 250;

		while (isRunning && !Thread.currentThread().isInterrupted()) {
			try {
				long currentRealTime = System.nanoTime();
				double realWorldDeltaTime = (currentRealTime - lastRealTime) / 1_000_000_000.0;
				lastRealTime = currentRealTime;

				if (realWorldDeltaTime > 0.1) realWorldDeltaTime = 0.1;

				double timeScale = simulator.getTimeScale();
				double requestedFrameTime = realWorldDeltaTime * timeScale;
				int numSubSteps = (int) Math.ceil(requestedFrameTime / MAX_STABLE_DT_YEARS);
				if (numSubSteps > MAX_SUB_STEPS_PER_FRAME) numSubSteps = MAX_SUB_STEPS_PER_FRAME;

				numSubSteps = Math.max(1, numSubSteps);
				double subStepDt = requestedFrameTime / numSubSteps;

				for (int i = 0; i < numSubSteps; i++) {
					if (!isRunning) break;
					simulator.step(subStepDt);
				}
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
	}
}
