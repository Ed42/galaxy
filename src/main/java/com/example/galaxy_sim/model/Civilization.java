package com.example.galaxy_sim.model;

/**
 * Placeholder class for Drake simulation features.
 * Represents civilizations that can emerge and interact in the galaxy simulation.
 *
 * This is designed for future extensibility to model civilization emergence
 * and interactions based on habitability probability calculations.
 */
public class Civilization {

    public enum CivilizationType {
        PRIMITIVE,      // Pre-technological
        INDUSTRIAL,     // Industrial revolution level
        SPACEFARING,    // Can travel within star system
        INTERSTELLAR,   // Can travel between stars
        GALACTIC        // Advanced galactic civilization
    }

    public enum CivilizationState {
        EMERGING,       // Just appeared
        STABLE,         // Established and growing
        EXPANDING,      // Actively expanding territory
        DECLINING,      // In decline
        EXTINCT         // No longer active
    }

    private final long id;
    private final double emergenceTime;     // Time when civilization emerged (years)
    private final double originX, originY;  // Origin coordinates (parsecs)
    private final Particle originStar;      // Star system where civilization originated

    private CivilizationType type;
    private CivilizationState state;
    private double communicationRange;      // Range in parsecs
    private double expansionRange;          // Current territorial range
    private double lastUpdateTime;          // Last update time for evolution

    // Population and development metrics
    private long population;
    private double technologyLevel;         // Arbitrary scale 0-1
    private double energyConsumption;       // Energy consumption rate

    public Civilization(long id, double emergenceTime, Particle originStar) {
        this.id = id;
        this.emergenceTime = emergenceTime;
        this.originStar = originStar;
        this.originX = originStar.x();
        this.originY = originStar.y();

        // Initialize as primitive civilization
        this.type = CivilizationType.PRIMITIVE;
        this.state = CivilizationState.EMERGING;
        this.communicationRange = 0.1; // 0.1 parsecs initially
        this.expansionRange = 0.01;    // Very small initial range
        this.lastUpdateTime = emergenceTime;

        this.population = 1000000L;     // 1 million initial population
        this.technologyLevel = 0.01;    // Very low initial tech level
        this.energyConsumption = 1e12;  // Watts (roughly current Earth)
    }

    /**
     * Updates civilization development over time
     */
    public void evolve(double currentTime, double deltaTime) {
        if (state == CivilizationState.EXTINCT) {
            return;
        }

        double timeSinceLastUpdate = currentTime - lastUpdateTime;
        if (timeSinceLastUpdate <= 0) {
            return;
        }

        // Simple evolution model - technology grows exponentially with random factors
        double growthRate = calculateGrowthRate();
        double newTechLevel = technologyLevel * (1 + growthRate * timeSinceLastUpdate / 1000.0);
        technologyLevel = Math.min(1.0, newTechLevel);

        // Update civilization type based on technology level
        updateCivilizationType();

        // Update communication and expansion ranges
        updateRanges();

        // Population growth (with limits)
        long maxPopulation = (long)(technologyLevel * 1e12); // Max pop scales with tech
        if (population < maxPopulation) {
            population *= (1 + 0.001 * timeSinceLastUpdate / 1000.0); // Slow growth
            population = Math.min(population, maxPopulation);
        }

        // Random extinction events (very rare)
        if (Math.random() < 0.0001 * timeSinceLastUpdate / 1000.0) {
            state = CivilizationState.EXTINCT;
        }

        lastUpdateTime = currentTime;
    }

    /**
     * Calculates growth rate based on environmental factors
     */
    private double calculateGrowthRate() {
        double baseGrowthRate = 0.01; // 1% per millennium

        // Metallicity affects technological development
        double metallicityBonus = originStar.metallicity() * 0.5;

        // Distance from SMBH affects stability (closer = more dangerous)
        double smbhDistance = originStar.distanceToSMBH();
        double stabilityFactor = Math.min(1.0, smbhDistance / 100.0); // Safer beyond 100pc

        // Stellar age affects resource availability
        double ageBonus = Math.max(0.1, 1.0 - originStar.age() / 10.0); // Younger stars better

        return baseGrowthRate * (1 + metallicityBonus) * stabilityFactor * ageBonus;
    }

    /**
     * Updates civilization type based on current technology level
     */
    private void updateCivilizationType() {
        if (technologyLevel < 0.1) {
            type = CivilizationType.PRIMITIVE;
        } else if (technologyLevel < 0.3) {
            type = CivilizationType.INDUSTRIAL;
        } else if (technologyLevel < 0.6) {
            type = CivilizationType.SPACEFARING;
        } else if (technologyLevel < 0.9) {
            type = CivilizationType.INTERSTELLAR;
        } else {
            type = CivilizationType.GALACTIC;
        }
    }

    /**
     * Updates communication and expansion ranges based on technology
     */
    private void updateRanges() {
        // Communication range grows with technology
        communicationRange = 0.1 + technologyLevel * 1000.0; // Up to ~1000 pc for advanced civs

        // Expansion range is smaller than communication range
        expansionRange = communicationRange * 0.1 * technologyLevel;

        // Limit expansion for non-interstellar civilizations
        if (type.ordinal() < CivilizationType.INTERSTELLAR.ordinal()) {
            expansionRange = Math.min(expansionRange, 1.0); // Max 1 parsec
        }
    }

    /**
     * Attempts to interact with another civilization
     */
    public InteractionResult interact(Civilization other) {
        if (this.state == CivilizationState.EXTINCT ||
            other.state == CivilizationState.EXTINCT) {
            return InteractionResult.NO_INTERACTION;
        }

        double distance = Math.sqrt(
            Math.pow(this.originX - other.originX, 2) +
            Math.pow(this.originY - other.originY, 2)
        );

        // Check if civilizations can communicate
        double maxCommRange = Math.max(this.communicationRange, other.communicationRange);
        if (distance > maxCommRange) {
            return InteractionResult.NO_CONTACT;
        }

        // Simple interaction model based on technology levels
        double techDiff = Math.abs(this.technologyLevel - other.technologyLevel);

        if (techDiff < 0.1) {
            return InteractionResult.PEACEFUL_EXCHANGE;
        } else if (techDiff < 0.5) {
            return InteractionResult.CAUTIOUS_CONTACT;
        } else {
            return InteractionResult.TECHNOLOGICAL_DISPARITY;
        }
    }

    public enum InteractionResult {
        NO_INTERACTION,         // Civilizations not active
        NO_CONTACT,             // Too far apart
        PEACEFUL_EXCHANGE,      // Similar tech levels, positive outcome
        CAUTIOUS_CONTACT,       // Some tech difference, neutral
        TECHNOLOGICAL_DISPARITY, // Large tech gap, potentially problematic
        CONFLICT,               // Hostile interaction
        MERGER                  // Civilizations merge
    }

    // Getters
    public long getId() { return id; }
    public double getEmergenceTime() { return emergenceTime; }
    public double getOriginX() { return originX; }
    public double getOriginY() { return originY; }
    public Particle getOriginStar() { return originStar; }
    public CivilizationType getType() { return type; }
    public CivilizationState getState() { return state; }
    public double getCommunicationRange() { return communicationRange; }
    public double getExpansionRange() { return expansionRange; }
    public long getPopulation() { return population; }
    public double getTechnologyLevel() { return technologyLevel; }
    public double getEnergyConsumption() { return energyConsumption; }

    @Override
    public String toString() {
        return String.format("Civilization[id=%d, type=%s, tech=%.3f, pop=%d, range=%.1f pc]",
            id, type, technologyLevel, population, communicationRange);
    }
}
