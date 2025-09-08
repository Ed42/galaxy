package com.galaxy.sim.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Quadtree implementation for Barnes-Hut algorithm.
 * Efficiently computes gravitational forces by treating distant groups of particles
 * as single masses.
 */
public class Quadtree {
    
    private static class Node {
        // Boundary of this node
        private final double xMin, xMax, yMin, yMax;
        private final double width, height;
        private final Vector2D center;
        
        // Children nodes (NW, NE, SW, SE)
        private Node[] children;
        
        // Particle data
        private Particle particle;  // Single particle if leaf node
        private final List<Particle> particles;  // All particles in this node
        
        // Center of mass data
        private double totalMass;
        private Vector2D centerOfMass;
        
        // Node state
        private boolean isLeaf;
        private boolean isEmpty;
        
        public Node(double xMin, double xMax, double yMin, double yMax) {
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
            this.width = xMax - xMin;
            this.height = yMax - yMin;
            this.center = new Vector2D((xMin + xMax) / 2, (yMin + yMax) / 2);
            this.particles = new ArrayList<>();
            this.children = null;
            this.isLeaf = true;
            this.isEmpty = true;
            this.totalMass = 0;
            this.centerOfMass = Vector2D.ZERO;
        }
        
        /**
         * Check if a position is within this node's boundaries
         */
        public boolean contains(Vector2D position) {
            return position.x() >= xMin && position.x() < xMax &&
                   position.y() >= yMin && position.y() < yMax;
        }
        
        /**
         * Insert a particle into this node
         */
        public void insert(Particle p) {
            if (!contains(p.position())) {
                return;  // Particle is outside bounds
            }
            
            particles.add(p);
            updateCenterOfMass(p);
            
            if (isEmpty) {
                // First particle in this node
                particle = p;
                isEmpty = false;
                isLeaf = true;
            } else if (isLeaf && particle != null) {
                // Need to subdivide
                subdivide();
                
                // Re-insert existing particle into children
                insertIntoChildren(particle);
                particle = null;
                
                // Insert new particle into children
                insertIntoChildren(p);
                
                isLeaf = false;
            } else if (!isLeaf) {
                // Already subdivided, insert into children
                insertIntoChildren(p);
            }
        }
        
        /**
         * Update center of mass with new particle
         */
        private void updateCenterOfMass(Particle p) {
            if (totalMass == 0) {
                centerOfMass = p.position();
                totalMass = p.mass();
            } else {
                double newMass = totalMass + p.mass();
                double wx = (centerOfMass.x() * totalMass + p.position().x() * p.mass()) / newMass;
                double wy = (centerOfMass.y() * totalMass + p.position().y() * p.mass()) / newMass;
                centerOfMass = new Vector2D(wx, wy);
                totalMass = newMass;
            }
        }
        
        /**
         * Create four child nodes
         */
        private void subdivide() {
            double midX = (xMin + xMax) / 2;
            double midY = (yMin + yMax) / 2;
            
            children = new Node[4];
            children[0] = new Node(xMin, midX, midY, yMax);  // NW
            children[1] = new Node(midX, xMax, midY, yMax);  // NE
            children[2] = new Node(xMin, midX, yMin, midY);  // SW
            children[3] = new Node(midX, xMax, yMin, midY);  // SE
        }
        
        /**
         * Insert particle into appropriate child
         */
        private void insertIntoChildren(Particle p) {
            for (Node child : children) {
                if (child.contains(p.position())) {
                    child.insert(p);
                    break;
                }
            }
        }
        
        /**
         * Calculate force on a particle using Barnes-Hut approximation
         */
        public Vector2D calculateForce(Particle p, double theta) {
            if (isEmpty) {
                return Vector2D.ZERO;
            }
            
            // Don't calculate force on self
            if (isLeaf && particle != null && particle.id() == p.id()) {
                return Vector2D.ZERO;
            }
            
            Vector2D diff = centerOfMass.subtract(p.position());
            double distance = diff.magnitude();
            
            // Use softening to avoid singularities
            double softening = (isLeaf && particle != null && particle.type() == Particle.ParticleType.SMBH) 
                ? PhysicsConstants.EPSILON_SMBH 
                : PhysicsConstants.EPSILON_PARTICLE;
            double softenedDist = Math.sqrt(distance * distance + softening * softening);
            
            // Check if we can use center of mass approximation
            double ratio = width / distance;
            
            if (isLeaf || ratio < theta) {
                // Use this node's center of mass
                double forceMag = PhysicsConstants.G * totalMass / (softenedDist * softenedDist * softenedDist);
                return diff.multiply(forceMag);
            } else {
                // Need to go deeper into children
                Vector2D totalForce = Vector2D.ZERO;
                for (Node child : children) {
                    if (child != null) {
                        totalForce = totalForce.add(child.calculateForce(p, theta));
                    }
                }
                return totalForce;
            }
        }
        
        /**
         * Get all particles in this node and its children
         */
        public List<Particle> getAllParticles() {
            return new ArrayList<>(particles);
        }
        
        /**
         * Get quadtree structure for visualization
         */
        public void getQuadtreeLines(List<double[]> lines) {
            // Add lines for this node's boundaries
            lines.add(new double[]{xMin, yMin, xMax, yMin});  // Bottom
            lines.add(new double[]{xMax, yMin, xMax, yMax});  // Right
            lines.add(new double[]{xMax, yMax, xMin, yMax});  // Top
            lines.add(new double[]{xMin, yMax, xMin, yMin});  // Left
            
            // Recursively add children's boundaries
            if (!isLeaf && children != null) {
                for (Node child : children) {
                    if (child != null && !child.isEmpty) {
                        child.getQuadtreeLines(lines);
                    }
                }
            }
        }
    }
    
    private Node root;
    private final double bounds;
    
    /**
     * Create a new quadtree with specified bounds
     */
    public Quadtree(double bounds) {
        this.bounds = bounds;
        this.root = new Node(-bounds, bounds, -bounds, bounds);
    }
    
    /**
     * Clear and rebuild the quadtree
     */
    public void rebuild() {
        this.root = new Node(-bounds, bounds, -bounds, bounds);
    }
    
    /**
     * Insert a particle into the quadtree
     */
    public void insert(Particle particle) {
        root.insert(particle);
    }
    
    /**
     * Insert multiple particles
     */
    public void insertAll(List<Particle> particles) {
        for (Particle p : particles) {
            insert(p);
        }
    }
    
    /**
     * Calculate gravitational force on a particle
     */
    public Vector2D calculateForce(Particle particle) {
        return root.calculateForce(particle, PhysicsConstants.THETA);
    }
    
    /**
     * Get all particles in the quadtree
     */
    public List<Particle> getAllParticles() {
        return root.getAllParticles();
    }
    
    /**
     * Get total mass in the quadtree
     */
    public double getTotalMass() {
        return root.totalMass;
    }
    
    /**
     * Get center of mass of all particles
     */
    public Vector2D getCenterOfMass() {
        return root.centerOfMass;
    }
    
    /**
     * Get quadtree structure for visualization (list of line segments)
     */
    public List<double[]> getQuadtreeLines() {
        List<double[]> lines = new ArrayList<>();
        root.getQuadtreeLines(lines);
        return lines;
    }
    
    /**
     * Calculate local density at a position using quadtree
     */
    public double getLocalDensity(Vector2D position, double radius) {
        return getLocalDensityRecursive(root, position, radius);
    }
    
    private double getLocalDensityRecursive(Node node, Vector2D position, double radius) {
        if (node == null || node.isEmpty) {
            return 0;
        }
        
        // Check if node is completely outside search radius
        double distToNode = node.center.distanceTo(position);
        if (distToNode - node.width / Math.sqrt(2) > radius) {
            return 0;
        }
        
        // If node is small enough or is a leaf, count particles directly
        if (node.isLeaf || node.width < radius / 2) {
            double mass = 0;
            for (Particle p : node.particles) {
                if (p.position().distanceTo(position) <= radius) {
                    mass += p.mass();
                }
            }
            return mass / (Math.PI * radius * radius);
        }
        
        // Otherwise, recurse into children
        double totalDensity = 0;
        if (node.children != null) {
            for (Node child : node.children) {
                totalDensity += getLocalDensityRecursive(child, position, radius);
            }
        }
        return totalDensity;
    }
}
