package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import java.util.ArrayList;
import java.util.List;

/**
 * Barnes-Hut Quadtree implementation for efficient N-body force calculations.
 * Uses θ = 0.7 as the opening angle criterion for approximations.
 */
public class Quadtree {
    private static final double THETA = 0.7; // Opening angle criterion
    private static final int MAX_PARTICLES_PER_NODE = 1;

    private final double x, y, size; // Center and half-width of this node
    private double totalMass = 0;
    private double centerOfMassX = 0;
    private double centerOfMassY = 0;
    private List<Particle> particles = new ArrayList<>();

    // Child quadrants: NW, NE, SW, SE
    private Quadtree[] children = new Quadtree[4];
    private boolean hasChildren = false;

    public Quadtree(double centerX, double centerY, double halfSize) {
        this.x = centerX;
        this.y = centerY;
        this.size = halfSize;
    }

    /**
     * Inserts a particle into the quadtree
     */
    public void insert(Particle particle) {
        // Check if particle is within bounds
        if (!contains(particle.x(), particle.y())) {
            return;
        }

        totalMass += particle.mass();

        // Update center of mass
        if (totalMass > 0) {
            centerOfMassX = (centerOfMassX * (totalMass - particle.mass()) + particle.x() * particle.mass()) / totalMass;
            centerOfMassY = (centerOfMassY * (totalMass - particle.mass()) + particle.y() * particle.mass()) / totalMass;
        }

        if (!hasChildren && particles.size() < MAX_PARTICLES_PER_NODE) {
            // Add particle to this leaf node
            particles.add(particle);
        } else {
            // Need to subdivide or add to existing children
            if (!hasChildren) {
                subdivide();
                // Move existing particles to children
                for (Particle p : particles) {
                    addToChild(p);
                }
                particles.clear();
            }
            addToChild(particle);
        }
    }

    /**
     * Subdivides this node into four quadrants
     */
    private void subdivide() {
        double quarterSize = size / 2;
        children[0] = new Quadtree(x - quarterSize, y + quarterSize, quarterSize); // NW
        children[1] = new Quadtree(x + quarterSize, y + quarterSize, quarterSize); // NE
        children[2] = new Quadtree(x - quarterSize, y - quarterSize, quarterSize); // SW
        children[3] = new Quadtree(x + quarterSize, y - quarterSize, quarterSize); // SE
        hasChildren = true;
    }

    /**
     * Adds a particle to the appropriate child quadrant
     */
    private void addToChild(Particle particle) {
        for (Quadtree child : children) {
            if (child.contains(particle.x(), particle.y())) {
                child.insert(particle);
                break;
            }
        }
    }

    /**
     * Checks if a point is contained within this node
     */
    private boolean contains(double px, double py) {
        return px >= x - size && px < x + size && py >= y - size && py < y + size;
    }

    /**
     * Calculates gravitational force on a particle using Barnes-Hut approximation
     */
    public Force calculateForce(Particle particle) {
        if (totalMass == 0) {
            return new Force(0, 0);
        }

        double dx = centerOfMassX - particle.x();
        double dy = centerOfMassY - particle.y();
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Skip self-interaction
        if (distance < 1e-10) {
            return new Force(0, 0);
        }

        // Barnes-Hut criterion: if s/d < θ, treat as single body
        if (!hasChildren || size / distance < THETA) {
            return calculateDirectForce(particle, centerOfMassX, centerOfMassY, totalMass);
        } else {
            // Recursively calculate force from children
            Force totalForce = new Force(0, 0);
            for (Quadtree child : children) {
                if (child != null) {
                    Force childForce = child.calculateForce(particle);
                    totalForce = totalForce.add(childForce);
                }
            }
            return totalForce;
        }
    }

    /**
     * Calculates direct gravitational force between particle and a mass at given position
     */
    private Force calculateDirectForce(Particle particle, double massX, double massY, double mass) {
        double dx = massX - particle.x();
        double dy = massY - particle.y();
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < 1e-10) {
            return new Force(0, 0);
        }

        // Apply gravitational softening
        double softening = particle.softeningParameter();
        double softenedDistance = Math.sqrt(distance * distance + softening * softening);

        // F = G * m1 * m2 / r^2, G = 4.302 × 10^-3 pc (M_sun)^-1 (km/s)^2
        final double G = 4.302e-3;
        double forceMagnitude = G * particle.mass() * mass / (softenedDistance * softenedDistance);

        // Unit vector components
        double ux = dx / distance;
        double uy = dy / distance;

        return new Force(forceMagnitude * ux, forceMagnitude * uy);
    }

    /**
     * Returns all particles in this quadtree (for visualization)
     */
    public List<Particle> getAllParticles() {
        List<Particle> allParticles = new ArrayList<>();
        if (hasChildren) {
            for (Quadtree child : children) {
                if (child != null) {
                    allParticles.addAll(child.getAllParticles());
                }
            }
        } else {
            allParticles.addAll(particles);
        }
        return allParticles;
    }

    /**
     * Returns the local density at this node for adaptive time-stepping
     */
    public double getDensity() {
        double volume = 4.0 * size * size; // 2D area
        return totalMass / volume;
    }

    /**
     * Returns bounds for visualization
     */
    public record Bounds(double x, double y, double size) {}

    /**
     * Gets all node bounds for quadtree visualization overlay
     */
    public List<Bounds> getAllBounds() {
        List<Bounds> bounds = new ArrayList<>();
        bounds.add(new Bounds(x, y, size));

        if (hasChildren) {
            for (Quadtree child : children) {
                if (child != null) {
                    bounds.addAll(child.getAllBounds());
                }
            }
        }
        return bounds;
    }

    /**
     * Represents a 2D force vector
     */
    public record Force(double fx, double fy) {
        public Force add(Force other) {
            return new Force(fx + other.fx, fy + other.fy);
        }

        public double magnitude() {
            return Math.sqrt(fx * fx + fy * fy);
        }
    }
}
