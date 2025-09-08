package com.galaxy.sim.physics;

/**
 * Represents a stellar particle in the galaxy simulation.
 * Each particle represents approximately 10^6 solar masses of stars.
 */
public record Particle(
    long id,                    // Unique identifier
    Vector2D position,          // Position in parsecs
    Vector2D velocity,          // Velocity in km/s
    double mass,                // Mass in solar masses
    ParticleType type,          // Disk or bulge star
    double age,                 // Age in Gyr (gigayears)
    double metallicity,         // Metallicity relative to solar (1.0 = solar)
    boolean habitable,          // Whether this region could support life
    double distanceToSMBH       // Distance to supermassive black hole in parsecs
) {
    
    /**
     * Particle types
     */
    public enum ParticleType {
        DISK_STAR("Disk", 0.0, 0.5, 1.0),      // Blue color
        BULGE_STAR("Bulge", 1.0, 1.0, 0.0),    // Yellow color
        SMBH("SMBH", 1.0, 0.0, 0.0);           // Red color
        
        private final String name;
        private final double r, g, b;  // RGB color components
        
        ParticleType(String name, double r, double g, double b) {
            this.name = name;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public String getName() { return name; }
        public double getR() { return r; }
        public double getG() { return g; }
        public double getB() { return b; }
    }
    
    /**
     * Create a new particle with updated position and velocity
     */
    public Particle updateKinematics(Vector2D newPosition, Vector2D newVelocity) {
        double newDistToSMBH = newPosition.magnitude();
        return new Particle(
            id, newPosition, newVelocity, mass, type, 
            age, metallicity, habitable, newDistToSMBH
        );
    }
    
    /**
     * Create a new particle with updated age
     */
    public Particle updateAge(double deltaTime) {
        double newAge = age + deltaTime / 1e9;  // Convert years to Gyr
        return new Particle(
            id, position, velocity, mass, type,
            newAge, metallicity, habitable, distanceToSMBH
        );
    }
    
    /**
     * Check if particle should be habitable based on metallicity and SMBH distance
     */
    public Particle updateHabitability() {
        // Habitability probability based on metallicity and distance from SMBH
        // P_life ∝ metallicity × exp(-r/r_SMBH), r_SMBH ≈ 100 pc
        boolean newHabitable = false;
        if (metallicity >= PhysicsConstants.MIN_METALLICITY_FOR_LIFE) {
            double habitabilityFactor = metallicity * 
                Math.exp(-distanceToSMBH / PhysicsConstants.HABITABLE_ZONE_RADIUS);
            // Simple threshold for now
            newHabitable = habitabilityFactor > 0.5;
        }
        
        return new Particle(
            id, position, velocity, mass, type,
            age, metallicity, newHabitable, distanceToSMBH
        );
    }
    
    /**
     * Calculate kinetic energy (0.5 * m * v^2)
     */
    public double kineticEnergy() {
        return 0.5 * mass * velocity.magnitudeSquared();
    }
    
    /**
     * Get color components for visualization (0-255 scale)
     */
    public int[] getRGBColor() {
        // Adjust brightness based on density (this will be enhanced later)
        double brightness = 1.0;
        return new int[] {
            (int)(type.getR() * brightness * 255),
            (int)(type.getG() * brightness * 255),
            (int)(type.getB() * brightness * 255)
        };
    }
    
    /**
     * Factory method to create a disk star particle
     */
    public static Particle createDiskStar(long id, Vector2D position, Vector2D velocity,
                                          double mass, double age, double metallicity) {
        double distToSMBH = position.magnitude();
        return new Particle(id, position, velocity, mass, ParticleType.DISK_STAR,
                           age, metallicity, false, distToSMBH);
    }
    
    /**
     * Factory method to create a bulge star particle
     */
    public static Particle createBulgeStar(long id, Vector2D position, Vector2D velocity,
                                           double mass, double age, double metallicity) {
        double distToSMBH = position.magnitude();
        return new Particle(id, position, velocity, mass, ParticleType.BULGE_STAR,
                           age, metallicity, false, distToSMBH);
    }
    
    /**
     * Factory method to create the SMBH particle
     */
    public static Particle createSMBH(long id) {
        return new Particle(id, Vector2D.ZERO, Vector2D.ZERO, 
                           PhysicsConstants.M_SMBH, ParticleType.SMBH,
                           10.0, 0.0, false, 0.0);
    }
}
