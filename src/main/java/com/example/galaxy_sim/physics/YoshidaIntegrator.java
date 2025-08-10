package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.Quadtree.Force;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * A 4th-order symplectic integrator that uses parallel streams
 * to accelerate force calculations on multi-core systems.
 */
public class YoshidaIntegrator {
	// Yoshida 4th-order coefficients
	private static final double W1 = 1.0 / (2.0 - Math.pow(2.0, 1.0/3.0));
	private static final double W0 = -Math.pow(2.0, 1.0/3.0) * W1;
	private static final double C1 = W1 / 2.0, C4 = C1;
	private static final double C2 = (W0 + W1) / 2.0, C3 = C2;
	private static final double D1 = W1, D3 = W1;
	private static final double D2 = W0;

	private static final double MYR_TO_SYSTEM_TIME = 1.0227;

	private final BackgroundPotential backgroundPotential;
	private final double G, THETA, SOFTENING;
	private Quadtree lastBuiltTree;

	public YoshidaIntegrator(BackgroundPotential backgroundPotential, double G, double theta, double softening) {
		this.backgroundPotential = backgroundPotential;
		this.G = G;
		this.THETA = theta;
		this.SOFTENING = softening;
	}

	public List<Particle> step(List<Particle> particles, double dt, double currentTimeMyr) {
		double dt_sys = dt * MYR_TO_SYSTEM_TIME;
		List<Particle> p_state = new ArrayList<>(particles);

		p_state = updatePositions(p_state, C1 * dt_sys);
		p_state = updateVelocities(p_state, D1 * dt_sys, currentTimeMyr + C1 * dt);
		p_state = updatePositions(p_state, C2 * dt_sys);
		p_state = updateVelocities(p_state, D2 * dt_sys, currentTimeMyr + C2 * dt);
		p_state = updatePositions(p_state, C3 * dt_sys);
		p_state = updateVelocities(p_state, D3 * dt_sys, currentTimeMyr + C3 * dt);
		p_state = updatePositions(p_state, C4 * dt_sys);

		return p_state;
	}

	private List<Particle> updatePositions(List<Particle> particles, double dt_sys) {
		return particles.parallelStream()
			.map(p -> new Particle(p.x() + p.vx() * dt_sys, p.y() + p.vy() * dt_sys, p.vx(), p.vy(), p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), p.distanceToSMBH()))
			.collect(Collectors.toList());
	}

	private List<Particle> updateVelocities(List<Particle> particles, double dt_sys, double timeForForceCalc) {
		this.lastBuiltTree = buildQuadtree(particles);
		return particles.parallelStream()
			.map(p -> {
				Force particleForce = lastBuiltTree.calculateForce(p, G, THETA, SOFTENING);
				Force backgroundForce = backgroundPotential.calculateBackgroundForce(p, timeForForceCalc);
				Force totalForce = particleForce.add(backgroundForce);

				double ax = totalForce.fx() / p.mass();
				double ay = totalForce.fy() / p.mass();

				double newVx = p.vx() + ax * dt_sys;
				double newVy = p.vy() + ay * dt_sys;

				double distToSMBH = Math.sqrt(p.x() * p.x() + p.y() * p.y());

				return new Particle(p.x(), p.y(), newVx, newVy, p.mass(), p.stellarType(), p.age(), p.metallicity(), p.habitable(), distToSMBH);
			})
			.collect(Collectors.toList());
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

	public Quadtree getLastBuiltTree() {
		return this.lastBuiltTree;
	}

	public double calculateTotalEnergy(List<Particle> particles) {
		if (particles == null || particles.isEmpty()) return 0.0;
		this.lastBuiltTree = buildQuadtree(particles);
		return particles.parallelStream()
			.mapToDouble(p -> {
				double kineticEnergy = 0.5 * p.mass() * (p.vx() * p.vx() + p.vy() * p.vy());
				double potentialEnergy = lastBuiltTree.calculatePotential(p, G, THETA, SOFTENING);
				return kineticEnergy + potentialEnergy;
			})
			.sum();
	}
}
