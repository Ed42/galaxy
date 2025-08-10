package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.BackgroundPotential;
import com.example.galaxy_sim.physics.Quadtree;
import com.example.galaxy_sim.physics.YoshidaIntegrator;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Manages the state and progression of the galaxy simulation.
 */
@Component
public class Simulator {
	private volatile List<Particle> particles;
	private double currentTime = 0;
	private double initialEnergy = 0;
	private boolean fastForward = false;
	private int particleCount = 1000;
	private double timeScale = 100000.0;
	private final YoshidaIntegrator integrator;
	private final BackgroundPotential backgroundPotential;
	private boolean quadtreeOverlayEnabled = false;
	private int quadtreeMaxDepth = 8;
	private static final double G = 4.30091e-3;
	private static final double THETA = 0.7;
	private static final double SOFTENING = 15.0;

	public Simulator() {
		this.backgroundPotential = new BackgroundPotential();
		this.integrator = new YoshidaIntegrator(this.backgroundPotential, G, THETA, SOFTENING);
		reset();
	}

	public void reset() {
		this.particles = initializeParticles(this.particleCount);
		this.currentTime = 0;
		try { Thread.sleep(10); } catch (InterruptedException e) {}
		this.initialEnergy = this.integrator.calculateTotalEnergy(this.particles);
	}

	public void step(double dt) {
		double timeStepMyr = dt / 1_000_000.0;
		double currentTimeMyr = this.currentTime / 1_000_000.0;
		this.particles = integrator.step(this.particles, timeStepMyr, currentTimeMyr);
		this.currentTime += dt;
	}

	private List<Particle> initializeParticles(int count) {
		List<Particle> newParticles = new ArrayList<>();
		Random rand = new Random();
		double diskRadius = 15000.0, bulgeRadius = 3000.0;
		int bulgeCount = count / 3, diskCount = count - bulgeCount;

		for (int i = 0; i < bulgeCount; i++) {
			double r = bulgeRadius * Math.pow(rand.nextDouble(), 0.5);
			double theta = 2 * Math.PI * rand.nextDouble();
			double x = r * Math.cos(theta), y = r * Math.sin(theta);
			Quadtree.Force force = backgroundPotential.calculateAxisymmetricForce(new Particle(x, y, 0, 0, 1.2e6, Particle.StellarType.GIANT, 0, 0, false, r));
			double accel = force.magnitude() / 1.2e6, v_circ = Math.sqrt(accel * r);
			double vx = -v_circ * Math.sin(theta) + (rand.nextDouble() - 0.5) * 10.0;
			double vy = v_circ * Math.cos(theta) + (rand.nextDouble() - 0.5) * 10.0;
			newParticles.add(new Particle(x, y, vx, vy, 1.2e6, Particle.StellarType.GIANT, 5.0 + rand.nextDouble() * 5.0, 0.02, false, r));
		}
		for (int i = 0; i < diskCount; i++) {
			double r = bulgeRadius + (diskRadius - bulgeRadius) * Math.sqrt(rand.nextDouble());
			double theta = 2 * Math.PI * rand.nextDouble();
			double x = r * Math.cos(theta), y = r * Math.sin(theta);
			Quadtree.Force force = backgroundPotential.calculateAxisymmetricForce(new Particle(x, y, 0, 0, 1e6, Particle.StellarType.MAIN_SEQUENCE, 0, 0, false, r));
			double accel = force.magnitude() / 1e6, v_circ = Math.sqrt(accel * r);
			double vx = -v_circ * Math.sin(theta) + (rand.nextDouble() - 0.5) * 5.0;
			double vy = v_circ * Math.cos(theta) + (rand.nextDouble() - 0.5) * 5.0;
			newParticles.add(new Particle(x, y, vx, vy, 1e6, Particle.StellarType.MAIN_SEQUENCE, rand.nextDouble() * 5.0, 0.01, false, r));
		}
		return newParticles;
	}

	public double getEnergyDrift() {
		if (initialEnergy == 0) return 0;
		return (this.integrator.calculateTotalEnergy(this.particles) - initialEnergy) / initialEnergy;
	}
	public double getCurrentEnergy() { return this.integrator.calculateTotalEnergy(this.particles); }
	public List<Particle> getParticles() { return particles; }
	public double getCurrentTime() { return currentTime; }
	public double getTimeScale() { return fastForward ? timeScale * 5.0 : timeScale; }
	public void setTimeScale(double scale) { this.timeScale = Math.max(1.0, scale); }
	public int getParticleCount() { return (particles != null) ? particles.size() : 0; }
	public boolean isFastForward() { return fastForward; }
	public void setFastForward(boolean ff) { this.fastForward = ff; }
	public void setBarEnabled(boolean enabled) { this.backgroundPotential.setBarEnabled(enabled); }
	public void setParticleCount(int count) { this.particleCount = count; reset(); }
	public boolean isQuadtreeOverlayEnabled() { return quadtreeOverlayEnabled; }
	public void setQuadtreeOverlayEnabled(boolean enabled) { this.quadtreeOverlayEnabled = enabled; }
	public void setQuadtreeMaxDepth(int depth) { this.quadtreeMaxDepth = depth; }
	public void setNumberOfArms(int n) { this.backgroundPotential.setNumberOfArms(n); }

	public List<Quadtree.Bounds> getQuadtreeBounds() {
		Quadtree tree = integrator.getLastBuiltTree();
		if (tree != null) {
			final double viewRadius = 30000;
			return tree.getBounds(this.quadtreeMaxDepth).stream()
				.filter(b -> Math.abs(b.x()) < viewRadius && Math.abs(b.y()) < viewRadius)
				.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}
}
