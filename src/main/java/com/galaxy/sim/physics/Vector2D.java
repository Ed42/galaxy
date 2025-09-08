package com.galaxy.sim.physics;

/**
 * Immutable 2D vector for positions and velocities in the galaxy simulation.
 * All coordinates are in parsecs (pc) for positions and km/s for velocities.
 */
public record Vector2D(double x, double y) {
    
    /**
     * Zero vector constant
     */
    public static final Vector2D ZERO = new Vector2D(0, 0);
    
    /**
     * Add another vector to this vector
     */
    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }
    
    /**
     * Subtract another vector from this vector
     */
    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }
    
    /**
     * Multiply this vector by a scalar
     */
    public Vector2D multiply(double scalar) {
        return new Vector2D(x * scalar, y * scalar);
    }
    
    /**
     * Divide this vector by a scalar
     */
    public Vector2D divide(double scalar) {
        return new Vector2D(x / scalar, y / scalar);
    }
    
    /**
     * Calculate the magnitude (length) of this vector
     */
    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }
    
    /**
     * Calculate the squared magnitude (avoids sqrt for performance)
     */
    public double magnitudeSquared() {
        return x * x + y * y;
    }
    
    /**
     * Get a normalized (unit) vector in the same direction
     */
    public Vector2D normalize() {
        double mag = magnitude();
        if (mag == 0) return ZERO;
        return divide(mag);
    }
    
    /**
     * Calculate the dot product with another vector
     */
    public double dot(Vector2D other) {
        return x * other.x + y * other.y;
    }
    
    /**
     * Calculate the distance to another vector
     */
    public double distanceTo(Vector2D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Calculate the squared distance to another vector (avoids sqrt)
     */
    public double distanceSquaredTo(Vector2D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }
    
    /**
     * Get the angle in radians (from -π to π)
     */
    public double angle() {
        return Math.atan2(y, x);
    }
    
    /**
     * Create a vector from polar coordinates
     */
    public static Vector2D fromPolar(double r, double theta) {
        return new Vector2D(r * Math.cos(theta), r * Math.sin(theta));
    }
}
