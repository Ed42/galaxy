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

	private Quadtree[] children = new Quadtree[4];
	private boolean isLeaf = true;

	/** Represents the boundary of a quadtree node for visualization. */
	public record Bounds(double x, double y, double size) {}

	public Quadtree(double centerX, double centerY, double halfSize) {
		this.x = centerX;
		this.y = centerY;
		this.size = halfSize;
	}

	public void insert(Particle p) {
		if (!contains(p.x(), p.y())) return;
		if (isLeaf) {
			if (particleInNode == null) {
				particleInNode = p;
				totalMass = p.mass();
				centerOfMassX = p.x();
				centerOfMassY = p.y();
				return;
			} else {
				subdivide();
				addToChild(particleInNode);
				particleInNode = null;
			}
		}
		addToChild(p);
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
			if ((2 * size) / distance < theta) {
				totalForce = totalForce.add(calculateDirectForce(p, totalMass, centerOfMassX, centerOfMassY, G, softening));
			} else {
				for (Quadtree child : children) {
					if (child.totalMass > 0) {
						totalForce = totalForce.add(child.calculateForce(p, G, theta, softening));
					}
				}
			}
		}
		return totalForce;
	}

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
		if (dist < 1e-9) return new Force(0, 0);
		double softenedDistSq = distSq + softening * softening;
		double forceMag = (G * p.mass() * mass) / softenedDistSq;
		return new Force(forceMag * dx / dist, forceMag * dy / dist);
	}

	/**
	 * Recursively collects the boundaries of all quadtree nodes up to a max depth.
	 */
	public List<Bounds> getBounds(int maxDepth) {
		List<Bounds> bounds = new ArrayList<>();
		getBoundsRecursive(bounds, 0, maxDepth);
		return bounds;
	}

	private void getBoundsRecursive(List<Bounds> bounds, int currentDepth, int maxDepth) {
		if (currentDepth > maxDepth) {
			return;
		}
		bounds.add(new Bounds(this.x, this.y, this.size));
		if (!isLeaf) {
			for (Quadtree child : children) {
				child.getBoundsRecursive(bounds, currentDepth + 1, maxDepth);
			}
		}
	}

	public record Force(double fx, double fy) {
		public Force add(Force other) {
			return new Force(fx + other.fx, fy + other.fy);
		}
		public double magnitude() {
			return Math.sqrt(fx * fx + fy * fy);
		}
	}
}
