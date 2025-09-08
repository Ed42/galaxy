package com.example.galaxy_sim.model;

import java.util.List;

public class Quadtree {
    private final int MAX_PARTICLES = 1;
    private final Rectangle boundary;
    private final List<Particle> particles;

    private Quadtree northWest;
    private Quadtree northEast;
    private Quadtree southWest;
    private Quadtree southEast;

    public Quadtree(Rectangle boundary) {
        this.boundary = boundary;
        this.particles = new java.util.ArrayList<>();
    }

    public void insert(Particle p) {
        // To be implemented
    }
}

record Rectangle(double x, double y, double width, double height) {}
