package com.example.galaxy_sim.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulation engine: maintains particles, integrates with 4th-order symplectic, adaptive dt.
 * Provides snapshots for rendering and endpoints to control speed and bar toggle.
 */
public final class SimulationEngine {
    private final List<Particle> particles = new ArrayList<>();
    private final ForceComputer forces;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Controls
    private volatile boolean paused = false;
    private volatile boolean barEnabled = false;
    private volatile double yearsPerSecond = 1_000_000.0; // default 1 Myr/s

    // Time bookkeeping
    private volatile double tMyr = 0.0;
    private long steps = 0;

    // Energy monitor
    private double lastEnergy = Double.NaN;
    private double lastEnergyTimeYears = 0.0;

    public SimulationEngine(int nParticles, int threads) {
        this.forces = new ForceComputer(threads);
        initParticles(nParticles);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS); // ~60 Hz
        }
    }

    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        forces.shutdown();
    }

    private void initParticles(int n) {
        particles.clear();
        Random rng = new Random(42);
        double totalPatchMass = 1.0e9; // Msun in the 2kpc patch
        double massPerParticle = totalPatchMass / n;
        for (int i = 0; i < n; i++) {
            // Sample r from Gamma(k=2, theta=R_d)
            double r;
            do {
                double u1 = Math.max(1e-12, rng.nextDouble());
                double u2 = Math.max(1e-12, rng.nextDouble());
                r = -Phys.DISK_SCALE_LENGTH_PC * Math.log(u1) - Phys.DISK_SCALE_LENGTH_PC * Math.log(u2);
            } while (r > Phys.REGION_HALF_SIZE_PC);
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double x = r * Math.cos(theta);
            double y = r * Math.sin(theta);

            // Circular velocity from halo as baseline
            double v_c = Phys.V_HALO_PC_PER_MYR; // pc/Myr
            double tx = -Math.sin(theta);
            double ty = Math.cos(theta);
            double vx = v_c * tx;
            double vy = v_c * ty;

            // Disk vs bulge fraction: central bulge concentration
            StarType type = (r < 300.0 && rng.nextDouble() < 0.6) ? StarType.BULGE : StarType.DISK;

            // Velocity perturbations 5-10 km/s
            double sigma_kms = 5.0 + 5.0 * rng.nextDouble();
            double sigma = sigma_kms * Phys.KM_S_TO_PC_PER_MYR; // pc/Myr
            vx += gaussian(rng, 0, sigma);
            vy += gaussian(rng, 0, sigma);

            // Ages and metallicity
            double ageGyr = type == StarType.BULGE ? (5.0 + rng.nextDouble() * 5.0) : (rng.nextDouble() * 5.0);
            double metallicity = type == StarType.BULGE ? (0.9 + 0.1 * rng.nextDouble()) : (0.2 + 0.8 * rng.nextDouble());

            particles.add(new Particle(x, y, vx, vy, massPerParticle, type, ageGyr, metallicity));
        }
        // Initial accelerations
        forces.computeAccelerations(tMyr, barEnabled, particles);
    }

    private static double gaussian(Random rng, double mean, double std) {
        double u1 = Math.max(1e-12, rng.nextDouble());
        double u2 = Math.max(1e-12, rng.nextDouble());
        double r = Math.sqrt(-2.0 * Math.log(u1));
        double ang = 2.0 * Math.PI * u2;
        return mean + std * r * Math.cos(ang);
    }

    private void tick() {
        if (paused) return;
        try {
            double frameTargetYears = yearsPerSecond / 60.0; // 60Hz
            double remaining = frameTargetYears;
            // Use a fresh tree for dt suggestion
            QuadTree tempTree = new QuadTree();
            tempTree.build(particles);
            while (remaining > 0) {
                double dtYears = new ForceComputer(1).suggestDtYears(particles, tempTree); // cheap estimate; threads=1
                // Bound by remaining to meet real-time speed mapping
                dtYears = Math.min(dtYears, remaining);
                IntegratorSymplectic4.step(particles, tMyr, dtYears, barEnabled, forces);
                tMyr += dtYears * Phys.YEAR_TO_MYR;
                steps++;
                remaining -= dtYears;
                if (steps % 1000 == 0) monitorEnergyAndAdjust(dtYears);
            }
        } catch (Throwable t) {
            // swallow to keep scheduler alive
        }
    }

    private void monitorEnergyAndAdjust(double dtYears) {
        double E = totalEnergyApprox();
        double tYears = tMyr * Phys.MYR_TO_YEAR;
        if (Double.isNaN(lastEnergy)) {
            lastEnergy = E; lastEnergyTimeYears = tYears; return;
        }
        double horizon = 10_000.0; // years
        if (tYears - lastEnergyTimeYears >= horizon) {
            double drift = Math.abs((E - lastEnergy) / lastEnergy);
            if (drift > 0.001) {
                // Reduce speed target to effectively reduce dt per frame
                yearsPerSecond *= 0.8; // gentle reduction
            }
            lastEnergy = E;
            lastEnergyTimeYears = tYears;
        }
    }

    // Approximate total energy using BH-style potential estimation and kinetic energy
    public double getEnergyApprox() { return totalEnergyApprox(); }

    private double totalEnergyApprox() {
        // Kinetic
        double K = 0.0;
        for (Particle p : particles) {
            double v2 = p.vx * p.vx + p.vy * p.vy;
            // mass * (pc/Myr)^2; potential uses same internal units (pc^2/Myr^2) * mass
            K += 0.5 * p.mass * v2;
        }
        // Background potential energy approximation (not exact pair-wise)
        double Ubg = 0.0;
        for (Particle p : particles) {
            // Use a proxy: m * v_c^2 * ln r scale around halo; bounded
            double r = Math.max(1.0, Math.hypot(p.x, p.y));
            double v2 = Phys.V_HALO_PC_PER_MYR * Phys.V_HALO_PC_PER_MYR;
            Ubg += p.mass * v2 * Math.log(r);
        }
        return K + Ubg;
    }

    // Public API
    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }
    public void togglePause() { this.paused = !this.paused; }
    public void reset(int n) { tMyr = 0; steps = 0; initParticles(n); }
    public void setYearsPerSecond(double yps) { this.yearsPerSecond = Math.max(100.0, Math.min(100_000_000.0, yps)); }
    public double getYearsPerSecond() { return yearsPerSecond; }
    public void setBarEnabled(boolean enabled) { this.barEnabled = enabled; }
    public boolean isBarEnabled() { return barEnabled; }

    public double getTimeYears() { return tMyr * Phys.MYR_TO_YEAR; }
    public long getSteps() { return steps; }

    public List<Particle> getParticles() { return particles; }
}

