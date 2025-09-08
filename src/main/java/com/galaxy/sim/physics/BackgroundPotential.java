package com.galaxy.sim.physics;

/**
 * Calculates background gravitational potential and forces from galaxy components:
 * - Miyamoto-Nagai disk
 * - Hernquist bulge
 * - Isothermal halo
 * - Logarithmic spiral arms
 * - Supermassive black hole (SMBH)
 * - Optional rotating bar
 */
public class BackgroundPotential {
    
    private boolean barEnabled = false;
    private double currentTime = 0;
    
    /**
     * Calculate total force from all background potentials at a given position
     */
    public Vector2D calculateTotalForce(Vector2D position) {
        Vector2D totalForce = Vector2D.ZERO;
        
        // Add forces from each component
        totalForce = totalForce.add(calculateDiskForce(position));
        totalForce = totalForce.add(calculateBulgeForce(position));
        totalForce = totalForce.add(calculateHaloForce(position));
        totalForce = totalForce.add(calculateSpiralForce(position));
        totalForce = totalForce.add(calculateSMBHForce(position));
        
        if (barEnabled) {
            totalForce = totalForce.add(calculateBarForce(position));
        }
        
        return totalForce;
    }
    
    /**
     * Calculate total potential energy at a given position
     */
    public double calculateTotalPotential(Vector2D position) {
        double totalPotential = 0;
        
        totalPotential += calculateDiskPotential(position);
        totalPotential += calculateBulgePotential(position);
        totalPotential += calculateHaloPotential(position);
        totalPotential += calculateSpiralPotential(position);
        totalPotential += calculateSMBHPotential(position);
        
        if (barEnabled) {
            totalPotential += calculateBarPotential(position);
        }
        
        return totalPotential;
    }
    
    /**
     * Miyamoto-Nagai disk potential
     * Φ_disk = -GM_disk / sqrt(R^2 + (a + sqrt(z^2 + b^2))^2)
     * For 2D: z = 0
     */
    private double calculateDiskPotential(Vector2D position) {
        double R = position.magnitude();
        double z = 0;  // 2D simulation
        double b = PhysicsConstants.DISK_B;
        double a = PhysicsConstants.DISK_A;
        
        double zTerm = Math.sqrt(z * z + b * b);
        double denominator = Math.sqrt(R * R + Math.pow(a + zTerm, 2));
        
        return -PhysicsConstants.G * PhysicsConstants.M_DISK / denominator;
    }
    
    /**
     * Force from Miyamoto-Nagai disk
     */
    private Vector2D calculateDiskForce(Vector2D position) {
        double R = position.magnitude();
        if (R < 1e-10) return Vector2D.ZERO;  // Avoid division by zero
        
        double z = 0;  // 2D simulation
        double b = PhysicsConstants.DISK_B;
        double a = PhysicsConstants.DISK_A;
        
        double zTerm = Math.sqrt(z * z + b * b);
        double denominator = Math.pow(R * R + Math.pow(a + zTerm, 2), 1.5);
        
        double forceMag = PhysicsConstants.G * PhysicsConstants.M_DISK * R / denominator;
        
        // Force points toward center
        return position.normalize().multiply(-forceMag);
    }
    
    /**
     * Hernquist bulge potential
     * Φ_bulge = -GM_bulge / (r + a)
     */
    private double calculateBulgePotential(Vector2D position) {
        double r = position.magnitude();
        double a = PhysicsConstants.BULGE_A;
        
        return -PhysicsConstants.G * PhysicsConstants.M_BULGE / (r + a);
    }
    
    /**
     * Force from Hernquist bulge
     */
    private Vector2D calculateBulgeForce(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return Vector2D.ZERO;
        
        double a = PhysicsConstants.BULGE_A;
        double forceMag = PhysicsConstants.G * PhysicsConstants.M_BULGE / Math.pow(r + a, 2);
        
        // Force points toward center
        return position.normalize().multiply(-forceMag);
    }
    
    /**
     * Isothermal halo potential
     * Φ_halo = v_c^2 * ln(r)
     */
    private double calculateHaloPotential(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return 0;
        
        return PhysicsConstants.HALO_V_C * PhysicsConstants.HALO_V_C * Math.log(r);
    }
    
    /**
     * Force from isothermal halo
     * F = v_c^2 / r (toward center)
     */
    private Vector2D calculateHaloForce(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return Vector2D.ZERO;
        
        double forceMag = PhysicsConstants.HALO_V_C * PhysicsConstants.HALO_V_C / r;
        
        // Force points toward center
        return position.normalize().multiply(-forceMag);
    }
    
    /**
     * Logarithmic spiral arm potential
     * Φ_spiral = A * cos(m*θ - k*ln(r))
     * where m = number of arms, k = pitch angle parameter
     */
    private double calculateSpiralPotential(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return 0;
        
        double theta = position.angle();
        double diskPot = Math.abs(calculateDiskPotential(position));
        double amplitude = PhysicsConstants.SPIRAL_AMPLITUDE * diskPot;
        
        double phase = PhysicsConstants.SPIRAL_ARMS * theta - 
                      PhysicsConstants.SPIRAL_K * Math.log(r);
        
        return amplitude * Math.cos(phase);
    }
    
    /**
     * Force from spiral arms
     */
    private Vector2D calculateSpiralForce(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return Vector2D.ZERO;
        
        double theta = position.angle();
        double x = position.x();
        double y = position.y();
        
        double diskPot = Math.abs(calculateDiskPotential(position));
        double amplitude = PhysicsConstants.SPIRAL_AMPLITUDE * diskPot;
        
        double m = PhysicsConstants.SPIRAL_ARMS;
        double k = PhysicsConstants.SPIRAL_K;
        double phase = m * theta - k * Math.log(r);
        double sinPhase = Math.sin(phase);
        
        // Radial component: dΦ/dr
        double Fr = amplitude * sinPhase * k / r;
        
        // Tangential component: (1/r) * dΦ/dθ
        double Ftheta = -amplitude * sinPhase * m / r;
        
        // Convert to Cartesian
        double Fx = Fr * (x/r) - Ftheta * (y/r);
        double Fy = Fr * (y/r) + Ftheta * (x/r);
        
        return new Vector2D(-Fx, -Fy);
    }
    
    /**
     * SMBH potential with softening
     * Φ_SMBH = -GM_SMBH / sqrt(r^2 + ε^2)
     */
    private double calculateSMBHPotential(Vector2D position) {
        double r = position.magnitude();
        double softenedR = Math.sqrt(r * r + PhysicsConstants.EPSILON_SMBH * PhysicsConstants.EPSILON_SMBH);
        
        return -PhysicsConstants.G * PhysicsConstants.M_SMBH / softenedR;
    }
    
    /**
     * Force from SMBH
     */
    private Vector2D calculateSMBHForce(Vector2D position) {
        double r = position.magnitude();
        if (r < 1e-10) return Vector2D.ZERO;
        
        double epsilon2 = PhysicsConstants.EPSILON_SMBH * PhysicsConstants.EPSILON_SMBH;
        double softenedR3 = Math.pow(r * r + epsilon2, 1.5);
        
        double forceMag = PhysicsConstants.G * PhysicsConstants.M_SMBH * r / softenedR3;
        
        // Force points toward center
        return position.normalize().multiply(-forceMag);
    }
    
    /**
     * Rotating bar potential (optional)
     * Φ_bar = A_bar * cos(2(θ - Ω_bar*t))
     */
    private double calculateBarPotential(Vector2D position) {
        if (!barEnabled) return 0;
        
        double r = position.magnitude();
        if (r < 1e-10) return 0;
        
        double theta = position.angle();
        double diskPot = Math.abs(calculateDiskPotential(position));
        double amplitude = PhysicsConstants.BAR_AMPLITUDE * diskPot;
        
        // Convert pattern speed from km/s/kpc to rad/year
        double omegaBar = PhysicsConstants.BAR_PATTERN_SPEED / 1000.0;  // Simplified conversion
        double barAngle = 2 * (theta - omegaBar * currentTime);
        
        return amplitude * Math.cos(barAngle);
    }
    
    /**
     * Force from rotating bar
     */
    private Vector2D calculateBarForce(Vector2D position) {
        if (!barEnabled) return Vector2D.ZERO;
        
        double r = position.magnitude();
        if (r < 1e-10) return Vector2D.ZERO;
        
        double theta = position.angle();
        double x = position.x();
        double y = position.y();
        
        double diskPot = Math.abs(calculateDiskPotential(position));
        double amplitude = PhysicsConstants.BAR_AMPLITUDE * diskPot;
        
        double omegaBar = PhysicsConstants.BAR_PATTERN_SPEED / 1000.0;
        double barAngle = 2 * (theta - omegaBar * currentTime);
        double sinBar = Math.sin(barAngle);
        
        // Tangential force component
        double Ftheta = 2 * amplitude * sinBar / r;
        
        // Convert to Cartesian
        double Fx = -Ftheta * (y/r);
        double Fy = Ftheta * (x/r);
        
        return new Vector2D(-Fx, -Fy);
    }
    
    /**
     * Calculate circular velocity at a given radius
     * v_circ = sqrt(r * |dΦ/dr|)
     */
    public double getCircularVelocity(double radius) {
        if (radius < 1e-10) return 0;
        
        Vector2D testPos = new Vector2D(radius, 0);
        Vector2D force = calculateTotalForce(testPos);
        double forceMag = force.magnitude();
        
        return Math.sqrt(forceMag * radius);
    }
    
    /**
     * Update time for rotating components
     */
    public void updateTime(double deltaTime) {
        currentTime += deltaTime;
    }
    
    /**
     * Enable or disable the bar potential
     */
    public void setBarEnabled(boolean enabled) {
        this.barEnabled = enabled;
    }
    
    /**
     * Check if bar is enabled
     */
    public boolean isBarEnabled() {
        return barEnabled;
    }
    
    /**
     * Reset time to zero
     */
    public void resetTime() {
        currentTime = 0;
    }
}
