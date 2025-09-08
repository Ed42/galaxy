package com.galaxy.sim.core;

import com.galaxy.sim.physics.Particle;
import java.util.List;

/**
 * Immutable snapshot of the simulation state for visualization and monitoring.
 */
public record SimulationState(
    List<Particle> particles,
    double simulationTime,      // Current time in years
    double totalEnergy,          // Total system energy
    double energyDrift,          // Fractional energy drift from initial
    double currentTimestep,      // Current integration timestep
    double timeScale,            // Years per second
    boolean running,
    boolean paused,
    boolean fastForward,
    boolean barEnabled,
    List<double[]> quadtreeLines // Lines for quadtree visualization
) {
    
    /**
     * Get formatted time string (in appropriate units)
     */
    public String getFormattedTime() {
        if (simulationTime < 1000) {
            return String.format("%.1f years", simulationTime);
        } else if (simulationTime < 1e6) {
            return String.format("%.2f kyr", simulationTime / 1000);
        } else if (simulationTime < 1e9) {
            return String.format("%.2f Myr", simulationTime / 1e6);
        } else {
            return String.format("%.2f Gyr", simulationTime / 1e9);
        }
    }
    
    /**
     * Get formatted time scale string
     */
    public String getFormattedTimeScale() {
        if (timeScale < 1000) {
            return String.format("%.0f yr/s", timeScale);
        } else if (timeScale < 1e6) {
            return String.format("%.1f kyr/s", timeScale / 1000);
        } else if (timeScale < 1e9) {
            return String.format("%.1f Myr/s", timeScale / 1e6);
        } else {
            return String.format("%.1f Gyr/s", timeScale / 1e9);
        }
    }
    
    /**
     * Get energy drift as percentage
     */
    public double getEnergyDriftPercent() {
        return energyDrift * 100;
    }
    
    /**
     * Count particles by type
     */
    public ParticleStats getParticleStats() {
        int diskStars = 0;
        int bulgeStars = 0;
        int smbh = 0;
        int habitable = 0;
        
        for (Particle p : particles) {
            switch (p.type()) {
                case DISK_STAR -> diskStars++;
                case BULGE_STAR -> bulgeStars++;
                case SMBH -> smbh++;
            }
            if (p.habitable()) {
                habitable++;
            }
        }
        
        return new ParticleStats(diskStars, bulgeStars, smbh, habitable, particles.size());
    }
    
    /**
     * Statistics about particle types
     */
    public record ParticleStats(
        int diskStars,
        int bulgeStars,
        int smbh,
        int habitableRegions,
        int total
    ) {}
}
