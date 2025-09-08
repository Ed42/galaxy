package com.example.galaxy_sim.model;

public record Particle(
    double x,
    double y,
    double mass,
    double vx,
    double vy,
    StellarType stellarType,
    double age,
    double metallicity,
    boolean habitable
) {
    public enum StellarType {
        MAIN_SEQUENCE,
        GIANT
    }
}
