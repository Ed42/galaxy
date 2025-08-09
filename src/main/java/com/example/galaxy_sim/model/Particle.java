package com.example.galaxy_sim.model;

/**
 * Represents a particle in the galaxy simulation with position, velocity, mass,
 * and stellar properties. Designed for extensibility with Drake simulation features.
 *
 * Each particle represents approximately 10^6 solar masses of stellar material.
 */
public record Particle(
    double x, double y,           // Position in parsecs
    double vx, double vy,         // Velocity in km/s
    double mass,                  // Mass in solar masses (~10^6)
    StellarType stellarType,      // Main sequence, giant, etc.
    double age,                   // Age in Gyr
    double metallicity,           // Metallicity [Fe/H]
    boolean habitable,            // Habitability flag for Drake simulation
    double distanceToSMBH         // Distance to SMBH in parsecs
) {

    public enum StellarType {
        MAIN_SEQUENCE,    // Disk population
        GIANT,            // Bulge population
        SUPERGIANT,       // Rare massive stars
        WHITE_DWARF       // End-stage stars
    }

    /**
     * Creates a new particle with updated position and velocity
     */
    public Particle withPosition(double newX, double newY) {
        return new Particle(newX, newY, vx, vy, mass, stellarType, age, metallicity, habitable, distanceToSMBH);
    }

    /**
     * Creates a new particle with updated velocity
     */
    public Particle withVelocity(double newVx, double newVy) {
        return new Particle(x, y, newVx, newVy, mass, stellarType, age, metallicity, habitable, distanceToSMBH);
    }

    /**
     * Creates a new particle with updated distance to SMBH
     */
    public Particle withDistanceToSMBH(double newDistance) {
        return new Particle(x, y, vx, vy, mass, stellarType, age, metallicity, habitable, newDistance);
    }

    /**
     * Calculates habitability probability based on metallicity and SMBH proximity
     * P_life ∝ metallicity × exp(-r/r_SMBH), r_SMBH ≈ 100 pc
     */
    public double habitabilityProbability() {
        final double r_SMBH = 100.0; // parsecs
        return metallicity * Math.exp(-distanceToSMBH / r_SMBH);
    }

    /**
     * Returns the gravitational softening parameter for this particle
     */
    public double softeningParameter() {
        return 10.0; // ε = 10 pc for particle-particle interactions
    }
}
