package com.example.galaxy_sim.controller;

import com.example.galaxy_sim.model.Particle;
import com.example.galaxy_sim.simulation.Simulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SimulationController {

    @Autowired
    private Simulator simulator;

    @GetMapping("/api/particles")
    public List<Particle> getParticles() {
        return simulator.getParticles();
    }

    @GetMapping("/api/time")
    public double getTime() {
        return simulator.getTime();
    }
}
