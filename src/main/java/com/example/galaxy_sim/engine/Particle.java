package com.example.galaxy_sim.engine;

public final class Particle {
    // Phase space
    public double x; // pc
    public double y; // pc
    public double vx; // pc/Myr
    public double vy; // pc/Myr
    public double ax; // pc/Myr^2 (computed)
    public double ay; // pc/Myr^2 (computed)

    // Mass
    public double mass; // M_sun

    // Drake/extensibility attributes
    public StarType type;
    public double ageGyr;       // Gyr
    public double metallicity;  // 0..1
    public boolean habitable;   // placeholder

    // Derived
    public double distToSMBH; // pc (updated each step)

    public Particle(double x, double y, double vx, double vy, double mass, StarType type, double ageGyr, double metallicity) {
        this.x = x; this.y = y;
        this.vx = vx; this.vy = vy;
        this.mass = mass;
        this.type = type;
        this.ageGyr = ageGyr;
        this.metallicity = metallicity;
        this.habitable = false;
    }

    public void resetAcc() { this.ax = 0; this.ay = 0; }
}

