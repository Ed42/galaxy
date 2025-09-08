package com.example.galaxy_sim.engine;

final class Quad {
    final double cx; // center x (pc)
    final double cy; // center y (pc)
    final double h;  // half-size (pc)

    Quad(double cx, double cy, double h) {
        this.cx = cx; this.cy = cy; this.h = h;
    }

    boolean contains(double x, double y) {
        return x >= cx - h && x < cx + h && y >= cy - h && y < cy + h;
    }

    Quad nw() { return new Quad(cx - h/2, cy + h/2, h/2); }
    Quad ne() { return new Quad(cx + h/2, cy + h/2, h/2); }
    Quad sw() { return new Quad(cx - h/2, cy - h/2, h/2); }
    Quad se() { return new Quad(cx + h/2, cy - h/2, h/2); }

    double size() { return 2*h; }
}

