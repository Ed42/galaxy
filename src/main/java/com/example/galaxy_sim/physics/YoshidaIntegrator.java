package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.Quadtree.Force; // <-- Import the nested Force record
import java.util.List;
import java.util.ArrayList;

/**
 * 4th-order symplectic integrator using Yoshida coefficients.
 * Provides excellent energy conservation for Hamiltonian systems.
 */
public class YoshidaIntegrator {
	// Yoshida 4th-order coefficients
	private static final double W1 = 1.0 / (2.0 - Math.pow(2.0, 1.0/3.0));
	private static final double W0 = -Math.pow(2.0, 1.0/3.0) * W1;
	private static final double C1 = W1 / 2.0;
	private static final double C2 = (W0 + W1) / 2.0;
	private static final double C3 = C2;
	private static final double C4 = C1;
	private static final double D1 = W1;
	private static final double D2 = W0;
	private static final double D3 = W1;

	private final BackgroundPotential backgroundPotential;
	// Store physical constants needed by the quadtree
	private final double G;
	private final double THETA;
	private final double SOFTENING;

	public YoshidaIntegrator(BackgroundPotential backgroundPotential, double G, double theta, double softening) {
		this.backgroundPotential = backgroundPotential;
		this.G = G;
		this.THETA = theta;
		this.SOFTENING = softening;
	}

	/**
	 * Advances all particles by one time step using Yoshida 4th-order method
	 */
	public List<Particle> step(List<Particle> particles, double dt) {
		// The integrator returns a new list representing the next state
		List<Particle> nextStateParticles = new ArrayList<>(particles);

		// Step 1: Update position by c1*dt, then update velocity by d1*dt
		nextStateParticles = updatePositions(nextStateParticles, C1 * dt);
		nextStateParticles = updateVelocities(nextStateParticles, D1 * dt);

		// Step 2: Update position by c2*dt, then update velocity by d2*dt
		nextStateParticles = updatePositions(nextStateParticles, C2 * dt);
		nextStateParticles = updateVelocities(nextStateParticles, D2 * dt);

		// Step 3: Update position by c3*dt, then update velocity by d3*dt
		nextStateParticles = updatePositions(nextStateParticles, C3 * dt);
		nextStateParticles = updateVelocities(nextStateParticles, D3 * dt);

		// Step 4: Final position update by c4*dt
		nextStateParticles = updatePositions(nextStateParticles, C4 * dt);

		return nextStateParticles;
	}

	private List<Particle> updatePositions(List<Particle> particles, double dt) {
		List<Particle> updated = new ArrayList<>();
		for (Particle p : particles) {
			double newX = p.x() + p.vx() * dt;
			double newY = p.y() + p.vy() * dt;
			// Use the full constructor to create the new immutable particle
			updated.add(new Particle(newX, newY, p.vx(), p.vy(), p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), p.distanceToSMBH()));
		}
		return updated;
	}

	private List<Particle> updateVelocities(List<Particle> particles, double dt) {
		List<Particle> updated = new ArrayList<>();
		// Build the quadtree for this velocity update substep
		Quadtree quadtree = buildQuadtree(particles);

		for (Particle p : particles) {
			// Calculate total force (particle-particle + background)
			Force particleForce = quadtree.calculateForce(p, G, THETA, SOFTENING);
			Force backgroundForce = backgroundPotential.calculateBackgroundForce(p);
			Force totalForce = particleForce.add(backgroundForce);

			// a = F/m
			double ax = totalForce.fx() / p.mass();
			double ay = totalForce.fy() / p.mass();

			double newVx = p.vx() + ax * dt;
			double newVy = p.vy() + ay * dt;

			// Re-calculate distance to center based on current position
			double distToSMBH = Math.sqrt(p.x() * p.x() + p.y() * p.y());

			// Use the full constructor
			updated.add(new Particle(p.x(), p.y(), newVx, newVy, p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), distToSMBH));
		}
		return updated;
	}

	private Quadtree buildQuadtree(List<Particle> particles) {
		// Dynamically calculate the bounding box containing all particles
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

		Quadtree tree = new Quadtree(centerX, centerY, size * 1.2); // Add a buffer
		for (Particle p : particles) {
			tree.insert(p);
		}
		return tree;
	}

	public double calculateTotalEnergy(List<Particle> particles) {
		double kineticEnergy = 0.0;
		double potentialEnergy = 0.0;

		Quadtree tree = buildQuadtree(particles);

		for (Particle p : particles) {
			// Kinetic Energy
			kineticEnergy += 0.5 * p.mass() * (p.vx() * p.vx() + p.vy() * p.vy());

			// Potential energy from particle-particle interactions
			potentialEnergy += tree.calculatePotential(p, G, THETA, SOFTENING);

			// Potential energy from background
			// NOTE: Assumes BackgroundPotential has a method to calculate potential.
			// potentialEnergy += backgroundPotential.calculatePotential(p);
		}

		// The particle-particle potential is double-counted, so divide by 2
		return kineticEnergy + (potentialEnergy / 2.0);
	}
}
