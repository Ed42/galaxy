package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.BackgroundPotential;
import com.example.galaxy_sim.physics.Quadtree;
import com.example.galaxy_sim.physics.YoshidaIntegrator;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages the state and progression of the galaxy simulation.
 * This class orchestrates the physics integration and particle management.
 */
@Component
public class Simulator {
	private volatile List<Particle> particles;
	private double currentTime = 0;
	private double initialEnergy = 0;
	private boolean barEnabled = false;
	private boolean fastForward = false;
	private int particleCount = 1000;
	private double timeScale = 100000.0;

	// Physics Subsystems
	private final YoshidaIntegrator integrator;
	private final BackgroundPotential backgroundPotential;

	// Physical Constants
	private static final double G = 4.30091e-3; // pc*(km/s)^2 / M_solar
	private static final double THETA = 0.7;
	private static final double SOFTENING = 15.0; // pc

	public Simulator() {
		// Initialize the physics subsystems
		this.backgroundPotential = new BackgroundPotential();
		this.integrator = new YoshidaIntegrator(this.backgroundPotential, G, THETA, SOFTENING);
		reset();
	}

	/**
	 * Resets the simulation to its initial state.
	 */
	public void reset() {
		this.particles = initializeParticles(this.particleCount);
		this.currentTime = 0;
		// A short delay to allow the tree to be built before calculating energy
		try { Thread.sleep(10); } catch (InterruptedException e) {}
		this.initialEnergy = this.integrator.calculateTotalEnergy(this.particles);
	}

	/**
	 * Advances the simulation by a given time step (dt) in years.
	 */
	public void step(double dt) {
		// The simulation time step in internal units (millions of years)
		double timeStepMyr = dt / 1_000_000.0;

		// Delegate the entire particle state update to the integrator.
		this.particles = integrator.step(this.particles, timeStepMyr);

		// Advance simulation time
		this.currentTime += dt;
	}

	/**
	 * Initializes particles with positions and stable circular velocities.
	 */
	private List<Particle> initializeParticles(int count) {
		List<Particle> newParticles = new ArrayList<>();
		Random rand = new Random();

		double diskRadius = 15000.0;
		double bulgeRadius = 3000.0;

		// Increase the proportion of particles in the bulge for a more prominent core.
		int bulgeCount = count / 3;
		int diskCount = count - bulgeCount;

		// Initialize bulge particles, concentrated in the center
		for (int i = 0; i < bulgeCount; i++) {
			double r = bulgeRadius * Math.pow(rand.nextDouble(), 0.5); // Concentrate towards center
			double theta = 2 * Math.PI * rand.nextDouble();
			double x = r * Math.cos(theta);
			double y = r * Math.sin(theta);

			Quadtree.Force force = backgroundPotential.calculateAxisymmetricForce(new Particle(x, y, 0, 0, 1.2e6, Particle.StellarType.GIANT, 0, 0, false, r));
			double acceleration_mag = force.magnitude() / 1.2e6;
			double v_circ = Math.sqrt(acceleration_mag * r);

			double vx = -v_circ * Math.sin(theta);
			double vy = v_circ * Math.cos(theta);

			vx += (rand.nextDouble() - 0.5) * 10.0; // Higher velocity dispersion in bulge
			vy += (rand.nextDouble() - 0.5) * 10.0;

			double age = 5.0 + rand.nextDouble() * 5.0;
			double metallicity = 0.02;

			newParticles.add(new Particle(x, y, vx, vy, 1.2e6, Particle.StellarType.GIANT, age, metallicity, false, r));
		}

		// Initialize disk particles, outside the bulge
		for (int i = 0; i < diskCount; i++) {
			double r = bulgeRadius + (diskRadius - bulgeRadius) * Math.sqrt(rand.nextDouble()); // Start from bulge edge

			double theta = 2 * Math.PI * rand.nextDouble();
			double x = r * Math.cos(theta);
			double y = r * Math.sin(theta);

			Quadtree.Force force = backgroundPotential.calculateAxisymmetricForce(new Particle(x, y, 0, 0, 1e6, Particle.StellarType.MAIN_SEQUENCE, 0, 0, false, r));
			double acceleration_mag = force.magnitude() / 1e6;
			double v_circ = Math.sqrt(acceleration_mag * r);

			double vx = -v_circ * Math.sin(theta);
			double vy = v_circ * Math.cos(theta);

			vx += (rand.nextDouble() - 0.5) * 5.0; // Lower velocity dispersion in disk
			vy += (rand.nextDouble() - 0.5) * 5.0;

			double age = rand.nextDouble() * 5.0;
			double metallicity = 0.01;

			newParticles.add(new Particle(x, y, vx, vy, 1e6, Particle.StellarType.MAIN_SEQUENCE, age, metallicity, false, r));
		}

		return newParticles;
	}

	public double getEnergyDrift() {
		if (initialEnergy == 0) return 0;
		double currentEnergy = this.integrator.calculateTotalEnergy(this.particles);
		return (currentEnergy - initialEnergy) / initialEnergy;
	}

	public double getCurrentEnergy() {
		if (this.particles == null || this.particles.isEmpty()) return 0.0;
		return this.integrator.calculateTotalEnergy(this.particles);
	}

	// --- Getters and Setters ---
	public List<Particle> getParticles() { return particles; }
	public double getCurrentTime() { return currentTime; }
	public double getTimeScale() { return fastForward ? timeScale * 5.0 : timeScale; }
	public void setTimeScale(double scale) { this.timeScale = Math.max(1.0, scale); }
	public int getParticleCount() { return (particles != null) ? particles.size() : 0; }
	public boolean isFastForward() { return fastForward; }
	public void setFastForward(boolean ff) { this.fastForward = ff; }
	public void setBarEnabled(boolean enabled) { this.backgroundPotential.setBarEnabled(enabled); }

	public void setParticleCount(int count) {
		this.particleCount = count;
		reset();
	}
}
