package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;

/**
 * Calculates the total background gravitational potential of the galaxy.
 * This includes components for the disk, bulge, halo, SMBH, and a rotating spiral arm pattern.
 */
public class BackgroundPotential {
	// Physical Constants
	private static final double G = 4.30091e-3;

	// Galaxy Model Parameters
	private static final double M_DISK = 1.0e11;
	private static final double A_DISK = 5000.0;
	private static final double B_DISK = 300.0;
	private static final double M_BULGE = 1.0e10;
	private static final double A_BULGE = 1000.0;
	private static final double V_HALO = 200.0;
	private double M_SMBH = 4.0e6;
	private static final double EPSILON_SMBH = 1.0;

	// Spiral Arm Parameters
	private static final double K_SPIRAL = 2.0 / Math.tan(12.0 * Math.PI / 180.0);
	private static final double AMP_SPIRAL = 0.1;
	private static final double OMEGA_PATTERN = 0.025; // rad/Myr
	private double nSpiral = 2.0;

	// Bar Potential Parameters
	private boolean barEnabled = false;
	private static final double AMP_BAR = 0.1; // Bar strength as a fraction of disk acceleration
	private static final double OMEGA_BAR = 0.04; // Bar pattern speed in rad/Myr

	public Quadtree.Force calculateBackgroundForce(Particle p, double currentTimeMyr) {
		Quadtree.Force axisymmetricForce = calculateAxisymmetricForce(p);
		Quadtree.Force spiralForce = calculateSpiralArmForce(p, currentTimeMyr);
		Quadtree.Force barForce = this.barEnabled ? calculateBarForce(p, currentTimeMyr) : new Quadtree.Force(0, 0);

		return axisymmetricForce.add(spiralForce).add(barForce);
	}

	public Quadtree.Force calculateAxisymmetricForce(Particle p) {
		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y());
		if (r < 1e-9) return new Quadtree.Force(0, 0);
		double ux = p.x() / r, uy = p.y() / r;
		double accel_disk = diskAcceleration(r);
		double accel_bulge = bulgeAcceleration(r);
		double accel_halo = haloAcceleration(r);
		double accel_smbh = smbhAcceleration(r);
		double total_accel_mag = -(accel_disk + accel_bulge + accel_halo + accel_smbh);
		double force_mag = total_accel_mag * p.mass();
		return new Quadtree.Force(force_mag * ux, force_mag * uy);
	}

	private double diskAcceleration(double r) {
		double term = A_DISK + B_DISK;
		return (G * M_DISK * r) / Math.pow(r * r + term * term, 1.5);
	}

	private double bulgeAcceleration(double r) {
		double term = r + A_BULGE;
		return (G * M_BULGE) / (term * term);
	}

	private double haloAcceleration(double r) {
		return (V_HALO * V_HALO) / r;
	}

	private double smbhAcceleration(double r) {
		return (G * M_SMBH) / (r * r + EPSILON_SMBH * EPSILON_SMBH);
	}

	private Quadtree.Force calculateSpiralArmForce(Particle p, double currentTimeMyr) {
		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y());
		if (r < 1e-9) return new Quadtree.Force(0, 0);
		double theta = Math.atan2(p.y(), p.x());
		double A = AMP_SPIRAL * diskAcceleration(r) * r;
		// Corrected: Use currentTimeMyr directly as OMEGA_PATTERN is in rad/Myr
		double phase = this.nSpiral * (theta - OMEGA_PATTERN * currentTimeMyr) - K_SPIRAL * Math.log(r / A_DISK);
		double Fr = -A * (K_SPIRAL / r) * Math.sin(phase);
		double F_theta = A * (this.nSpiral / r) * Math.sin(phase);
		double fx = Fr * Math.cos(theta) - F_theta * Math.sin(theta);
		double fy = Fr * Math.sin(theta) + F_theta * Math.cos(theta);
		return new Quadtree.Force(fx * p.mass(), fy * p.mass());
	}

	private Quadtree.Force calculateBarForce(Particle p, double currentTimeMyr) {
		double r = Math.sqrt(p.x() * p.x() + p.y() * p.y());
		if (r < 1e-9 || r > A_DISK) return new Quadtree.Force(0, 0); // Bar is confined to inner disk

		double theta = Math.atan2(p.y(), p.x());
		// Corrected: Use currentTimeMyr directly as OMEGA_BAR is in rad/Myr
		double barAngle = OMEGA_BAR * currentTimeMyr;
		double phase = 2 * (theta - barAngle);

		// A simple but effective bar model that applies a force proportional to the disk's gravity
		double barStrength = diskAcceleration(r) * AMP_BAR * Math.pow(1 - (r/A_DISK), 2);

		// Components of the bar force in polar coordinates
		double Fr_bar = -barStrength * Math.cos(phase);
		double F_theta_bar = barStrength * Math.sin(phase);

		// Convert polar force components to Cartesian
		double fx = Fr_bar * Math.cos(theta) - F_theta_bar * Math.sin(theta);
		double fy = Fr_bar * Math.sin(theta) + F_theta_bar * Math.cos(theta);

		return new Quadtree.Force(fx * p.mass(), fy * p.mass());
	}

	public void setBarEnabled(boolean enabled) {
		this.barEnabled = enabled;
	}

	public void setNumberOfArms(int n) {
		if (n > 0) {
			this.nSpiral = n;
		}
	}

	public void setSmbhMass(double mass) {
		if (mass > 0) {
			this.M_SMBH = mass;
		}
	}
}
