package com.example.galaxy_sim.engine;

/**
 * Background potentials: Miyamoto-Nagai disk, Hernquist bulge, isothermal halo,
 * logarithmic spiral arms, and central SMBH. Returns accelerations in pc/Myr^2.
 */
final class BackgroundPotential {
    private BackgroundPotential() {}

    static void addAccel(double tMyr, boolean barEnabled, Particle p) {
        // Disk (Miyamoto-Nagai) in plane (z=0)
        addDiskAccel(p);
        // Bulge (Hernquist)
        addHernquistBulgeAccel(p);
        // Halo (isothermal)
        addIsothermalHaloAccel(p);
        // Spiral arms (logarithmic)
        addSpiralArmsAccel(p);
        // Rotating bar (optional)
        if (barEnabled) addBarAccel(tMyr, p);
        // SMBH
        addSMBHAccel(p);
    }

    private static void addDiskAccel(Particle p) {
        double x = p.x, y = p.y;
        double R2 = x*x + y*y;
        double denom = Math.pow(R2 + Math.pow(Phys.A_DISK_PC + Phys.B_DISK_PC, 2.0), 1.5);
        if (denom == 0) return;
        double aR = -Phys.G * Phys.M_DISK_MSUN / denom; // times R later
        if (R2 > 1e-12) {
            double R = Math.sqrt(R2);
            double ax = aR * x * R; // a_R * (x/R) * R = a_R * x
            double ay = aR * y * R; // same
            p.ax += ax;
            p.ay += ay;
        }
    }

    private static void addHernquistBulgeAccel(Particle p) {
        double x = p.x, y = p.y;
        double r2 = x*x + y*y;
        double r = Math.sqrt(r2) + 1e-12;
        double factor = -Phys.G * Phys.M_BULGE_MSUN / (r * Math.pow(r + Phys.A_BULGE_PC, 2.0));
        p.ax += factor * x;
        p.ay += factor * y;
    }

    private static void addIsothermalHaloAccel(Particle p) {
        double x = p.x, y = p.y;
        double r2 = x*x + y*y + Phys.SOFTENING_PC*Phys.SOFTENING_PC; // avoid singularity at center
        if (r2 == 0) return;
        double factor = - (Phys.V_HALO_PC_PER_MYR * Phys.V_HALO_PC_PER_MYR) / r2;
        p.ax += factor * x;
        p.ay += factor * y;
    }

    private static void addSpiralArmsAccel(Particle p) {
        double x = p.x, y = p.y;
        double r2 = x*x + y*y;
        double r = Math.sqrt(r2) + 1e-9;
        double theta = Math.atan2(y, x);
        double arg = Phys.SPIRAL_M * theta - (Phys.SPIRAL_K_PER_PC * Math.log(Math.max(r, 1.0))) * r; // simple k ln r approx
        // Amplitude scaled to halo velocity squared as baseline
        double A = Phys.SPIRAL_FRACTION * (Phys.V_HALO_PC_PER_MYR * Phys.V_HALO_PC_PER_MYR);

        // Polar components
        double dphidr = A * Math.sin(arg) * (Phys.SPIRAL_K_PER_PC); // ∂Φ/∂r
        double dphidtheta = -A * Phys.SPIRAL_M * Math.sin(arg);
        double a_r = -dphidr;
        double a_theta = -(1.0 / r) * dphidtheta;

        double cos = x / r, sin = y / r;
        double ax = a_r * cos - a_theta * sin;
        double ay = a_r * sin + a_theta * cos;
        if (Double.isFinite(ax)) p.ax += ax;
        if (Double.isFinite(ay)) p.ay += ay;
    }

    private static void addBarAccel(double tMyr, Particle p) {
        double x = p.x, y = p.y;
        double r = Math.hypot(x, y) + 1e-9;
        double theta = Math.atan2(y, x);
        double phase = 2.0 * theta - Phys.OMEGA_BAR_PER_MYR * tMyr;
        double A = Phys.BAR_FRACTION * (Phys.V_HALO_PC_PER_MYR * Phys.V_HALO_PC_PER_MYR);
        double dphidr = A * Math.sin(phase) * (1.0 / Math.max(r, 10.0));
        double dphidtheta = -2.0 * A * Math.sin(phase);
        double a_r = -dphidr;
        double a_theta = -(1.0 / r) * dphidtheta;
        double cos = x / r, sin = y / r;
        double ax = a_r * cos - a_theta * sin;
        double ay = a_r * sin + a_theta * cos;
        if (Double.isFinite(ax)) p.ax += ax;
        if (Double.isFinite(ay)) p.ay += ay;
    }

    private static void addSMBHAccel(Particle p) {
        double x = p.x, y = p.y;
        double r2 = x*x + y*y + Phys.SOFTENING_SMBH_PC*Phys.SOFTENING_SMBH_PC;
        double invR = 1.0 / Math.sqrt(r2);
        double invR2 = invR * invR;
        double aMag = Phys.G * Phys.M_SMBH_MSUN * invR2 * invR; // GM / r^2 with softening
        p.ax += -aMag * x;
        p.ay += -aMag * y;
    }
}

