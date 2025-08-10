package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.Quadtree.Force;
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

	// The units of G (pc, km/s, M_solar) imply a natural time unit of ~0.978 Myr.
	// This factor converts dt from Myr into the simulation's internal time unit.
	private static final double MYR_TO_SYSTEM_TIME = 1.0227;

	private final BackgroundPotential backgroundPotential;
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
	 * Advances all particles by one time step using Yoshida 4th-order method.
	 * @param dt The time step in Mega-years (Myr).
	 */
	public List<Particle> step(List<Particle> particles, double dt) {
		// Convert dt from Myr into the consistent system time unit
		double dt_sys = dt * MYR_TO_SYSTEM_TIME;

		List<Particle> nextStateParticles = new ArrayList<>(particles);

		// Step 1: Update position by c1*dt, then update velocity by d1*dt
		nextStateParticles = updatePositions(nextStateParticles, C1 * dt_sys);
		nextStateParticles = updateVelocities(nextStateParticles, D1 * dt_sys);

		// Step 2: Update position by c2*dt, then update velocity by d2*dt
		nextStateParticles = updatePositions(nextStateParticles, C2 * dt_sys);
		nextStateParticles = updateVelocities(nextStateParticles, D2 * dt_sys);

		// Step 3: Update position by c3*dt, then update velocity by d3*dt
		nextStateParticles = updatePositions(nextStateParticles, C3 * dt_sys);
		nextStateParticles = updateVelocities(nextStateParticles, D3 * dt_sys);

		// Step 4: Final position update by c4*dt
		nextStateParticles = updatePositions(nextStateParticles, C4 * dt_sys);

		return nextStateParticles;
	}

	private List<Particle> updatePositions(List<Particle> particles, double dt_sys) {
		List<Particle> updated = new ArrayList<>();
		for (Particle p : particles) {
			double newX = p.x() + p.vx() * dt_sys;
			double newY = p.y() + p.vy() * dt_sys;
			updated.add(new Particle(newX, newY, p.vx(), p.vy(), p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), p.distanceToSMBH()));
		}
		return updated;
	}

	private List<Particle> updateVelocities(List<Particle> particles, double dt_sys) {
		List<Particle> updated = new ArrayList<>();
		Quadtree quadtree = buildQuadtree(particles);

		for (Particle p : particles) {
			Force particleForce = quadtree.calculateForce(p, G, THETA, SOFTENING);
			Force backgroundForce = backgroundPotential.calculateBackgroundForce(p);
			Force totalForce = particleForce.add(backgroundForce);

			double ax = totalForce.fx() / p.mass();
			double ay = totalForce.fy() / p.mass();

			double newVx = p.vx() + ax * dt_sys;
			double newVy = p.vy() + ay * dt_sys;

			double distToSMBH = Math.sqrt(p.x() * p.x() + p.y() * p.y());

			updated.add(new Particle(p.x(), p.y(), newVx, newVy, p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), distToSMBH));
		}
		return updated;
	}

	private Quadtree buildQuadtree(List<Particle> particles) {
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
		return tree;
	}

	public double calculateTotalEnergy(List<Particle> particles) {
		double kineticEnergy = 0.0;
		double potentialEnergy = 0.0;

		Quadtree tree = buildQuadtree(particles);

		for (Particle p : particles) {
			kineticEnergy += 0.5 * p.mass() * (p.vx() * p.vx() + p.vy() * p.vy());
			potentialEnergy += tree.calculatePotential(p, G, THETA, SOFTENING);
		}
		return kineticEnergy + (potentialEnergy / 2.0);
	}
}
