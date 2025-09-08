package com.example.galaxy_sim.engine;

import java.util.List;

/**
 * 4th-order symplectic integrator (Yoshida triple-jump composition of leapfrog).
 */
final class IntegratorSymplectic4 {
    private IntegratorSymplectic4() {}

    // Yoshida coefficients
    private static final double C = Math.cbrt(2.0); // 2^(1/3)
    private static final double W1 = 1.0 / (2.0 - C);
    private static final double W2 = -C / (2.0 - C);
    private static final double W3 = W1;

    static void step(List<Particle> particles, double tMyr, double dtYears, boolean barEnabled, ForceComputer forces) {
        double dtMyr = dtYears * Phys.YEAR_TO_MYR;
        // S4(dt) = S2(W1*dt) S2(W2*dt) S2(W3*dt)
        tMyr = s2(particles, tMyr, W1 * dtMyr, barEnabled, forces);
        tMyr = s2(particles, tMyr, W2 * dtMyr, barEnabled, forces);
        tMyr = s2(particles, tMyr, W3 * dtMyr, barEnabled, forces);
        // return updated time via side effects on caller variable if needed
    }

    private static double s2(List<Particle> particles, double tMyr, double hMyr, boolean barEnabled, ForceComputer forces) {
        // Drift half
        double half = 0.5 * hMyr;
        for (Particle p : particles) {
            p.x += p.vx * half;
            p.y += p.vy * half;
        }
        // Compute accelerations at mid positions
        forces.computeAccelerations(tMyr, barEnabled, particles);
        // Kick full
        for (Particle p : particles) {
            p.vx += p.ax * hMyr;
            p.vy += p.ay * hMyr;
        }
        // Drift half
        for (Particle p : particles) {
            p.x += p.vx * half;
            p.y += p.vy * half;
        }
        return tMyr + hMyr;
    }
}

