package com.example.galaxy_sim.engine;

final class QuadNode {
    final Quad quad;

    // aggregate
    double mass;
    double comX;
    double comY;

    QuadNode nw, ne, sw, se;
    Particle body; // if leaf with one body
    boolean internal;

    QuadNode(Quad quad) { this.quad = quad; }

    void insert(Particle p) {
        if (mass == 0 && body == null && !internal) {
            body = p;
            mass = p.mass;
            comX = p.x; comY = p.y;
            return;
        }
        if (!internal) subdivide();
        if (body != null) {
            put(body);
            body = null;
        }
        put(p);
        // update aggregate
        double newM = mass + p.mass;
        comX = (comX * mass + p.x * p.mass) / newM;
        comY = (comY * mass + p.y * p.mass) / newM;
        mass = newM;
    }

    private void put(Particle p) {
        if (nw.quad.contains(p.x, p.y)) { nw.insert(p); return; }
        if (ne.quad.contains(p.x, p.y)) { ne.insert(p); return; }
        if (sw.quad.contains(p.x, p.y)) { sw.insert(p); return; }
        if (se.quad.contains(p.x, p.y)) { se.insert(p); return; }
        // Clamp tiny nudge
        double eps = 1e-9;
        p.x = Math.max(Math.min(p.x, quad.cx + quad.h - eps), quad.cx - quad.h + eps);
        p.y = Math.max(Math.min(p.y, quad.cy + quad.h - eps), quad.cy - quad.h + eps);
        if (nw.quad.contains(p.x, p.y)) nw.insert(p);
        else if (ne.quad.contains(p.x, p.y)) ne.insert(p);
        else if (sw.quad.contains(p.x, p.y)) sw.insert(p);
        else se.insert(p);
    }

    private void subdivide() {
        internal = true;
        nw = new QuadNode(quad.nw());
        ne = new QuadNode(quad.ne());
        sw = new QuadNode(quad.sw());
        se = new QuadNode(quad.se());
    }

    void addAccel(Particle p) {
        if (mass == 0) return;
        if (!internal) {
            if (body == null || body == p) return;
            addAccelFromPointMass(p, body.mass, body.x, body.y);
            return;
        }
        double dx = comX - p.x;
        double dy = comY - p.y;
        double r2 = dx*dx + dy*dy + Phys.SOFTENING_PC*Phys.SOFTENING_PC;
        double r = Math.sqrt(r2);
        if ((quad.size()) / r < Phys.BH_THETA) {
            addAccelFromPointMass(p, mass, comX, comY);
        } else {
            nw.addAccel(p);
            ne.addAccel(p);
            sw.addAccel(p);
            se.addAccel(p);
        }
    }

    static void addAccelFromPointMass(Particle p, double massMsun, double ox, double oy) {
        double dx = ox - p.x;
        double dy = oy - p.y;
        double r2 = dx*dx + dy*dy + Phys.SOFTENING_PC*Phys.SOFTENING_PC;
        double invR = 1.0 / Math.sqrt(r2);
        double invR3 = invR * invR * invR;
        double a = Phys.G * massMsun * invR3;
        p.ax += a * dx;
        p.ay += a * dy;
    }
}

