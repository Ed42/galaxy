package com.example.galaxy_sim.service;

import org.springframework.stereotype.Service;

import com.example.galaxy_sim.engine.Particle;
import com.example.galaxy_sim.engine.SimulationEngine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

@Service
public class SimulationService {
    private SimulationEngine engine;

    @PostConstruct
    public void init() {
        int cores = Runtime.getRuntime().availableProcessors();
        engine = new SimulationEngine(10_000, Math.max(1, cores - 1));
        engine.start();
    }

    @PreDestroy
    public void shutdown() {
        if (engine != null) engine.stop();
    }

    public void pause(boolean paused) { engine.setPaused(paused); }
    public void togglePause() { engine.togglePause(); }
    public boolean isPaused() { return engine.isPaused(); }

    public void reset(int n) { engine.reset(n); }

    public void setYearsPerSecond(double yps) { engine.setYearsPerSecond(yps); }
    public double getYearsPerSecond() { return engine.getYearsPerSecond(); }

    public void setBarEnabled(boolean enabled) { engine.setBarEnabled(enabled); }
    public boolean isBarEnabled() { return engine.isBarEnabled(); }

    public double getTimeYears() { return engine.getTimeYears(); }
    public long getSteps() { return engine.getSteps(); }

    public double getEnergyApprox() { return engine.getEnergyApprox(); }

    public List<Particle> particles() { return engine.getParticles(); }
}

