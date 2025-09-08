package com.galaxy.sim.physics;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/**
 * 4th-order symplectic integrator using Yoshida's method.
 * Preserves energy and phase space structure over long timescales.
 * Includes adaptive time-stepping based on local density.
 */
public class SymplecticIntegrator {
    
    // Yoshida 4th-order coefficients
    private static final double C1 = 1.351207191959657634;
    private static final double C2 = -1.702414383919315268;
    private static final double C3 = C1;  // Symmetric
    private static final double C4 = 0.0;
    
    private static final double D1 = 0.675603595979828817;
    private static final double D2 = -0.175603595979828817;
    private static final double D3 = -0.175603595979828817;
    private static final double D4 = 0.675603595979828817;
    
    private double currentTimestep;
    private double minTimestep = PhysicsConstants.MIN_TIMESTEP;
    private double maxTimestep = PhysicsConstants.MAX_TIMESTEP;
    private final double safetyFactor = PhysicsConstants.TIMESTEP_SAFETY;
    
    /**
     * Perform one integration step using 4th-order Yoshida method
     * 
     * @param particles List of particles to integrate
     * @param forceFunction Function that calculates force for each particle
     * @param dt Time step
     * @return Updated list of particles
     */
    public List<Particle> integrate(List<Particle> particles, 
                                   Function<Particle, Vector2D> forceFunction,
                                   double dt) {
        
        List<Particle> result = new ArrayList<>(particles);
        
        // Yoshida 4th-order symplectic integration
        // Step 1: Position update with C1 * dt
        result = updatePositions(result, C1 * dt);
        
        // Step 2: Velocity update with D1 * dt
        result = updateVelocities(result, forceFunction, D1 * dt);
        
        // Step 3: Position update with C2 * dt
        result = updatePositions(result, C2 * dt);
        
        // Step 4: Velocity update with D2 * dt
        result = updateVelocities(result, forceFunction, D2 * dt);
        
        // Step 5: Position update with C3 * dt
        result = updatePositions(result, C3 * dt);
        
        // Step 6: Velocity update with D3 * dt
        result = updateVelocities(result, forceFunction, D3 * dt);
        
        // Step 7: Position update with C4 * dt (C4 = 0, so skip)
        
        // Step 8: Velocity update with D4 * dt
        result = updateVelocities(result, forceFunction, D4 * dt);
        
        currentTimestep = dt;
        return result;
    }
    
    /**
     * Update particle positions: x(t+dt) = x(t) + v(t) * dt
     */
    private List<Particle> updatePositions(List<Particle> particles, double dt) {
        List<Particle> updated = new ArrayList<>(particles.size());
        
        for (Particle p : particles) {
            Vector2D newPosition = p.position().add(p.velocity().multiply(dt));
            updated.add(p.updateKinematics(newPosition, p.velocity()));
        }
        
        return updated;
    }
    
    /**
     * Update particle velocities: v(t+dt) = v(t) + a(t) * dt
     */
    private List<Particle> updateVelocities(List<Particle> particles,
                                           Function<Particle, Vector2D> forceFunction,
                                           double dt) {
        List<Particle> updated = new ArrayList<>(particles.size());
        
        for (Particle p : particles) {
            Vector2D force = forceFunction.apply(p);
            Vector2D acceleration = force.divide(p.mass());
            Vector2D newVelocity = p.velocity().add(acceleration.multiply(dt));
            updated.add(p.updateKinematics(p.position(), newVelocity));
        }
        
        return updated;
    }
    
    /**
     * Calculate adaptive timestep based on local density
     * Δt = min(0.01 × (Gρ)^(-1/2), maxTimestep)
     */
    public double calculateAdaptiveTimestep(Quadtree quadtree, List<Particle> particles) {
        double minDynamicalTime = maxTimestep;
        
        // Sample density at various positions
        for (Particle p : particles) {
            // Get local density within 50 pc
            double localDensity = quadtree.getLocalDensity(p.position(), 50.0);
            
            // Special handling near SMBH
            double distToCenter = p.position().magnitude();
            if (distToCenter < 10.0) {  // Within 10 pc of center
                // Use very small timestep near SMBH
                minDynamicalTime = Math.min(minDynamicalTime, minTimestep);
            } else if (localDensity > 0) {
                // Calculate dynamical time: t_dyn ≈ (Gρ)^(-1/2)
                double dynamicalTime = 1.0 / Math.sqrt(PhysicsConstants.G * localDensity);
                double safeTime = safetyFactor * dynamicalTime;
                minDynamicalTime = Math.min(minDynamicalTime, safeTime);
            }
        }
        
        // Clamp to allowed range
        return Math.max(minTimestep, Math.min(maxTimestep, minDynamicalTime));
    }
    
    /**
     * Calculate total energy (kinetic + potential) for energy conservation monitoring
     */
    public double calculateTotalEnergy(List<Particle> particles, 
                                      BackgroundPotential background,
                                      Quadtree quadtree) {
        double totalEnergy = 0;
        
        for (Particle p : particles) {
            // Kinetic energy
            totalEnergy += p.kineticEnergy();
            
            // Background potential energy
            totalEnergy += p.mass() * background.calculateTotalPotential(p.position());
            
            // Particle-particle potential energy (count each pair once)
            // This is simplified - full calculation would avoid double counting
            Vector2D force = quadtree.calculateForce(p);
            // Approximate potential from force (simplified)
            totalEnergy -= 0.5 * p.mass() * force.dot(p.position());
        }
        
        return totalEnergy;
    }
    
    /**
     * Simple Leapfrog integrator (2nd order, for comparison/testing)
     */
    public List<Particle> integrateLeapfrog(List<Particle> particles,
                                           Function<Particle, Vector2D> forceFunction,
                                           double dt) {
        // Half step velocity update
        List<Particle> halfStep = updateVelocities(particles, forceFunction, dt / 2);
        
        // Full step position update
        List<Particle> fullPosition = updatePositions(halfStep, dt);
        
        // Half step velocity update
        return updateVelocities(fullPosition, forceFunction, dt / 2);
    }
    
    /**
     * Get current timestep
     */
    public double getCurrentTimestep() {
        return currentTimestep;
    }
    
    /**
     * Set timestep bounds
     */
    public void setTimestepBounds(double min, double max) {
        this.minTimestep = min;
        this.maxTimestep = max;
    }
}
