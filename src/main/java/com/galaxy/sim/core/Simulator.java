package com.galaxy.sim.core;

import com.galaxy.sim.physics.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Main galaxy simulation engine.
 * Manages particles, forces, time evolution, and energy monitoring.
 */
@Component
public class Simulator {
    private static final Logger logger = Logger.getLogger(Simulator.class.getName());
    
    // Simulation components
    private List<Particle> particles;
    private Quadtree quadtree;
    private BackgroundPotential backgroundPotential;
    private SymplecticIntegrator integrator;
    
    // Simulation state
    private double simulationTime = 0;  // in years
    private double timeScale = 100;     // years per second
    private double timestep = 50;       // years
    private boolean running = false;
    private boolean paused = false;
    private boolean fastForward = false;
    
    // Energy monitoring
    private double initialEnergy = 0;
    private double currentEnergy = 0;
    private int stepsSinceEnergyCheck = 0;
    
    // Parallelization
    private ExecutorService executorService;
    private final int numThreads;
    
    // Simulation parameters
    private int particleCount = 1000;
    private final Random random = new Random();
    
    public Simulator() {
        this.numThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(numThreads);
        this.quadtree = new Quadtree(PhysicsConstants.REGION_HALF_SIZE);
        this.backgroundPotential = new BackgroundPotential();
        this.integrator = new SymplecticIntegrator();
        this.particles = new ArrayList<>();
    }
    
    /**
     * Initialize particles with realistic galaxy distribution
     */
    public void initializeParticles(int count) {
        this.particleCount = count;
        particles.clear();
        
        // Add SMBH at center
        particles.add(Particle.createSMBH(0));
        
        // Determine disk vs bulge particle counts
        int bulgeCount = (int)(count * 0.2);  // 20% bulge stars
        int diskCount = count - bulgeCount - 1;  // Remaining are disk stars (minus SMBH)
        
        long id = 1;
        
        // Initialize bulge particles (concentrated near center)
        for (int i = 0; i < bulgeCount; i++) {
            Particle p = createBulgeParticle(id++);
            particles.add(p);
        }
        
        // Initialize disk particles (exponential distribution)
        for (int i = 0; i < diskCount; i++) {
            Particle p = createDiskParticle(id++);
            particles.add(p);
        }
        
        // Build initial quadtree
        rebuildQuadtree();
        
        // Calculate initial energy
        initialEnergy = integrator.calculateTotalEnergy(particles, backgroundPotential, quadtree);
        currentEnergy = initialEnergy;
        
        logger.info(String.format("Initialized %d particles (1 SMBH, %d bulge, %d disk)", 
                                  count, bulgeCount, diskCount));
    }
    
    /**
     * Create a bulge particle with Hernquist profile distribution
     */
    private Particle createBulgeParticle(long id) {
        // Hernquist cumulative mass: M(<r) = M * r^2 / (r + a)^2
        // Inverse: r = a * u / (1 - u) where u is uniform [0,1)
        double u = random.nextDouble() * 0.999;  // Avoid infinity
        double r = PhysicsConstants.BULGE_A * u / (1 - u);
        
        // Limit to simulation region
        r = Math.min(r, PhysicsConstants.REGION_HALF_SIZE * 0.9);
        
        // Random angle
        double theta = random.nextDouble() * 2 * Math.PI;
        
        // Position
        Vector2D position = Vector2D.fromPolar(r, theta);
        
        // Circular velocity at this radius
        double vCirc = backgroundPotential.getCircularVelocity(r);
        
        // Add dispersion (bulge has higher velocity dispersion)
        double dispersion = vCirc * 0.3;  // 30% of circular velocity
        double vr = random.nextGaussian() * dispersion;
        double vtheta = vCirc + random.nextGaussian() * dispersion;
        
        // Convert to Cartesian velocities
        double vx = vr * Math.cos(theta) - vtheta * Math.sin(theta);
        double vy = vr * Math.sin(theta) + vtheta * Math.cos(theta);
        Vector2D velocity = new Vector2D(vx, vy);
        
        // Bulge stars are older with higher metallicity
        double age = 5 + random.nextDouble() * 5;  // 5-10 Gyr
        double metallicity = 0.8 + random.nextDouble() * 0.4;  // 0.8-1.2 solar
        
        return Particle.createBulgeStar(id, position, velocity, 
                                        PhysicsConstants.M_PARTICLE, age, metallicity);
    }
    
    /**
     * Create a disk particle with exponential distribution
     */
    private Particle createDiskParticle(long id) {
        // Exponential disk: cumulative M(<r) = M * (1 - exp(-r/Rd) * (1 + r/Rd))
        // Use rejection sampling for simplicity
        double r, prob;
        do {
            r = random.nextDouble() * PhysicsConstants.REGION_HALF_SIZE;
            prob = Math.exp(-r / PhysicsConstants.DISK_SCALE_LENGTH);
        } while (random.nextDouble() > prob);
        
        // Random angle
        double theta = random.nextDouble() * 2 * Math.PI;
        
        // Position
        Vector2D position = Vector2D.fromPolar(r, theta);
        
        // Circular velocity at this radius
        double vCirc = backgroundPotential.getCircularVelocity(r);
        
        // Add small velocity dispersion
        double dispersion = 10.0;  // 10 km/s dispersion
        double vr = random.nextGaussian() * dispersion;
        double vtheta = vCirc + random.nextGaussian() * dispersion;
        
        // Convert to Cartesian velocities
        double vx = vr * Math.cos(theta) - vtheta * Math.sin(theta);
        double vy = vr * Math.sin(theta) + vtheta * Math.cos(theta);
        Vector2D velocity = new Vector2D(vx, vy);
        
        // Disk stars are younger with variable metallicity
        double age = random.nextDouble() * 5;  // 0-5 Gyr
        double metallicity = 0.2 + random.nextDouble() * 0.8;  // 0.2-1.0 solar
        
        return Particle.createDiskStar(id, position, velocity,
                                       PhysicsConstants.M_PARTICLE, age, metallicity);
    }
    
    /**
     * Perform one simulation step
     */
    public void step() {
        if (paused || !running) return;
        
        // Rebuild quadtree
        rebuildQuadtree();
        
        // Calculate adaptive timestep
        double dt = fastForward ? PhysicsConstants.MAX_TIMESTEP : 
                   integrator.calculateAdaptiveTimestep(quadtree, particles);
        
        // Scale by time scale
        dt = Math.min(dt, timeScale);
        
        // Create force function that combines particle and background forces
        Function<Particle, Vector2D> forceFunction = (Particle p) -> {
            Vector2D particleForce = quadtree.calculateForce(p);
            Vector2D backgroundForce = backgroundPotential.calculateTotalForce(p.position());
            return particleForce.add(backgroundForce.multiply(p.mass()));
        };
        
        // Integrate using symplectic method
        particles = integrator.integrate(particles, forceFunction, dt);
        
        // Update simulation time
        simulationTime += dt;
        backgroundPotential.updateTime(dt);
        
        // Update particle ages and habitability
        List<Particle> updatedParticles = new ArrayList<>();
        for (Particle p : particles) {
            Particle aged = p.updateAge(dt);
            Particle habitable = aged.updateHabitability();
            updatedParticles.add(habitable);
        }
        particles = updatedParticles;
        
        // Monitor energy conservation
        stepsSinceEnergyCheck++;
        if (stepsSinceEnergyCheck >= PhysicsConstants.ENERGY_CHECK_INTERVAL) {
            checkEnergyConservation();
            stepsSinceEnergyCheck = 0;
        }
    }
    
    /**
     * Rebuild the quadtree with current particle positions
     */
    private void rebuildQuadtree() {
        quadtree.rebuild();
        quadtree.insertAll(particles);
    }
    
    /**
     * Check energy conservation and adjust timestep if needed
     */
    private void checkEnergyConservation() {
        currentEnergy = integrator.calculateTotalEnergy(particles, backgroundPotential, quadtree);
        double energyDrift = Math.abs((currentEnergy - initialEnergy) / initialEnergy);
        
        if (energyDrift > PhysicsConstants.ENERGY_TOLERANCE) {
            logger.warning(String.format("Energy drift: %.4f%% at time %.2f years", 
                                        energyDrift * 100, simulationTime));
            
            // Reduce timestep if energy drift is too large
            if (!fastForward) {
                timestep *= 0.9;
                logger.info("Reducing timestep to " + timestep);
            }
        }
    }
    
    /**
     * Get current simulation state for visualization
     */
    public SimulationState getState() {
        return new SimulationState(
            new ArrayList<>(particles),
            simulationTime,
            currentEnergy,
            (currentEnergy - initialEnergy) / initialEnergy,
            integrator.getCurrentTimestep(),
            timeScale,
            running,
            paused,
            fastForward,
            backgroundPotential.isBarEnabled(),
            quadtree.getQuadtreeLines()
        );
    }
    
    /**
     * Start the simulation
     */
    public void start() {
        running = true;
        paused = false;
        logger.info("Simulation started");
    }
    
    /**
     * Pause the simulation
     */
    public void pause() {
        paused = true;
        logger.info("Simulation paused");
    }
    
    /**
     * Resume the simulation
     */
    public void resume() {
        paused = false;
        logger.info("Simulation resumed");
    }
    
    /**
     * Stop the simulation
     */
    public void stop() {
        running = false;
        paused = false;
        logger.info("Simulation stopped");
    }
    
    /**
     * Reset the simulation
     */
    public void reset() {
        stop();
        simulationTime = 0;
        backgroundPotential.resetTime();
        initializeParticles(particleCount);
        logger.info("Simulation reset");
    }
    
    /**
     * Set time scale (years per second)
     */
    public void setTimeScale(double scale) {
        this.timeScale = scale;
        logger.info("Time scale set to " + scale + " years/second");
    }
    
    /**
     * Enable/disable fast forward mode
     */
    public void setFastForward(boolean enabled) {
        this.fastForward = enabled;
        if (enabled) {
            setTimeScale(1e8);  // 100 Myr/s
        } else {
            setTimeScale(100);  // 100 yr/s
        }
        logger.info("Fast forward: " + enabled);
    }
    
    /**
     * Enable/disable bar potential
     */
    public void setBarEnabled(boolean enabled) {
        backgroundPotential.setBarEnabled(enabled);
        logger.info("Bar potential: " + enabled);
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    // Getters
    public double getSimulationTime() { return simulationTime; }
    public int getParticleCount() { return particles.size(); }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
}
