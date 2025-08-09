package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.physics.BackgroundPotential;
import com.example.galaxy_sim.physics.YoshidaIntegrator;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main galaxy simulation engine with adaptive time-stepping and energy monitoring.
 * Supports time scales from 100 yr/s to 100 Myr/s with numerical stability.
 */
@Service
public class Simulator {
    // Physical constants
    private static final double G = 4.302e-3; // pc (M_sun)^-1 (km/s)^2

    // Simulation parameters
    private static final double REGION_SIZE = 1000.0; // ±1 kpc
    private static final double PARTICLE_MASS = 1e6; // solar masses per particle
    private static final double SCALE_LENGTH = 1000.0; // Rd = 1 kpc
    private static final double TOTAL_MASS = 1e9; // Total mass in region (solar masses)

    // Time-stepping parameters
    private static final double MIN_DT = 10.0; // Minimum time step (years)
    private static final double MAX_DT = 100.0; // Maximum time step (years)
    private static final double ENERGY_TOLERANCE = 0.001; // 0.1% energy drift tolerance
    private static final int ENERGY_CHECK_INTERVAL = 1000; // Check energy every N steps

    // Simulation state
    private List<Particle> particles = new ArrayList<>();
    private BackgroundPotential backgroundPotential;
    private YoshidaIntegrator integrator;
    private ExecutorService executorService;

    private double currentTime = 0.0; // years
    private double timeScale = 100.0; // years/second (simulation time per real second)
    private double currentDt = 50.0; // current adaptive time step
    private double initialEnergy = 0.0;
    private int stepCount = 0;

    // Fast-forward mode
    private boolean fastForward = false;
    private static final double FAST_FORWARD_SCALE = 100e6; // 100 Myr/s

    public Simulator() {
        this.backgroundPotential = new BackgroundPotential();
        this.integrator = new YoshidaIntegrator(backgroundPotential);
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
        initializeParticles(1000); // Start with 1000 particles
    }

    /**
     * Initializes particles with exponential disk profile and circular velocities
     */
    public void initializeParticles(int numParticles) {
        particles.clear();
        Random random = new Random(42); // Reproducible seed

        for (int i = 0; i < numParticles; i++) {
            // Generate position using exponential disk profile
            // Σ(r) = Σ_0 exp(-r/R_d)
            double r = generateExponentialRadius(random, SCALE_LENGTH);
            double theta = 2 * Math.PI * random.nextDouble();

            double x = r * Math.cos(theta);
            double y = r * Math.sin(theta);

            // Ensure particles are within simulation region
            if (Math.abs(x) > REGION_SIZE || Math.abs(y) > REGION_SIZE) {
                i--; // Retry this particle
                continue;
            }

            // Calculate circular velocity from background potential
            double vCirc = backgroundPotential.getCircularVelocity(r);

            // Add velocity perturbations for clustering (5-10 km/s)
            double vPerturbation = 5.0 + 5.0 * random.nextGaussian();
            vCirc += vPerturbation;

            // Circular velocity components (perpendicular to radius)
            double vx = -vCirc * Math.sin(theta);
            double vy = vCirc * Math.cos(theta);

            // Determine stellar type and properties based on radius
            Particle.StellarType stellarType;
            double age, metallicity;
            boolean habitable = false;

            if (r < 500.0) { // Bulge region
                stellarType = random.nextDouble() < 0.8 ?
                    Particle.StellarType.GIANT : Particle.StellarType.MAIN_SEQUENCE;
                age = 5.0 + 5.0 * random.nextDouble(); // 5-10 Gyr
                metallicity = 0.8 + 0.4 * random.nextDouble(); // ~1.0 [Fe/H]
            } else { // Disk region
                stellarType = Particle.StellarType.MAIN_SEQUENCE;
                age = 5.0 * random.nextDouble(); // 0-5 Gyr
                metallicity = 0.2 + 0.8 * random.nextDouble(); // 0.2-1.0 [Fe/H]
            }

            double distanceToSMBH = r;

            Particle particle = new Particle(
                x, y, vx, vy, PARTICLE_MASS,
                stellarType, age, metallicity, habitable, distanceToSMBH
            );

            particles.add(particle);
        }

        // Calculate initial energy for monitoring
        initialEnergy = integrator.calculateTotalEnergy(particles);
        System.out.println("Initialized " + numParticles + " particles");
        System.out.println("Initial total energy: " + initialEnergy);
    }

    /**
     * Generates radius following exponential disk profile
     */
    private double generateExponentialRadius(Random random, double scaleLength) {
        // Inverse transform sampling for exp(-r/R_d) profile
        // Clamp to avoid extremely large radii that escape simulation bounds
        double u = random.nextDouble();
        if (u > 0.995) u = 0.995; // Prevent log(0) and extreme values
        double r = -scaleLength * Math.log(1 - u);
        return Math.min(r, REGION_SIZE * 0.8); // Keep within 80% of region size
    }

    /**
     * Advances simulation by one time step
     */
    public synchronized void step() {
        // Update background potential time for bar rotation
        backgroundPotential.setCurrentTime(currentTime);

        // Adaptive time-stepping
        double adaptiveDt = calculateAdaptiveTimeStep();
        currentDt = Math.max(MIN_DT, Math.min(MAX_DT, adaptiveDt));

        if (fastForward) {
            currentDt *= FAST_FORWARD_SCALE / timeScale;
        }

        // Advance particles
        particles = integrator.step(particles, currentDt);

        // Update simulation time
        currentTime += currentDt;
        stepCount++;

        // Monitor energy periodically
        if (stepCount % ENERGY_CHECK_INTERVAL == 0) {
            monitorEnergyConservation();
        }
    }

    /**
     * Calculates adaptive time step based on local density and dynamics
     * Δt = min(0.01 × (Gρ)^(-1/2), MAX_DT)
     */
    private double calculateAdaptiveTimeStep() {
        double maxDensity = integrator.getMaxDensity(particles);

        if (maxDensity > 0) {
            double dynamicalTime = 0.01 / Math.sqrt(G * maxDensity);
            return Math.min(dynamicalTime, MAX_DT);
        }

        return MAX_DT;
    }

    /**
     * Monitors energy conservation and adjusts time step if needed
     */
    private void monitorEnergyConservation() {
        double currentEnergy = integrator.calculateTotalEnergy(particles);
        double energyDrift = Math.abs((currentEnergy - initialEnergy) / initialEnergy);

        if (energyDrift > ENERGY_TOLERANCE) {
            // Reduce time step for better energy conservation
            currentDt *= 0.5;
            System.out.println("Warning: Energy drift " + (energyDrift * 100) +
                             "%, reducing time step to " + currentDt + " years");
        }

        System.out.println("Step " + stepCount + ", Time: " + (currentTime / 1e6) +
                         " Myr, Energy drift: " + (energyDrift * 100) + "%");
    }

    /**
     * Resets simulation to initial state
     */
    public synchronized void reset() {
        currentTime = 0.0;
        stepCount = 0;
        currentDt = 50.0;
        fastForward = false;
        initializeParticles(particles.size());
    }

    // Getters and setters
    public synchronized List<Particle> getParticles() {
        return new ArrayList<>(particles);
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public double getTimeScale() {
        return timeScale;
    }

    public void setTimeScale(double scale) {
        this.timeScale = Math.max(100.0, Math.min(100e6, scale));
    }

    public boolean isFastForward() {
        return fastForward;
    }

    public void setFastForward(boolean fastForward) {
        this.fastForward = fastForward;
        if (fastForward) {
            this.timeScale = FAST_FORWARD_SCALE;
        }
    }

    public void setBarEnabled(boolean enabled) {
        backgroundPotential.setBarEnabled(enabled);
    }

    public synchronized double getCurrentEnergy() {
        return integrator.calculateTotalEnergy(particles);
    }

    public synchronized double getEnergyDrift() {
        if (initialEnergy != 0) {
            return Math.abs((getCurrentEnergy() - initialEnergy) / initialEnergy);
        }
        return 0.0;
    }

    public synchronized int getParticleCount() {
        return particles.size();
    }

    public synchronized void setParticleCount(int count) {
        if (count != particles.size()) {
            initializeParticles(count);
        }
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
