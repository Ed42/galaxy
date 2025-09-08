package com.example.galaxy_sim.engine;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Computes total accelerations: Barnes–Hut self-gravity + background potentials + SMBH.
 */
final class ForceComputer {
    private final ExecutorService pool;
    private final int threads;

    ForceComputer(int threads) {
        this.threads = Math.max(1, threads);
        this.pool = Executors.newFixedThreadPool(this.threads);
    }

    void shutdown() { pool.shutdownNow(); }

    void computeAccelerations(double tMyr, boolean barEnabled, List<Particle> particles) {
        // Build tree for current particle positions
        QuadTree tree = new QuadTree();
        tree.build(particles);

        // Parallel over particles
        int n = particles.size();
        int chunk = Math.max(1, n / threads);
        Future<?>[] tasks = new Future<?>[threads];
        for (int ti = 0; ti < threads; ti++) {
            final int start = ti * chunk;
            final int end = (ti == threads - 1) ? n : Math.min(n, (ti + 1) * chunk);
            tasks[ti] = pool.submit(() -> {
                for (int i = start; i < end; i++) {
                    Particle p = particles.get(i);
                    p.resetAcc();
                    // Self gravity (Barnes–Hut)
                    tree.addAcceleration(p);
                    // Background potentials
                    BackgroundPotential.addAccel(tMyr, barEnabled, p);
                    // Derived values
                    p.distToSMBH = Math.hypot(p.x, p.y);
                }
            });
        }
        for (Future<?> f : tasks) {
            if (f != null) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
    }

    // Estimate a conservative global dt (years) using local density and SMBH constraints
    double suggestDtYears(List<Particle> particles, QuadTree tree) {
        double rhoMax = 0.0;
        for (Particle p : particles) {
            double rho = tree.estimateLocalRhoMsunPerPc3(p);
            if (rho > rhoMax) rhoMax = rho;
        }
        double dtDynMyr = rhoMax > 0.0 ? Phys.DYNAMICAL_FACTOR / Math.sqrt(Phys.G * rhoMax) : Phys.DT_MAX_YEARS * Phys.YEAR_TO_MYR;
        double dtDynYears = dtDynMyr * Phys.MYR_TO_YEAR;

        // SMBH constraint using Kepler timescale T ~ 2π sqrt(r^3 / GM)
        double rMin = Double.POSITIVE_INFINITY;
        for (Particle p : particles) {
            double r = Math.hypot(p.x, p.y);
            if (r < rMin) rMin = r;
        }
        if (Double.isFinite(rMin) && rMin > 0) {
            double T_Myr = 2.0 * Math.PI * Math.sqrt((rMin * rMin * rMin) / (Phys.G * Phys.M_SMBH_MSUN));
            double dtBHYears = 0.01 * T_Myr * Phys.MYR_TO_YEAR; // 1% of orbital period
            dtDynYears = Math.min(dtDynYears, dtBHYears);
        }

        // Clamp to [10, 100] years
        dtDynYears = Math.max(Phys.DT_MIN_YEARS, Math.min(Phys.DT_MAX_YEARS, dtDynYears));
        return dtDynYears;
    }
}

