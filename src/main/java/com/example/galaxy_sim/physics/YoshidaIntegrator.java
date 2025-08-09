package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
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

    private final Quadtree quadtree;
    private final BackgroundPotential backgroundPotential;

    public YoshidaIntegrator(BackgroundPotential backgroundPotential) {
        this.backgroundPotential = backgroundPotential;
        this.quadtree = null; // Will be set per step
    }

    /**
     * Advances all particles by one time step using Yoshida 4th-order method
     */
    public List<Particle> step(List<Particle> particles, double dt) {
        List<Particle> result = new ArrayList<>(particles);

        // Build quadtree for current particle positions
        Quadtree currentQuadtree = buildQuadtree(result);

        // Step 1: x1 = x0 + c1*dt*v0, v1 = v0 + d1*dt*a(x1)
        result = updatePositions(result, C1 * dt);
        result = updateVelocities(result, currentQuadtree, D1 * dt);

        // Step 2: x2 = x1 + c2*dt*v1, v2 = v1 + d2*dt*a(x2)
        result = updatePositions(result, C2 * dt);
        currentQuadtree = buildQuadtree(result);
        result = updateVelocities(result, currentQuadtree, D2 * dt);

        // Step 3: x3 = x2 + c3*dt*v2, v3 = v2 + d3*dt*a(x3)
        result = updatePositions(result, C3 * dt);
        currentQuadtree = buildQuadtree(result);
        result = updateVelocities(result, currentQuadtree, D3 * dt);

        // Step 4: x4 = x3 + c4*dt*v3 (final position update)
        result = updatePositions(result, C4 * dt);

        return result;
    }

    /**
     * Updates particle positions: x_new = x_old + dt * v
     */
    private List<Particle> updatePositions(List<Particle> particles, double dt) {
        List<Particle> updated = new ArrayList<>();

        for (Particle p : particles) {
            double newX = p.x() + dt * p.vx();
            double newY = p.y() + dt * p.vy();
            updated.add(p.withPosition(newX, newY));
        }

        return updated;
    }

    /**
     * Updates particle velocities: v_new = v_old + dt * a
     */
    private List<Particle> updateVelocities(List<Particle> particles, Quadtree quadtree, double dt) {
        List<Particle> updated = new ArrayList<>();

        for (Particle p : particles) {
            // Calculate total acceleration (particle + background forces)
            Quadtree.Force particleForce = quadtree.calculateForce(p);
            Quadtree.Force backgroundForce = backgroundPotential.calculateBackgroundForce(p);

            Quadtree.Force totalForce = particleForce.add(backgroundForce);

            // F = ma, so a = F/m
            double ax = totalForce.fx() / p.mass();
            double ay = totalForce.fy() / p.mass();

            double newVx = p.vx() + dt * ax;
            double newVy = p.vy() + dt * ay;

            // Update distance to SMBH for Drake simulation features
            double distToSMBH = Math.sqrt(p.x() * p.x() + p.y() * p.y());

            updated.add(p.withVelocity(newVx, newVy).withDistanceToSMBH(distToSMBH));
        }

        return updated;
    }

    /**
     * Builds a new quadtree from current particle positions
     */
    private Quadtree buildQuadtree(List<Particle> particles) {
        // Create quadtree covering 2 kpc × 2 kpc region
        Quadtree tree = new Quadtree(0.0, 0.0, 1000.0); // ±1 kpc

        for (Particle p : particles) {
            tree.insert(p);
        }

        return tree;
    }

    /**
     * Calculates total kinetic energy of the system
     */
    public double calculateKineticEnergy(List<Particle> particles) {
        double totalKE = 0.0;

        for (Particle p : particles) {
            double v2 = p.vx() * p.vx() + p.vy() * p.vy();
            totalKE += 0.5 * p.mass() * v2;
        }

        return totalKE;
    }

    /**
     * Calculates total potential energy of the system
     */
    public double calculatePotentialEnergy(List<Particle> particles) {
        double totalPE = 0.0;

        // Build quadtree for potential energy calculation
        Quadtree tree = buildQuadtree(particles);

        for (Particle p : particles) {
            // Particle-particle potential energy
            Quadtree.Force particleForce = tree.calculateForce(p);
            double particlePE = -0.5 * (particleForce.fx() * p.x() + particleForce.fy() * p.y());

            // Background potential energy (approximate)
            Quadtree.Force backgroundForce = backgroundPotential.calculateBackgroundForce(p);
            double backgroundPE = -(backgroundForce.fx() * p.x() + backgroundForce.fy() * p.y());

            totalPE += particlePE + backgroundPE;
        }

        return totalPE;
    }

    /**
     * Calculates total energy (kinetic + potential)
     */
    public double calculateTotalEnergy(List<Particle> particles) {
        return calculateKineticEnergy(particles) + calculatePotentialEnergy(particles);
    }

    /**
     * Estimates maximum density for adaptive time-stepping
     */
    public double getMaxDensity(List<Particle> particles) {
        Quadtree tree = buildQuadtree(particles);
        return getMaxDensityRecursive(tree);
    }

    private double getMaxDensityRecursive(Quadtree tree) {
        // This is a simplified approach - in practice you'd traverse the tree
        // For now, return a conservative estimate
        return 1000.0; // solar masses per pc^2
    }
}
