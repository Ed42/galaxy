package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
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
	private long tickCount = 0;
	private final Map<String, Object> cachedState = new HashMap<>();

	// The simulation will be ticked at this rate in the real world.
	private static final int TICK_RATE_HZ = 30;
	private static final long NANOS_PER_SECOND = 1_000_000_000;
	private static final long TICK_INTERVAL_NANOS = NANOS_PER_SECOND / TICK_RATE_HZ;

	@Autowired
	public SimulationService(Simulator simulator, SimulationSocketHandler socketHandler) {
		this.simulator = simulator;
		this.socketHandler = socketHandler;
	}

	public void start() {
		if (isRunning) {
			return;
		}
		isRunning = true;
		// Schedule the simulation task to run at a fixed rate.
		simulationTask = scheduler.scheduleAtFixedRate(this::tick, 0, TICK_INTERVAL_NANOS, TimeUnit.NANOSECONDS);
	}

	public void pause() {
		isRunning = false;
		if (simulationTask != null) {
			simulationTask.cancel(false);
		}
		// Send a final state update on pause, ensuring the UI reflects the "paused" state.
		broadcastState();
	}

	public void reset() {
		pause(); // Ensure the simulation is stopped before resetting.
		simulator.reset();
		cachedState.clear();
		tickCount = 0;
		broadcastState(); // Broadcast the fresh state.
	}

	public void setParticleCount(int count) {
		pause();
		simulator.setParticleCount(count);
		broadcastState();
	}

	public boolean isRunning() {
		return isRunning;
	}

	private void tick() {
		try {
			// Calculate the amount of simulation time to advance in this single tick.
			// timeScale is in sim years per real second.
			// We divide by TICK_RATE_HZ to get sim years per tick.
			double simulationDt = simulator.getTimeScale() / TICK_RATE_HZ;
			simulator.step(simulationDt);
			broadcastState();
			tickCount++;
		} catch (Exception e) {
			// If anything goes wrong, log it and stop the simulation to prevent further errors.
			e.printStackTrace();
			pause();
		}
	}

	private void broadcastState() {
		Map<String, Object> state = new HashMap<>();
		List<Particle> particles = simulator.getParticles();
		if (particles == null) {
			particles = new ArrayList<>();
		}

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
		state.put("isRunning", isRunning); // Use the service's own state
		state.put("fastForward", simulator.isFastForward());

		// Update expensive calculations periodically and cache them
		if (tickCount % 10 == 0 || cachedState.isEmpty()) {
			double currentEnergy = simulator.getCurrentEnergy();
			double initialEnergy = simulator.getInitialEnergy();
			double energyDrift = (initialEnergy == 0) ? 0.0 : (currentEnergy - initialEnergy) / initialEnergy;
			cachedState.put("energy", currentEnergy);
			cachedState.put("energyDrift", energyDrift);

			double avgVelocity = particles.stream().mapToDouble(p -> Math.sqrt(p.vx() * p.vx() + p.vy() * p.vy())).average().orElse(0.0);
			double avgDistanceToCenter = particles.stream().mapToDouble(Particle::distanceToSMBH).average().orElse(0.0);
			cachedState.put("avgVelocity", avgVelocity);
			cachedState.put("avgDistanceToCenter", avgDistanceToCenter);
		}
		state.putAll(cachedState);

		if (simulator.isQuadtreeOverlayEnabled()) {
			state.put("quadtreeBounds", simulator.getQuadtreeBounds());
		}

		socketHandler.broadcast(state);
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}
}
