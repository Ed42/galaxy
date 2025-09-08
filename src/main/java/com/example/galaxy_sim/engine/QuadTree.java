package com.example.galaxy_sim.engine;

import java.util.List;

final class QuadTree {
    private QuadNode root;

    void build(List<Particle> particles) {
        if (particles.isEmpty()) { root = null; return; }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Particle p : particles) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y;
        }
        double cx = 0.5 * (minX + maxX);
        double cy = 0.5 * (minY + maxY);
        double half = 0.5 * Math.max(maxX - minX, maxY - minY);
        half = Math.max(half, Phys.REGION_HALF_SIZE_PC);
        half *= 1.05;
        root = new QuadNode(new Quad(cx, cy, half));
        for (Particle p : particles) root.insert(p);
    }

    void addAcceleration(Particle p) {
        if (root != null) root.addAccel(p);
    }

    // Estimate local density: use mass/area of smallest node containing p (approx).
    double estimateLocalRhoMsunPerPc3(Particle p) {
        QuadNode node = root;
        if (node == null) return 0.0;
        while (node.internal) {
            if (node.nw.quad.contains(p.x, p.y)) node = node.nw;
            else if (node.ne.quad.contains(p.x, p.y)) node = node.ne;
            else if (node.sw.quad.contains(p.x, p.y)) node = node.sw;
            else node = node.se;
            // Stop if child is null (shouldn't happen), break
            if (node == null) break;
        }
        if (node == null) return 0.0;
        double areaPc2 = (node.quad.size()) * (node.quad.size());
        double surfaceDensity = node.mass / areaPc2; // Msun/pc^2
        double rho = surfaceDensity / (2.0 * Phys.DISK_THICKNESS_PC); // Msun/pc^3
        return rho;
    }
}

