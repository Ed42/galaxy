package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.BackgroundPotential;
import com.example.galaxy_sim.physics.CudaIntegrator;
import com.example.galaxy_sim.physics.Quadtree;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class Simulator {
	private double currentTime = 0;
	private double initialEnergy = 0;
	private boolean fastForward = false;
	private int particleCount = 10000;
	private double timeScale = 1.0e6; // Reduced for stability

	private final CudaIntegrator integrator;
	private final BackgroundPotential backgroundPotential; // Used for initialization only

	private boolean quadtreeOverlayEnabled = false;
	private int quadtreeMaxDepth = 8;

	public Simulator() {
		this.backgroundPotential = new BackgroundPotential();
		this.integrator = new CudaIntegrator();
		reset();
	}

	public void reset() {
		List<Particle> initialParticles = initializeParticles(this.particleCount);
		this.currentTime = 0;
		this.integrator.initialize(initialParticles);
		this.initialEnergy = calculateTotalEnergyFromList(initialParticles);
	}

	public void advanceSimulation(double dt) {
		integrator.step(dt / 1_000_000.0, this.currentTime / 1_000_000.0);
		this.currentTime += dt;
	}

	public float[] getRawDataForVisualization() {
		return integrator.getRawData();
	}

	public List<Quadtree.Bounds> getQuadtreeBounds(float[] rawData) {
		if (!this.quadtreeOverlayEnabled || rawData.length == 0) {
			return new ArrayList<>();
		}
		// Build the quadtree on the CPU from the latest data for visualization only
		List<Particle> particles = new ArrayList<>(rawData.length / 6);
		for (int i = 0; i < rawData.length / 6; i++) {
			int base = i * 6;
			particles.add(new Particle(rawData[base], rawData[base+1], 0,0,0, Particle.StellarType.MAIN_SEQUENCE,0,0,false,0));
		}

		double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
		for (Particle p : particles) {
			if (p.x() < minX) minX = p.x();
			if (p.x() > maxX) maxX = p.x();
			if (p.y() < minY) minY = p.y();
			if (p.y() > maxY) maxY = p.y();
		}
		double size = Math.max(maxX - minX, maxY - minY);
		double centerX = (minX + maxX) / 2.0;
		double centerY = (minY + maxY) / 2.0;
		Quadtree tree = new Quadtree(centerX, centerY, size * 1.2);
		for (Particle p : particles) {
			tree.insert(p);
		}
		return tree.getBounds(this.quadtreeMaxDepth);
	}

	public double calculateTotalEnergyFromList(List<Particle> particleList) {
		if (particleList == null || particleList.isEmpty()) return 0.0;
		return particleList.parallelStream()
			.mapToDouble(p -> 0.5 * p.mass() * (p.vx() * p.vx() + p.vy() * p.vy()))
			.sum();
	}

	public double calculateTotalEnergy(float[] rawData) {
		if (rawData == null || rawData.length == 0) return 0.0;
		double totalEnergy = 0;
		for (int i = 0; i < rawData.length; i += 6) {
			double vx = rawData[i + 2];
			double vy = rawData[i + 3];
			double mass = rawData[i + 4];
			totalEnergy += 0.5 * mass * (vx * vx + vy * vy);
		}
		return totalEnergy;
	}

	public double getEnergyDrift(double currentEnergy) {
		if (initialEnergy == 0) return 0.0;
		return (currentEnergy - initialEnergy) / initialEnergy;
	}

	public void shutdown() {
		integrator.shutdown();
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
			// Increased velocity dispersion for bulge particles for stability
			double vx = -v_circ * Math.sin(theta) + (rand.nextDouble() - 0.5) * 100.0;
			double vy = v_circ * Math.cos(theta) + (rand.nextDouble() - 0.5) * 100.0;
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

	public double getCurrentTime() { return currentTime; }
	public double getTimeScale() { return fastForward ? timeScale * 5.0 : timeScale; }
	public void setTimeScale(double scale) { this.timeScale = Math.max(1.0, scale); }
	public int getParticleCount() { return this.particleCount; }
	public boolean isFastForward() { return fastForward; }
	public void setFastForward(boolean ff) { this.fastForward = ff; }
	public void setBarEnabled(boolean enabled) { integrator.setBarEnabled(enabled); }
	public void setParticleCount(int count) { this.particleCount = count; reset(); }
	public void setNumberOfArms(int n) { /* No-op */ }
	public void setSmbhMass(double mass) { integrator.setSmbhMass(mass); }
	public void setQuadtreeOverlayEnabled(boolean enabled) { this.quadtreeOverlayEnabled = enabled; }
	public void setQuadtreeMaxDepth(int depth) { this.quadtreeMaxDepth = depth; }
}
