Objective: Develop a Java-based spiral galaxy simulator using the Barnes-Hut algorithm to model a localized region (1–2 kpc) of a disk galaxy, including a central supermassive black hole (SMBH), central bulge, and background spiral arm potential. The simulation must support an adjustable time scale (100 yr/s to 100 Myr/s), ensure numerical stability, and provide real-time visualization of particle motions, with extensibility for a “Drake” simulation to model civilization emergence and interactions.
Requirements:
 Simulation Core:
	•  Use the Barnes-Hut algorithm with a 2D quadtree to compute gravitational forces among 1,000–10,000 particles, supplemented by a background potential (disk + halo + bulge + spiral arms + SMBH).
	•  Implement a 4th-order symplectic integrator (e.g., Yoshida) for time evolution.
	•  Use gravitational softening: ε = 10 pc for particle-particle interactions, ε_SMBH = 1 pc for SMBH interactions.
	•  Ensure stability at 100 yr/s using adaptive time-stepping: Δt = min(0.01 × (Gρ)^(-1/2), 10–100 years).
	•  Support a fast-forward mode up to 100 Myr/s.
 Initial Conditions:
	•  Initialize a 2 kpc × 2 kpc patch with 1,000–10,000 particles (mass ~10⁶ M_sun each), using an exponential density profile: Σ(r) = Σ_0 exp(-r/R_d), R_d = 1 kpc, total mass ~10⁹ M_sun.
	•  Assign circular velocities based on a background potential:
		•  Miyamoto-Nagai disk: M_disk = 10¹¹ M_sun, a = 5 kpc, b = 0.3 kpc.
		•  Hernquist bulge: M_bulge = 10¹⁰ M_sun, a_bulge = 1 kpc.
		•  Isothermal halo: v_c = 200 km/s.
		•  Logarithmic spiral arms: Φ_spiral = A cos(2θ – k ln(r)), A = 10% of disk potential, k for ~10–15° pitch angle.
		•  SMBH: M_SMBH = 4 × 10⁶ M_sun, Φ_SMBH = -GM_SMBH/(r + ε_SMBH).
	•  Add Gaussian velocity perturbations (~5–10 km/s) for clustering.
	•  Assign particle attributes: stellar type (disk: main sequence, bulge: older giants), age (disk: 0–5 Gyr, bulge: 5–10 Gyr), metallicity (disk: 0.2–1, bulge: ~1), habitable flag (false).
 Background Phenomena:
	•  Include a static logarithmic spiral potential for arms.
	•  Add an optional rotating bar potential (Φ_bar = A_bar cos(2θ – Ω_bar t), A_bar = 5% of disk potential, Ω_bar = 50 km/s/kpc), toggled via UI.
	•  Cache background potential (disk + bulge + halo + arms + SMBH) analytically.
 Time Scale and Stability:
	•  Support time scale of 100 yr/s to 100 Myr/s via slider, with fast-forward toggle to 100 Myr/s.
	•  Compute Δt = min(0.01 × (Gρ)^(-1/2), 10–100 years) using quadtree density, with tighter constraints near SMBH (t_dyn ≈ 10³ years at r ≈ 1 pc).
	•  Monitor energy every 10³ steps, reducing Δt if drift exceeds 0.1% over 10⁴ years.
 Visualization:
	•  Use Java Swing or JOGL for 2D rendering of a 2 kpc × 2 kpc region (800x800 pixels).
	•  Render particles as points (blue for disk, yellow for bulge, red dot for SMBH), with brightness proportional to density.
	•  Include UI:
		•  Slider for time scale (100 yr/s to 100 Myr/s).
		•  Fast-forward button.
		•  Toggle for bar potential.
		•  Display of simulation time (years), energy, and particle count.
		•  Pause/resume/reset buttons.
		•  Zoom/pan to focus on central 100 pc (SMBH) or 2 kpc region.
	•  Target ≥10 FPS for 10,000 particles.
 Extensibility for Drake Simulation:
	•  Design Particle with fields: stellar type, age, metallicity, habitable flag, distance to SMBH.
	•  Include placeholder Civilization class with attributes (emergence time, range, type) and methods (e.g., emerge, interact).
	•  Support habitability probability: P_life ∝ metallicity × exp(-r/r_SMBH), r_SMBH ≈ 100 pc.
	•  Track simulation time for long-term evolution (~10⁹ years).
 Physical Parameters:
	•  G = 4.302 × 10⁻³ pc (M_sun)^(-1) (km/s)².
	•  ε = 10 pc, ε_SMBH = 1 pc.
	•  Region: ±1 kpc in x, y.
	•  Background potential: Miyamoto-Nagai disk + Hernquist bulge + isothermal halo + spiral arms + SMBH.
 Performance Optimization:
	•  Parallelize force calculations using ExecutorService.
	•  Cache background potential.
	•  Use level-of-detail rendering.
 Deliverables:
	•  Java classes: Particle, Quadtree, Simulator, Visualizer, Civilization (placeholder).
	•  Main loop for real-time simulation and visualization.
	•  Comments explaining SMBH/bulge integration and “Drake” extensibility.
	•  README with build/run instructions.
Constraints:
•  No external physics libraries; implement physics from scratch.
•  Run on standard hardware (8 GB RAM, quad-core CPU).
•  Focus on collisionless dynamics.
Success Criteria:
•  Stable simulation (energy drift <0.1% over 10⁴ years) at 100 yr/s.
•  Visible clustering in 2 kpc region, with SMBH and bulge effects (e.g., tight central orbits).
•  Interactive visualization (≥10 FPS) for 10,000 particles.
•  Fast-forward mode shows long-term dynamics (~250 Myr).
•  Code supports “Drake” features (e.g., habitability based on SMBH proximity).
Implementation Guidance:
•  Quadtree with θ = 0.7.
•  Cache background potential (sum of components).
•  Test with 1,000 particles, scale to 10,000.
•  Add hooks for civilization logic (e.g., P_life calculation).
Example Workflow:
* Initialize particles with disk/bulge properties.
* Build quadtree, compute forces (particles + background).
* Step with Yoshida integrator.
* Render particles, SMBH, and UI.
* Monitor energy and adjust Δt.

