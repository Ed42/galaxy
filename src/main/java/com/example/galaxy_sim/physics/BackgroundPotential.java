package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;

/**
 * Calculates the total background gravitational potential of the galaxy.
 * This includes components for the disk, bulge, halo, and spiral arms.
 */
public class BackgroundPotential {
	// Physical Constants
	private static final double G = 4.30091e-3; // pc*(km/s)^2 / M_solar

	// Galaxy Model Parameters from project requirements
	// Miyamoto-Nagai Disk
	private static final double M_DISK = 1.0e11; // Solar masses
	private static final double A_DISK = 5000.0; // pc
	private static final double B_DISK = 300.0;  // pc

	// Hernquist Bulge
	private static final double M_BULGE = 1.0e10; // Solar masses
	private static final double A_BULGE = 1000.0; // pc

	// Isothermal Halo
	private static final double V_HALO = 200.0; // km/s

	// Logarithmic Spiral Arms
	private static final double K_SPIRAL = 2.0 / Math.tan(12.0 * Math.PI / 180.0); // For ~12 degree pitch angle
	private static final double N_SPIRAL = 2.0; // Number of arms
	private static final double AMP_SPIRAL = 0.1; // 10% of disk potential

	private boolean barEnabled = false;

	/**
	 * Calculates the total background force on a particle, including non-axisymmetric components.
	 */
	public Quadtree.Force calculateBackgroundForce(Particle p) {
		Quadtree.Force axisymmetricForce = calculateAxisymmetricForce(p);
		Quadtree.Force spiralForce = calculateSpiralArmForce(p);

		return axisymmetricForce.add(spiralForce);
	}

	public Quadtree.Force calculateAxisymmetricForce(Particle p) {
		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y());
		if (r < 1e-9) return new Quadtree.Force(0, 0);

		// Unit vector for radial direction
		double ux = p.x() / r;
		double uy = p.y() / r;

		// Calculate magnitude of acceleration from each component
		double accel_disk = diskAcceleration(r);
		double accel_bulge = bulgeAcceleration(r);
		double accel_halo = haloAcceleration(r);

		// ** THE FIX IS HERE **
		// The total acceleration must be negative to be attractive (pointing towards the center).
		double total_accel_mag = -(accel_disk + accel_bulge + accel_halo);

		// Convert total radial acceleration to force vector
		double force_mag = total_accel_mag * p.mass();

		return new Quadtree.Force(force_mag * ux, force_mag * uy);
	}
	private double diskAcceleration(double r) {
		// Force from a Miyamoto-Nagai disk potential in the plane (z=0)
		double term = A_DISK + B_DISK;
		return (G * M_DISK * r) / Math.pow(r * r + term * term, 1.5);
	}

	private double bulgeAcceleration(double r) {
		// Force from a Hernquist potential
		double term = r + A_BULGE;
		return (G * M_BULGE) / (term * term);
	}

	private double haloAcceleration(double r) {
		// Force from an isothermal halo potential
		return (V_HALO * V_HALO) / r;
	}

	private Quadtree.Force calculateSpiralArmForce(Particle p) {
		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y());
		if (r < 1e-9) return new Quadtree.Force(0, 0);

		double theta = Math.atan2(p.y(), p.x());

		// Amplitude is a fraction of the disk's acceleration at that radius
		double A = AMP_SPIRAL * diskAcceleration(r) * r;

		double phase = N_SPIRAL * theta - K_SPIRAL * Math.log(r / A_DISK);

		// Forces are derived from the potential F = -∇Φ
		double Fr = -A * (K_SPIRAL / r) * Math.sin(phase);
		double F_theta = A * (N_SPIRAL / r) * Math.sin(phase);

		// Convert cylindrical forces to cartesian
		double fx = Fr * Math.cos(theta) - F_theta * Math.sin(theta);
		double fy = Fr * Math.sin(theta) + F_theta * Math.cos(theta);

		return new Quadtree.Force(fx * p.mass(), fy * p.mass());
	}

	public void setBarEnabled(boolean enabled) {
		this.barEnabled = enabled;
	}
}
