package com.galaxy.sim.drake;

import com.galaxy.sim.physics.Vector2D;
import com.galaxy.sim.physics.Particle;

/**
 * Placeholder class for Drake equation simulation of civilizations.
 * Represents an emergent civilization in the galaxy simulation.
 * 
 * This class is designed for future extensibility to model:
 * - Civilization emergence based on habitability
 * - Expansion and colonization
 * - Inter-civilization interactions
 * - Technology levels and detection ranges
 */
public class Civilization {
    
    /**
     * Civilization types based on Kardashev scale
     */
    public enum CivilizationType {
        TYPE_0("Pre-spacefaring", 0.5, 10),      // Pre-Type I
        TYPE_I("Planetary", 1.0, 100),           // Harnesses planetary energy
        TYPE_II("Stellar", 2.0, 1000),           // Harnesses stellar energy
        TYPE_III("Galactic", 3.0, 10000);        // Harnesses galactic energy
        
        private final String description;
        private final double kardashevLevel;
        private final double detectionRange;  // in parsecs
        
        CivilizationType(String description, double kardashevLevel, double detectionRange) {
            this.description = description;
            this.kardashevLevel = kardashevLevel;
            this.detectionRange = detectionRange;
        }
        
        public String getDescription() { return description; }
        public double getKardashevLevel() { return kardashevLevel; }
        public double getDetectionRange() { return detectionRange; }
    }
    
    private final long id;
    private final Vector2D origin;           // Origin location
    private Vector2D currentLocation;        // Current center of civilization
    private double emergenceTime;            // Time when civilization emerged
    private double age;                      // Current age of civilization
    private CivilizationType type;          // Technology level
    private double expansionRadius;          // Current sphere of influence
    private boolean alive;                   // Whether civilization still exists
    private Particle hostParticle;          // Host stellar system
    
    /**
     * Create a new civilization
     */
    public Civilization(long id, Particle hostParticle, double emergenceTime) {
        this.id = id;
        this.hostParticle = hostParticle;
        this.origin = hostParticle.position();
        this.currentLocation = origin;
        this.emergenceTime = emergenceTime;
        this.age = 0;
        this.type = CivilizationType.TYPE_0;
        this.expansionRadius = 1.0;  // Start with 1 pc
        this.alive = true;
    }
    
    /**
     * Update civilization state
     */
    public void update(double deltaTime, double currentTime) {
        if (!alive) return;
        
        age = currentTime - emergenceTime;
        
        // Evolve technology level based on age
        if (age > 1e6 && type == CivilizationType.TYPE_0) {
            evolve(CivilizationType.TYPE_I);
        } else if (age > 1e7 && type == CivilizationType.TYPE_I) {
            evolve(CivilizationType.TYPE_II);
        } else if (age > 1e8 && type == CivilizationType.TYPE_II) {
            evolve(CivilizationType.TYPE_III);
        }
        
        // Expand sphere of influence
        expand(deltaTime);
    }
    
    /**
     * Evolve to a new technology level
     */
    private void evolve(CivilizationType newType) {
        this.type = newType;
        // Could trigger events, change expansion rate, etc.
    }
    
    /**
     * Expand civilization's sphere of influence
     */
    private void expand(double deltaTime) {
        // Simple linear expansion for now
        // Could be made more sophisticated based on type, resources, etc.
        double expansionRate = switch (type) {
            case TYPE_0 -> 0.001;   // 0.001 pc/year
            case TYPE_I -> 0.01;    // 0.01 pc/year
            case TYPE_II -> 0.1;    // 0.1 pc/year
            case TYPE_III -> 1.0;   // 1 pc/year
        };
        
        expansionRadius += expansionRate * deltaTime;
    }
    
    /**
     * Check if this civilization can detect another
     */
    public boolean canDetect(Civilization other) {
        if (!alive || !other.alive) return false;
        
        double distance = currentLocation.distanceTo(other.currentLocation);
        return distance <= type.getDetectionRange();
    }
    
    /**
     * Interact with another civilization
     */
    public void interact(Civilization other) {
        // Placeholder for future interaction logic
        // Could include: trade, conflict, knowledge exchange, merger
    }
    
    /**
     * Calculate probability of civilization emergence
     * Based on Drake equation factors
     */
    public static double calculateEmergenceProbability(Particle particle, double currentTime) {
        // N = R* × fp × ne × fl × fi × fc × L
        // Simplified version based on particle properties
        
        double probability = 0;
        
        // Check basic habitability
        if (!particle.habitable()) return 0;
        
        // Factor in metallicity (proxy for planet formation)
        double fp = Math.min(1.0, particle.metallicity());
        
        // Factor in age (need time for evolution)
        double minAge = 1e9;  // 1 Gyr minimum
        double fl = particle.age() > minAge ? 1.0 : particle.age() / minAge;
        
        // Factor in distance from SMBH (radiation environment)
        double idealDistance = 500;  // 500 pc from center
        double distanceFactor = Math.exp(-Math.pow(particle.distanceToSMBH() - idealDistance, 2) / (2 * 200 * 200));
        
        // Combine factors
        probability = fp * fl * distanceFactor * 1e-6;  // Very small base probability
        
        return probability;
    }
    
    // Getters
    public long getId() { return id; }
    public Vector2D getOrigin() { return origin; }
    public Vector2D getCurrentLocation() { return currentLocation; }
    public double getEmergenceTime() { return emergenceTime; }
    public double getAge() { return age; }
    public CivilizationType getType() { return type; }
    public double getExpansionRadius() { return expansionRadius; }
    public boolean isAlive() { return alive; }
    public Particle getHostParticle() { return hostParticle; }
    
    // Setters
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setCurrentLocation(Vector2D location) { this.currentLocation = location; }
}
