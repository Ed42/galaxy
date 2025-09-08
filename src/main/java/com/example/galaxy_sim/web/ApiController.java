package com.example.galaxy_sim.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.galaxy_sim.engine.Particle;
import com.example.galaxy_sim.engine.Phys;
import com.example.galaxy_sim.engine.StarType;
import com.example.galaxy_sim.service.SimulationService;
import com.example.galaxy_sim.web.dto.SnapshotDTO;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final SimulationService sim;

    public ApiController(SimulationService sim) {
        this.sim = sim;
    }

    @GetMapping(value = "/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public SnapshotDTO snapshot(@RequestParam(name = "lod", defaultValue = "0") int lod) {
        List<Particle> ps = sim.particles();
        int n = ps.size();
        int stride = (lod > 0) ? lod : Math.max(1, n / 10000); // keep ~<=10k points
        int m = (n + stride - 1) / stride;
        float[] xs = new float[m];
        float[] ys = new float[m];
        byte[] types = new byte[m];
        for (int i = 0, j = 0; i < n; i += stride, j++) {
            Particle p = ps.get(i);
            xs[j] = (float) p.x;
            ys[j] = (float) p.y;
            types[j] = (byte) (p.type == StarType.BULGE ? 1 : 0);
        }
        float[] smbh = new float[] {0f, 0f};
        double energyApprox = sim.getEnergyApprox();
        return new SnapshotDTO(sim.getTimeYears(), sim.getYearsPerSecond(), sim.isPaused(), sim.isBarEnabled(), sim.getSteps(), stride, xs, ys, types, smbh, energyApprox);
    }

    @PostMapping("/pause")
    public void pause(@RequestParam(name = "v", defaultValue = "true") boolean v) { sim.pause(v); }

    @PostMapping("/togglePause")
    public void togglePause() { sim.togglePause(); }

    @PostMapping("/reset")
    public void reset(@RequestParam(name = "n", defaultValue = "10000") int n) { sim.reset(Math.max(1000, Math.min(50000, n))); }

    @PostMapping("/speed")
    public void speed(@RequestParam(name = "yps") double yps) { sim.setYearsPerSecond(yps); }

    @PostMapping("/fastForward")
    public void fastForward() { sim.setYearsPerSecond(100_000_000.0); }

    @PostMapping("/bar")
    public void bar(@RequestParam(name = "enabled") boolean enabled) { sim.setBarEnabled(enabled); }
}

