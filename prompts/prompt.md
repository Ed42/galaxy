Prompt for AI Coding Agent: Spiral Galaxy Simulator in Java
Task: Develop a spiral galaxy simulator in Java using the Barnes-Hut algorithm. The simulator must exhibit stable rotation, realistic spiral arms, and an adjustable time scale (1 second per 100 years to 1 second per 10 million years), while addressing stability concerns with time scaling.
Requirements

Simulate gravitational interactions of particles (stars) in 2D using the Barnes-Hut algorithm.
Use the leapfrog integrator for stable time stepping.
Include a fixed halo potential for disk stability and a flat rotation curve.
Set up initial conditions for a disk galaxy.
Implement a softening length to manage close encounters.
Allow users to adjust the simulation speed via a time scale control.
Visualize the simulation in real-time with Java graphics (e.g., Swing or JavaFX).

Approach
1. Barnes-Hut Algorithm

Quadtree: Build a 2D quadtree to partition space and organize particles.
Force Calculation: Compute gravitational forces with the Barnes-Hut approximation, using θ = 0.5 for a balance of speed and accuracy.

2. Leapfrog Integrator

Update positions and velocities using the leapfrog method:
Velocity half-step: v(t + δt/2) = v(t) + a(t) * δt/2
Position step: x(t + δt) = x(t) + v(t + δt/2) * δt
Velocity full-step: v(t + δt) = v(t + δt/2) + a(t + δt) * δt/2


This ensures better energy conservation.

3. Halo Potential

Use a logarithmic potential: Φ(r) = v₀² log(r), where v₀ ≈ 200 km/s (scaled to simulation units).
Add its force (F = -∇Φ) to the Barnes-Hut forces for each particle.

4. Initial Conditions

Positions: Distribute N = 10,000 particles in a disk with an exponential density profile (ρ ∝ e^(-r/r_scale)).
Velocities: Set circular velocities (v_c = √(GM(r)/r)) with a flat rotation curve, adding small random dispersion.

5. Softening Length

Modify gravity: F = -GMm / (r² + ε²)^(3/2) * r, with ε ≈ average interparticle spacing / 10.

6. Time Step and Stability

Set δt = T_dyn / 100, where T_dyn is the dynamical time (e.g., orbital period at the disk’s edge, ~200 million years in real units, scaled to simulation units).
Ensure δt is small enough for accuracy with the leapfrog method.

7. Adjustable Time Scale

Map 1 second of real time to 100 years–10 million years of simulation time.
Control speed by adjusting the number of time steps (k) per frame: simulation time per second = k * δt * FPS.
Add a UI slider to set k, scaling the speed interactively.

8. Visualization

Use Swing or JavaFX to render particles as points on a 2D canvas.
Update the display each frame after computing k time steps.

Parameters (Example in Normalized Units)

G = 1, total mass = 1, disk radius = 1.
N = 10,000 particles.
δt = 0.001 (tuned to dynamical time).
ε = 0.01.

Optimization

Consider multi-threading for quadtree construction and force calculations if performance lags.

Notes

Stability is ensured by a fixed δt and leapfrog integration, independent of visualization speed.
Spiral arms emerge from self-gravity; more particles or tuning may enhance realism.
Reference: Princeton’s Barnes-Hut assignment (web ID: 4).

This will create a stable, interactive spiral galaxy simulator meeting your requirements.
