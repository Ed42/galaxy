package com.example.galaxy_sim.engine;

/**
 * Physical constants and unit conversions.
 *
 * Internal simulation units:
 *  - Length: parsec (pc)
 *  - Time:   Megayear (Myr)
 *  - Mass:   Solar mass (M_sun)
 *  - Velocity: pc / Myr
 *
 * Given prompt constants use pc, km/s, and M_sun.
 * We convert (km/s) to (pc/Myr) via KM_S_TO_PC_PER_MYR.
 */
public final class Phys {
    private Phys() {}

    // Gravitational constant: G = 4.302e-3 pc (km/s)^2 / M_sun
    public static final double G_PC_KMS2_PER_MSUN = 4.302e-3;

    // Conversions
    public static final double KM_S_TO_PC_PER_MYR = 1.0227121650537078; // 1 km/s = 1.0227 pc/Myr
    public static final double YEAR_TO_MYR = 1.0 / 1_000_000.0;         // years -> Myr
    public static final double MYR_TO_YEAR = 1_000_000.0;               // Myr -> years

    // G in internal units: pc^3 / (M_sun * Myr^2)
    public static final double G = G_PC_KMS2_PER_MSUN * KM_S_TO_PC_PER_MYR * KM_S_TO_PC_PER_MYR;

    // Region and softenings
    public static final double REGION_HALF_SIZE_PC = 1000.0; // ±1 kpc
    public static final double SOFTENING_PC = 10.0;          // ε for particle-particle
    public static final double SOFTENING_SMBH_PC = 1.0;      // ε for SMBH

    // Background components (from prompt)
    public static final double M_DISK_MSUN = 1.0e11;
    public static final double A_DISK_KPC = 5.0; // kpc
    public static final double B_DISK_KPC = 0.3; // kpc
    public static final double A_DISK_PC = A_DISK_KPC * 1000.0;
    public static final double B_DISK_PC = B_DISK_KPC * 1000.0;

    public static final double M_BULGE_MSUN = 1.0e10;
    public static final double A_BULGE_KPC = 1.0; // kpc (Hernquist 'a')
    public static final double A_BULGE_PC = A_BULGE_KPC * 1000.0;

    public static final double V_HALO_KMS = 200.0; // km/s (isothermal halo)
    public static final double V_HALO_PC_PER_MYR = V_HALO_KMS * KM_S_TO_PC_PER_MYR;

    public static final double M_SMBH_MSUN = 4.0e6;

    // Spiral arms: amplitude ~ 10% of disk potential; we approximate using v_c^2 scale
    public static final double SPIRAL_FRACTION = 0.10; // 10%
    public static final int    SPIRAL_M = 2;           // 2-armed spiral
    public static final double SPIRAL_PITCH_DEG = 12.0; // between 10-15 degrees
    // k in Phi_spiral = A cos(m theta - k ln r); pitch angle i => tan i = m/(k r)
    // Approximate k from pitch: k = m / tan(i) / r_ref; we'll handle per-radius form by using k_const and r units in pc.
    public static final double SPIRAL_K_PER_PC = (SPIRAL_M / Math.tan(Math.toRadians(SPIRAL_PITCH_DEG))) / 1000.0; // using r_ref=1 kpc

    // Rotating bar potential
    public static final double BAR_FRACTION = 0.05; // 5% of disk potential
    // Pattern speed 50 km/s/kpc => convert to rad/Myr
    public static final double OMEGA_BAR_PER_MYR = (50.0 * KM_S_TO_PC_PER_MYR) / 1000.0; // pc/Myr per kpc -> 1/Myr

    // Disk properties for initialization
    public static final double DISK_SCALE_LENGTH_KPC = 1.0; // R_d
    public static final double DISK_SCALE_LENGTH_PC = DISK_SCALE_LENGTH_KPC * 1000.0;
    public static final double DISK_THICKNESS_PC = 300.0;   // assumed for rho estimate

    // Time stepping constraints
    public static final double DT_MIN_YEARS = 10.0;
    public static final double DT_MAX_YEARS = 100.0;
    public static final double DYNAMICAL_FACTOR = 0.01; // dt ~ 0.01 / sqrt(G rho)

    // Quadtree theta
    public static final double BH_THETA = 0.7;
}

