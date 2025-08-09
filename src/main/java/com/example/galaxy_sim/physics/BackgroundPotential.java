package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;

/**
 * Calculates background potential and forces from galaxy components:
 * - Miyamoto-Nagai disk
 * - Hernquist bulge
 * - Isothermal halo
 * - Logarithmic spiral arms
 * - Supermassive black hole (SMBH)
 * - Optional rotating bar
 */
public class BackgroundPotential {
    // Physical constants
    private static final double G = 4.302e-3; // pc (M_sun)^-1 (km/s)^2

    // Disk parameters (Miyamoto-Nagai)
    private static final double M_DISK = 1e11; // Solar masses
    private static final double A_DISK = 5000.0; // pc
    private static final double B_DISK = 300.0; // pc

    // Bulge parameters (Hernquist)
    private static final double M_BULGE = 1e10; // Solar masses
    private static final double A_BULGE = 1000.0; // pc

    // Halo parameters (Isothermal)
    private static final double V_C_HALO = 200.0; // km/s

    // Spiral arm parameters
    private static final double A_SPIRAL_FRACTION = 0.1; // 10% of disk potential
    private static final double PITCH_ANGLE = Math.toRadians(12.5); // ~12.5 degrees
    private static final double K_SPIRAL = 1.0 / Math.tan(PITCH_ANGLE);

    // SMBH parameters
    private static final double M_SMBH = 4e6; // Solar masses
    private static final double EPSILON_SMBH = 1.0; // pc softening

    // Bar parameters (optional)
    private static final double A_BAR_FRACTION = 0.05; // 5% of disk potential
    private static final double OMEGA_BAR = 50.0 / 1000.0; // rad/Myr (50 km/s/kpc)

    private boolean barEnabled = false;
    private double currentTime = 0.0; // Current simulation time in years

    public void setBarEnabled(boolean enabled) {
        this.barEnabled = enabled;
    }

    public void setCurrentTime(double timeInYears) {
        this.currentTime = timeInYears;
    }

    /**
     * Calculates the total background force on a particle
     */
    public Quadtree.Force calculateBackgroundForce(Particle particle) {
        double x = particle.x();
        double y = particle.y();
        double r = Math.sqrt(x * x + y * y);

        if (r < 1e-10) {
            return new Quadtree.Force(0, 0);
        }

        // Calculate forces from all components
        Quadtree.Force diskForce = calculateDiskForce(x, y, r);
        Quadtree.Force bulgeForce = calculateBulgeForce(x, y, r);
        Quadtree.Force haloForce = calculateHaloForce(x, y, r);
        Quadtree.Force spiralForce = calculateSpiralForce(x, y, r);
        Quadtree.Force smbhForce = calculateSMBHForce(x, y, r);

        Quadtree.Force totalForce = diskForce.add(bulgeForce).add(haloForce).add(spiralForce).add(smbhForce);

        if (barEnabled) {
            Quadtree.Force barForce = calculateBarForce(x, y, r);
            totalForce = totalForce.add(barForce);
        }

        return totalForce;
    }

    /**
     * Miyamoto-Nagai disk force calculation
     */
    private Quadtree.Force calculateDiskForce(double x, double y, double r) {
        double z = 0; // 2D simulation
        double R = Math.sqrt(x * x + y * y);
        double denomSq = A_DISK + Math.sqrt(z * z + B_DISK * B_DISK);
        double denom = Math.pow(R * R + denomSq * denomSq, 1.5);

        double factor = -G * M_DISK / denom;
        double fx = factor * x;
        double fy = factor * y;

        return new Quadtree.Force(fx, fy);
    }

    /**
     * Hernquist bulge force calculation
     */
    private Quadtree.Force calculateBulgeForce(double x, double y, double r) {
        double factor = -G * M_BULGE / (r * Math.pow(r + A_BULGE, 2));
        double fx = factor * x / r;
        double fy = factor * y / r;

        return new Quadtree.Force(fx, fy);
    }

    /**
     * Isothermal halo force calculation
     */
    private Quadtree.Force calculateHaloForce(double x, double y, double r) {
        double factor = -V_C_HALO * V_C_HALO / r;
        double fx = factor * x / r;
        double fy = factor * y / r;

        return new Quadtree.Force(fx, fy);
    }

    /**
     * Logarithmic spiral arms force calculation
     * Φ_spiral = A cos(2θ - k ln(r))
     */
    private Quadtree.Force calculateSpiralForce(double x, double y, double r) {
        if (r < 1e-10) {
            return new Quadtree.Force(0, 0);
        }

        double theta = Math.atan2(y, x);
        double spiralPhase = 2 * theta - K_SPIRAL * Math.log(r);

        // Get reference disk potential for amplitude
        double diskPotential = getDiskPotential(r);
        double A_spiral = A_SPIRAL_FRACTION * Math.abs(diskPotential);

        // Derivatives of potential for force calculation
        double dPhidr = A_spiral * Math.sin(spiralPhase) * K_SPIRAL / r;
        double dPhidtheta = -2 * A_spiral * Math.sin(spiralPhase);

        // Convert to Cartesian forces
        double fx = -dPhidr * x / r + dPhidtheta * (-y / (r * r));
        double fy = -dPhidr * y / r + dPhidtheta * (x / (r * r));

        return new Quadtree.Force(fx, fy);
    }

    /**
     * SMBH force calculation with softening
     */
    private Quadtree.Force calculateSMBHForce(double x, double y, double r) {
        double softenedR = Math.sqrt(r * r + EPSILON_SMBH * EPSILON_SMBH);
        double factor = -G * M_SMBH / (softenedR * softenedR * softenedR);
        double fx = factor * x;
        double fy = factor * y;

        return new Quadtree.Force(fx, fy);
    }

    /**
     * Rotating bar potential force calculation
     * Φ_bar = A_bar cos(2θ - Ω_bar t)
     */
    private Quadtree.Force calculateBarForce(double x, double y, double r) {
        if (r < 1e-10) {
            return new Quadtree.Force(0, 0);
        }

        double theta = Math.atan2(y, x);
        double barPhase = 2 * theta - OMEGA_BAR * currentTime * 1e6; // Convert to years

        // Get reference disk potential for amplitude
        double diskPotential = getDiskPotential(r);
        double A_bar = A_BAR_FRACTION * Math.abs(diskPotential);

        // Force calculation
        double dPhidtheta = 2 * A_bar * Math.sin(barPhase);

        // Convert to Cartesian forces
        double fx = dPhidtheta * (-y / (r * r));
        double fy = dPhidtheta * (x / (r * r));

        return new Quadtree.Force(fx, fy);
    }

    /**
     * Helper method to get disk potential magnitude for spiral/bar amplitude
     */
    private double getDiskPotential(double r) {
        double z = 0;
        double R = r;
        double denomSq = A_DISK + Math.sqrt(z * z + B_DISK * B_DISK);
        double denom = Math.sqrt(R * R + denomSq * denomSq);
        return -G * M_DISK / denom;
    }

    /**
     * Calculates circular velocity at given radius for initial conditions
     */
    public double getCircularVelocity(double r) {
        if (r < 1e-10) {
            return 0;
        }

        // Approximate circular velocity from all components
        double vDisk = getCircularVelocityDisk(r);
        double vBulge = getCircularVelocityBulge(r);
        double vHalo = V_C_HALO;
        double vSMBH = getCircularVelocitySMBH(r);

        // Combine velocities in quadrature
        return Math.sqrt(vDisk * vDisk + vBulge * vBulge + vHalo * vHalo + vSMBH * vSMBH);
    }

    private double getCircularVelocityDisk(double r) {
        // Simplified approximation for Miyamoto-Nagai disk
        double factor = G * M_DISK * r * r / Math.pow(r * r + (A_DISK + B_DISK) * (A_DISK + B_DISK), 1.5);
        return Math.sqrt(factor);
    }

    private double getCircularVelocityBulge(double r) {
        // Hernquist bulge circular velocity
        double factor = G * M_BULGE * r / Math.pow(r + A_BULGE, 2);
        return Math.sqrt(factor);
    }

    private double getCircularVelocitySMBH(double r) {
        // SMBH circular velocity with softening
        double softenedR = Math.sqrt(r * r + EPSILON_SMBH * EPSILON_SMBH);
        return Math.sqrt(G * M_SMBH / softenedR);
    }
}
