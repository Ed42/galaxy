package com.example.galaxy_sim.simulation;

import com.example.galaxy_sim.model.Particle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Simulator {

    private List<Particle> particles;
    private double time;

    public Simulator() {
        this.particles = new ArrayList<>();
        this.time = 0;
    }

    public void init() {
        // To be implemented
    }

    public void step() {
        // To be implemented
    }

    public List<Particle> getParticles() {
        return particles;
    }

    public double getTime() {
        return time;
    }
}
