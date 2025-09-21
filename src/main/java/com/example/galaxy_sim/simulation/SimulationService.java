package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.web.SimulationSocketHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationService {

	private final Simulator simulator;
	private final SimulationSocketHandler socketHandler;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> simulationTask;
	private volatile boolean isRunning = false;

	private static final int TICK_RATE_HZ = 30;
	private static final int SUB_STEPS_PER_TICK = 10;

	@Autowired
	public SimulationService(Simulator simulator, SimulationSocketHandler socketHandler) {
		this.simulator = simulator;
		this.socketHandler = socketHandler;
	}

	public void start() {
		if (isRunning) return;
		isRunning = true;
		simulationTask = scheduler.scheduleAtFixedRate(this::tick, 0, 1000 / TICK_RATE_HZ, TimeUnit.MILLISECONDS);
	}

	public void pause() {
		isRunning = false;
		if (simulationTask != null) {
			simulationTask.cancel(false);
		}
		broadcastState();
	}

	public void reset() {
		pause();
		simulator.reset();
		broadcastState();
	}

	private void tick() {
		try {
			double totalDtForTick = simulator.getTimeScale() / TICK_RATE_HZ;
			double subStepDt = totalDtForTick / SUB_STEPS_PER_TICK;
			for (int i = 0; i < SUB_STEPS_PER_TICK; i++) {
				simulator.advanceSimulation(subStepDt);
			}
			broadcastState();
		} catch (Exception e) {
			e.printStackTrace();
			pause();
		}
	}

	private void broadcastState() {
		float[] rawData = simulator.getRawDataForVisualization();
		if (rawData == null || rawData.length == 0) return;

		int particleCount = rawData.length / 6;
		double currentEnergy = simulator.calculateTotalEnergy(rawData);
		double energyDrift = simulator.getEnergyDrift(currentEnergy);
		double totalVelocity = 0;
		double totalDistance = 0;

		List<Object> flatParticleData = new ArrayList<>(particleCount * 3);
		for (int i = 0; i < particleCount; i++) {
			int base = i * 6;
			float x = rawData[base];
			float y = rawData[base + 1];
			totalVelocity += Math.sqrt(rawData[base + 2] * rawData[base + 2] + rawData[base + 3] * rawData[base + 3]);
			totalDistance += Math.sqrt(x * x + y * y);
			flatParticleData.add(x);
			flatParticleData.add(y);
			flatParticleData.add(rawData[base + 5]);
		}

		Map<String, Object> state = new HashMap<>();
		state.put("particles", flatParticleData);
		state.put("particleCount", particleCount);
		state.put("currentTime", simulator.getCurrentTime());
		state.put("timeScale", simulator.getTimeScale());
		state.put("energy", currentEnergy);
		state.put("energyDrift", energyDrift);
		state.put("isRunning", isRunning);
		state.put("fastForward", simulator.isFastForward());
		state.put("avgVelocity", (particleCount > 0) ? totalVelocity / particleCount : 0.0);
		state.put("avgDistanceToCenter", (particleCount > 0) ? totalDistance / particleCount : 0.0);

		socketHandler.broadcast(state);
	}

	public void setParticleCount(int count) {
		pause();
		simulator.setParticleCount(count);
		broadcastState();
	}

	public boolean isRunning() { return isRunning; }

	@PreDestroy
	public void shutdown() {
		if (simulator != null) simulator.shutdown();
		scheduler.shutdownNow();
	}
}
