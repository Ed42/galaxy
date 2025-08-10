package com.example.galaxy_sim.web;

import com.example.galaxy_sim.simulation.SimulationService;
import com.example.galaxy_sim.simulation.Simulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class SimulationController {

	private final SimulationService simulationService;
	private final Simulator simulator;

	@Autowired
	public SimulationController(SimulationService simulationService, Simulator simulator) {
		this.simulationService = simulationService;
		this.simulator = simulator;
	}

	@GetMapping("/")
	public String index(Model model) {
		return "index";
	}

	@PostMapping("/api/start") @ResponseBody
	public Map<String, Object> startSimulation() {
		simulationService.start();
		return Map.of("status", "started");
	}

	@PostMapping("/api/pause") @ResponseBody
	public Map<String, Object> pauseSimulation() {
		simulationService.pause();
		return Map.of("status", "paused");
	}

	@PostMapping("/api/reset") @ResponseBody
	public Map<String, Object> resetSimulation() {
		simulationService.reset();
		return Map.of("status", "reset");
	}

	@PostMapping("/api/particleCount") @ResponseBody
	public Map<String, Object> setParticleCount(@RequestBody Map<String, Integer> request) {
		simulationService.setParticleCount(request.getOrDefault("count", 1000));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/timeScale") @ResponseBody
	public Map<String, Object> setTimeScale(@RequestBody Map<String, Double> request) {
		simulator.setTimeScale(request.getOrDefault("timeScale", 100.0));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/fastForward") @ResponseBody
	public Map<String, Object> toggleFastForward(@RequestBody Map<String, Boolean> request) {
		simulator.setFastForward(request.getOrDefault("enabled", false));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/barPotential") @ResponseBody
	public Map<String, Object> toggleBarPotential(@RequestBody Map<String, Boolean> request) {
		simulator.setBarEnabled(request.getOrDefault("enabled", false));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/spiralArms") @ResponseBody
	public Map<String, Object> setSpiralArmCount(@RequestBody Map<String, Integer> request) {
		// Note: This may or may not be used by the CUDA kernel depending on implementation.
		// Kept for API consistency.
		simulator.setNumberOfArms(request.getOrDefault("arms", 2));
		return Map.of("status", "updated");
	}

	@PostMapping("/api/smbhMass") @ResponseBody
	public Map<String, Object> setSmbhMass(@RequestBody Map<String, Double> request) {
		// Mass is expected in millions of solar masses from the UI
		simulator.setSmbhMass(request.getOrDefault("mass", 4.0) * 1.0e6);
		return Map.of("status", "updated");
	}
}
