package com.example.galaxy_sim.web;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.simulation.Simulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the galaxy simulation web interface.
 */
@Controller
public class SimulationController {
	private final Simulator simulator;
	private final SimulationSocketHandler socketHandler;
	private volatile boolean isRunning = false;
	private Thread simulationThread;

	@Autowired
	public SimulationController(Simulator simulator, SimulationSocketHandler socketHandler) {
		this.simulator = simulator;
		this.socketHandler = socketHandler;
	}

	@GetMapping("/")
	public String index(Model model) {
		return "index";
	}

	@PostMapping("/api/start") @ResponseBody
	public Map<String, Object> startSimulation() {
		if (!isRunning) {
			isRunning = true;
			simulationThread = Thread.ofVirtual().start(this::runSimulation);
		}
		return Map.of("status", "started", "isRunning", isRunning);
	}

	@PostMapping("/api/pause") @ResponseBody
	public Map<String, Object> pauseSimulation() {
		isRunning = false;
		if (simulationThread != null) {
			simulationThread.interrupt();
		}
		broadcastState(); // Send a final state update on pause
		return Map.of("status", "paused", "isRunning", isRunning);
	}

	@PostMapping("/api/reset") @ResponseBody
	public Map<String, Object> resetSimulation() {
		if (isRunning) {
			pauseSimulation();
		}
		simulator.reset();
		broadcastState();
		return Map.of("status", "reset");
	}

	@PostMapping("/api/timeScale") @ResponseBody
	public Map<String, Object> setTimeScale(@RequestBody Map<String, Double> request) {
		simulator.setTimeScale(request.getOrDefault("timeScale", 100.0));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/fastForward") @ResponseBody
	public Map<String, Object> toggleFastForward(@RequestBody Map<String, Boolean> request) {
		simulator.setFastForward(request.getOrDefault("enabled", false));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/barPotential") @ResponseBody
	public Map<String, Object> toggleBarPotential(@RequestBody Map<String, Boolean> request) {
		simulator.setBarEnabled(request.getOrDefault("enabled", false));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/quadtree") @ResponseBody
	public Map<String, Object> setQuadtreeSettings(@RequestBody Map<String, Object> request) {
		simulator.setQuadtreeOverlayEnabled((boolean) request.getOrDefault("enabled", false));
		simulator.setQuadtreeMaxDepth(((Number) request.getOrDefault("depth", 8)).intValue());
		return Map.of("status", "updated");
	}

	@PostMapping("/api/particleCount") @ResponseBody
	public Map<String, Object> setParticleCount(@RequestBody Map<String, Integer> request) {
		simulator.setParticleCount(request.getOrDefault("count", 1000));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/spiralArms") @ResponseBody
	public Map<String, Object> setSpiralArmCount(@RequestBody Map<String, Integer> request) {
		simulator.setNumberOfArms(request.getOrDefault("arms", 2));
		return Map.of("status", "updated");
	}

	private void broadcastState() {
		Map<String, Object> state = new HashMap<>();
		List<Particle> particles = simulator.getParticles();
		if (particles == null) return;

		List<Object> flatParticleData = new ArrayList<>(particles.size() * 3);
		for (Particle p : particles) {
			flatParticleData.add(p.x());
			flatParticleData.add(p.y());
			flatParticleData.add(p.stellarType() == Particle.StellarType.GIANT ? 1 : 0);
		}
		state.put("particles", flatParticleData);
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

		socketHandler.broadcast(state);
	}

	private void runSimulation() {
		long lastRealTime = System.nanoTime();
		final double MAX_STABLE_DT_YEARS = 10000.0;
		final int MAX_SUB_STEPS_PER_FRAME = 250;
		final long broadcastIntervalNanos = 50_000_000; // 50ms -> 20 FPS
		long lastBroadcastTime = 0;

		while (isRunning && !Thread.currentThread().isInterrupted()) {
			try {
				long currentRealTime = System.nanoTime();
				double realWorldDeltaTime = (currentRealTime - lastRealTime) / 1_000_000_000.0;
				lastRealTime = currentRealTime;

				double timeScale = simulator.getTimeScale();
				double requestedFrameTime = realWorldDeltaTime * timeScale;

				int numSubSteps;
				double subStepDt;

				int neededSteps = (int) Math.ceil(requestedFrameTime / MAX_STABLE_DT_YEARS);

				if (neededSteps > MAX_SUB_STEPS_PER_FRAME) {
					numSubSteps = MAX_SUB_STEPS_PER_FRAME;
					subStepDt = MAX_STABLE_DT_YEARS;
				} else {
					numSubSteps = Math.max(1, neededSteps);
					subStepDt = requestedFrameTime / numSubSteps;
				}

				for (int i = 0; i < numSubSteps; i++) {
					if (!isRunning) break;
					simulator.step(subStepDt);
				}

				if (currentRealTime - lastBroadcastTime > broadcastIntervalNanos) {
					broadcastState();
					lastBroadcastTime = currentRealTime;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		isRunning = false;
	}
}
