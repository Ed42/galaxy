package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import java.util.ArrayList;
import java.util.List;

/**
 * Barnes-Hut Quadtree implementation for efficient N-body force calculations.
 */
public class Quadtree {
	private final double x, y, size; // Center and half-width of this node
	private double totalMass = 0;
	private double centerOfMassX = 0;
	private double centerOfMassY = 0;
	private Particle particleInNode; // If it's a leaf node with one particle

	// Child quadrants: NW, NE, SW, SE
	private Quadtree[] children = new Quadtree[4];
	private boolean isLeaf = true;

	public Quadtree(double centerX, double centerY, double halfSize) {
		this.x = centerX;
		this.y = centerY;
		this.size = halfSize;
	}

	/**
	 * Inserts a particle into the quadtree.
	 */
	public void insert(Particle p) {
		if (!contains(p.x(), p.y())) {
			return;
		}

		if (isLeaf) {
			if (particleInNode == null) {
				// This leaf is empty, store the particle here.
				particleInNode = p;
				totalMass = p.mass();
				centerOfMassX = p.x();
				centerOfMassY = p.y();
				return;
			} else {
				// This leaf is occupied, we must subdivide.
				subdivide();
				// Re-insert the original particle into the correct child.
				addToChild(particleInNode);
				particleInNode = null; // No longer a leaf with a single particle.
			}
		}

		// Add the new particle to the correct child.
		addToChild(p);
		// Update this node's center of mass.
		updateCenterOfMass(p);
	}

	private void updateCenterOfMass(Particle p) {
		centerOfMassX = (centerOfMassX * totalMass + p.x() * p.mass()) / (totalMass + p.mass());
		centerOfMassY = (centerOfMassY * totalMass + p.y() * p.mass()) / (totalMass + p.mass());
		totalMass += p.mass();
	}

	private void subdivide() {
		double quarterSize = size / 2;
		children[0] = new Quadtree(x - quarterSize, y - quarterSize, quarterSize); // SW
		children[1] = new Quadtree(x + quarterSize, y - quarterSize, quarterSize); // SE
		children[2] = new Quadtree(x - quarterSize, y + quarterSize, quarterSize); // NW
		children[3] = new Quadtree(x + quarterSize, y + quarterSize, quarterSize); // NE
		isLeaf = false;
	}

	private void addToChild(Particle p) {
		for (Quadtree child : children) {
			if (child.contains(p.x(), p.y())) {
				child.insert(p);
				return;
			}
		}
	}

	private boolean contains(double px, double py) {
		return px >= x - size && px <= x + size && py >= y - size && py <= y + size;
	}

	/**
	 * Calculates gravitational force on a particle using Barnes-Hut approximation.
	 */
	public Force calculateForce(Particle p, double G, double theta, double softening) {
		Force totalForce = new Force(0, 0);

		if (isLeaf) {
			if (particleInNode != null && particleInNode != p) {
				totalForce = totalForce.add(calculateDirectForce(p, particleInNode, G, softening));
			}
		} else {
			double dx = centerOfMassX - p.x();
			double dy = centerOfMassY - p.y();
			double distance = Math.sqrt(dx * dx + dy * dy);

			// If node is far enough away, approximate it as a single mass.
			if ((2 * size) / distance < theta) {
				totalForce = totalForce.add(calculateDirectForce(p, totalMass, centerOfMassX, centerOfMassY, G, softening));
			} else {
				// Otherwise, recurse into children.
				for (Quadtree child : children) {
					if (child.totalMass > 0) {
						totalForce = totalForce.add(child.calculateForce(p, G, theta, softening));
					}
				}
			}
		}
		return totalForce;
	}

	/**
	 * Calculates potential energy on a particle.
	 */
	public double calculatePotential(Particle p, double G, double theta, double softening) {
		double totalPotential = 0.0;

		if (isLeaf) {
			if (particleInNode != null && particleInNode != p) {
				double dx = particleInNode.x() - p.x();
				double dy = particleInNode.y() - p.y();
				double softenedDist = Math.sqrt(dx * dx + dy * dy + softening * softening);
				totalPotential -= G * p.mass() * particleInNode.mass() / softenedDist;
			}
		} else {
			double dx = centerOfMassX - p.x();
			double dy = centerOfMassY - p.y();
			double distance = Math.sqrt(dx * dx + dy * dy);

			if ((2 * size) / distance < theta) {
				double softenedDist = Math.sqrt(distance * distance + softening * softening);
				totalPotential -= G * p.mass() * totalMass / softenedDist;
			} else {
				for (Quadtree child : children) {
					if (child.totalMass > 0) {
						totalPotential += child.calculatePotential(p, G, theta, softening);
					}
				}
			}
		}
		return totalPotential;
	}

	private Force calculateDirectForce(Particle p1, Particle p2, double G, double softening) {
		return calculateDirectForce(p1, p2.mass(), p2.x(), p2.y(), G, softening);
	}

	private Force calculateDirectForce(Particle p, double mass, double massX, double massY, double G, double softening) {
		double dx = massX - p.x();
		double dy = massY - p.y();
		double distSq = dx * dx + dy * dy;
		double dist = Math.sqrt(distSq);

		// Avoid self-interaction and division by zero
		if (dist < 1e-9) {
			return new Force(0, 0);
		}

		double softenedDistSq = distSq + softening * softening;
		double forceMag = (G * p.mass() * mass) / softenedDistSq;

		return new Force(forceMag * dx / dist, forceMag * dy / dist);
	}

	/** Represents a 2D force vector. */
	public record Force(double fx, double fy) {
		public Force add(Force other) {
			return new Force(fx + other.fx, fy + other.fy);
		}

		public double magnitude() {
			return Math.sqrt(fx * fx + fy * fy);
		}
	}
}
