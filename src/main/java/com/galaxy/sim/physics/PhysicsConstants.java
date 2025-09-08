package com.galaxy.sim.physics;

/**
 * Physical constants for the galaxy simulation.
 * Units: parsec (pc), solar mass (M_sun), kilometer per second (km/s), year
 */
public class PhysicsConstants {
    
    // Gravitational constant in pc (M_sun)^(-1) (km/s)^2
    public static final double G = 4.302e-3;
    
    // Softening parameters in parsecs
    public static final double EPSILON_PARTICLE = 10.0;  // Particle-particle softening
    public static final double EPSILON_SMBH = 1.0;       // SMBH softening
    
    // Simulation region bounds in parsecs
    public static final double REGION_SIZE = 2000.0;  // Total size: 2 kpc x 2 kpc
    public static final double REGION_HALF_SIZE = REGION_SIZE / 2.0;  // ±1 kpc
    
    // Mass parameters in solar masses
    public static final double M_SMBH = 4e6;        // Supermassive black hole mass
    public static final double M_DISK = 1e11;       // Total disk mass
    public static final double M_BULGE = 1e10;      // Total bulge mass
    public static final double M_PARTICLE = 1e6;    // Mass per particle
    
    // Disk parameters
    public static final double DISK_SCALE_LENGTH = 1000.0;  // Rd = 1 kpc
    public static final double DISK_A = 5000.0;             // Miyamoto-Nagai a parameter (5 kpc)
    public static final double DISK_B = 300.0;              // Miyamoto-Nagai b parameter (0.3 kpc)
    
    // Bulge parameters
    public static final double BULGE_A = 1000.0;  // Hernquist scale length (1 kpc)
    
    // Halo parameters
    public static final double HALO_V_C = 200.0;  // Circular velocity (km/s)
    
    // Spiral arm parameters
    public static final double SPIRAL_AMPLITUDE = 0.1;     // 10% of disk potential
    public static final double SPIRAL_PITCH_ANGLE = 15.0;  // degrees
    public static final double SPIRAL_K = Math.tan(Math.toRadians(SPIRAL_PITCH_ANGLE));
    public static final int SPIRAL_ARMS = 2;  // Number of spiral arms
    
    // Bar potential parameters (optional)
    public static final double BAR_AMPLITUDE = 0.05;      // 5% of disk potential
    public static final double BAR_PATTERN_SPEED = 50.0;  // km/s/kpc
    
    // Time parameters (in years)
    public static final double MIN_TIMESTEP = 10.0;       // Minimum timestep
    public static final double MAX_TIMESTEP = 100.0;      // Maximum timestep
    public static final double TIMESTEP_SAFETY = 0.01;    // Safety factor for adaptive timestep
    
    // Barnes-Hut parameters
    public static final double THETA = 0.7;  // Opening angle parameter
    
    // Energy monitoring
    public static final double ENERGY_TOLERANCE = 0.001;  // 0.1% energy drift tolerance
    public static final int ENERGY_CHECK_INTERVAL = 1000;  // Check energy every 1000 steps
    
    // Visualization parameters
    public static final int CANVAS_SIZE = 800;  // 800x800 pixels
    public static final double PIXELS_PER_PARSEC = CANVAS_SIZE / REGION_SIZE;
    
    // Drake simulation parameters (for future extensibility)
    public static final double HABITABLE_ZONE_RADIUS = 100.0;  // pc from SMBH
    public static final double MIN_METALLICITY_FOR_LIFE = 0.2;
    
    /**
     * Convert years to simulation time units
     */
    public static double yearsToSimTime(double years) {
        return years;
    }
    
    /**
     * Convert simulation time units to years
     */
    public static double simTimeToYears(double simTime) {
        return simTime;
    }
}
