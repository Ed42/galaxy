#### **1. High-Level Objective**
Your primary goal is to refactor this existing Java Spring Boot N-body galaxy simulation to offload its core computational workload to an NVIDIA GPU. The current CPU-based implementation, while functional, is limited by two-body relaxation effects and cannot scale beyond ~50,000 particles. The refactoring will use the NVIDIA CUDA platform via JCuda bindings to enable simulations with a significantly higher particle count (targeting 100,000 to 1,000,000+), leading to more physically realistic and visually detailed results. The external behavior, particularly the WebSocket-based visual output to the existing web UI, must remain unchanged.
#### **2. Analysis of Existing Architecture**
You will be working with a standard Spring Boot application with the following key components:
- **Backend (Java):** A Java 21 and Maven-based backend server.
- **Simulation Clock:** uses a to call a method at a fixed rate (30 Hz). `SimulationService.java``ScheduledExecutorService``tick()`
- **Simulation Orchestrator:** manages the simulation state (particle list, current time) and orchestrates the physics steps. `Simulator.java`
- **CPU-based Physics:**
    - is the current physics engine. It uses Java's to distribute force calculations across CPU cores. `YoshidaIntegrator.java``parallelStream()`
    - implements the Barnes-Hut algorithm to optimize force calculations (from O(N²) to O(N log N)). This will be replaced. `Quadtree.java`
    - provides analytical forces for the disk, bulge, halo, SMBH, and spiral arms. `BackgroundPotential.java`

- **Frontend (HTML/JS):** is a single-page web application that connects via WebSocket, receives simulation state as JSON, and renders the particles on an HTML5 Canvas. `index.html`

#### **3. Core Task: Migrate Physics Calculations to the GPU**
The central task is to replace the CPU-bound and its dependency with a new GPU-powered integrator. `YoshidaIntegrator``Quadtree`
- **Chosen Technology:** You must use the **JCuda** library, which provides low-level Java bindings for the NVIDIA CUDA Driver API.
- **Architectural Shift:** The simulation loop will be modified as follows:
    1. On simulation start/reset, particle data will be transferred from the JVM to the GPU's VRAM.
    2. On each simulation , the Java backend will invoke a custom CUDA kernel. `tick`
    3. This kernel will perform all physics calculations (particle-particle forces and background potential forces) entirely on the GPU.
    4. The updated particle data will be copied back from the GPU to the CPU only when the frontend client requires a state update for rendering.

- **Key Design Decision (Performance):** For the target particle counts (up to ~1M), a direct, brute-force N-body summation (O(N²)) is often more performant on GPUs than a complex Barnes-Hut implementation due to its perfect parallelism and avoidance of recursive tree-building. **You must implement a direct summation kernel.** The class will no longer be used for the core physics calculation. `Quadtree`

#### **4. Detailed Implementation Plan**
Follow these steps precisely.
##### **Step 1: Update Maven Dependencies**
Modify the to include the necessary JCuda dependencies. Use versions compatible with a modern CUDA toolkit (e.g., 12.x). Ensure all architectures are included. `pom.xml`
``` xml
<dependencies>
    <!-- ... other dependencies ... -->
    <dependency>
        <groupId>org.jcuda</groupId>
        <artifactId>jcuda</artifactId>
        <version>12.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.jcuda</groupId>
        <artifactId>jcuda-natives</artifactId>
        <version>12.0.0</version>
        <classifier>windows-x86_64</classifier>
    </dependency>
    <dependency>
        <groupId>org.jcuda</groupId>
        <artifactId>jcuda-natives</artifactId>
        <version>12.0.0</version>
        <classifier>linux-x86_64</classifier>
    </dependency>
    <!-- Add other classifiers if needed -->
</dependencies>
```
##### **Step 2: Create the CUDA Kernel File**
1. Create a new file: `src/main/resources/kernels/galaxy_kernel.cu`.
2. Inside this file, implement the entire physics calculation.
3. **Particle Struct:** Define a C++ `struct Particle` that mirrors the layout of the data required for physics (`double x, y, vx, vy, mass`).
4. **Background Potential:** Re-implement the logic from as `__device__` helper functions in C++. These functions must calculate the forces from the Miyamoto-Nagai disk, Hernquist bulge, isothermal halo, SMBH, spiral arms, and the rotating bar. They will be called by each GPU thread for its assigned particle. `BackgroundPotential.java`
5. **Main Kernel Function:** Create the main kernel `extern "C" __global__ void integrate_step(...)`.
    - **Signature:** It must accept pointers to device memory for particles, particle count, delta time (), current simulation time (), and any other required parameters (e.g., SMBH mass). `dt``currentTimeMyr`
    - **Logic:**
        1. Identify the global index for the current thread.
        2. Load the particle data for this thread into local/register memory.
        3. Initialize forces and to zero. `fx``fy`
        4. **Particle-Particle Force (O(N²)):** Loop through all other particles, calculate the direct gravitational force using the formula `F = G * m1 * m2 / (r^2 + ε^2)`, and accumulate the force components into and . `fx``fy`
        5. **Background Force:** Call the `__device__` functions to calculate the total background potential force and add it to and . `fx``fy`
        6. **Integration:** Use a simple Leapfrog or Euler-Cromer integration scheme to update the particle's velocity and then its position based on the total calculated force and . `dt`
        7. Write the updated particle data back to the global device memory.

##### **Step 3: Create the Java-CUDA Bridge**
1. Create a new Java class: `CudaIntegrator.java` in the package. `com.example.galaxy_sim.physics`
2. This class will manage all interactions with the GPU.
3. **Fields:** It will need fields for the `CUfunction` (the kernel), `CUdeviceptr` (the pointer to particle memory on the GPU), and particle count.
4. **Constructor:**
    - Enable JCuda exceptions. Initialize the driver (`cuInit`). Get the device and create a context.
    - Implement a helper method to compile the file to a `.ptx` file using an external `nvcc` process call. This method should handle finding `nvcc` in the system's PATH. `.cu`
    - Load the compiled PTX file into a `CUmodule`.
    - Get the `CUfunction` handle for the `integrate_step` kernel from the module.

5. **`initialize(List<Particle> particles)` method:**
    - Takes the list of particles from the . `Simulator`
    - Serializes the particle data (x, y, vx, vy, mass) into a flat `double[]` array.
    - Allocates memory on the GPU (`cuMemAlloc`) of the correct size.
    - Copies the host `double[]` array to the device pointer (`cuMemcpyHtoD`).

6. **`step(double dtMyr, double currentTimeMyr)` method:**
    - Sets up the kernel launch parameters: `blockSize` (e.g., 256) and `gridSize` (ceil(particleCount / blockSize)).
    - Packs the kernel arguments (the device pointer, particle count, dtMyr, currentTimeMyr, etc.) into a object. `Pointer`
    - Launches the kernel using `cuLaunchKernel`.
    - Waits for completion with `cuCtxSynchronize`.

7. **method:`getParticles()`**
    - Allocates a `double[]` array on the host (CPU).
    - Copies the particle data from the device back to the host array (`cuMemcpyDtoH`).
    - Deserializes this flat array back into a . `List<Particle>`

8. **method:`shutdown()`**
    - Frees the allocated device memory (`cuMemFree`).

##### **Step 4: Update the Simulator**
1. Modify . `Simulator.java`
2. Replace the field with the new `CudaIntegrator`. `YoshidaIntegrator`
3. In the constructor, instantiate `CudaIntegrator`. `Simulator`
4. In , call `integrator.initialize(this.particles)`. `reset()`
5. In `step()`, call `integrator.step(...)` with the correct time variables.
6. The method should now simply return `integrator.getParticles()`. `getParticles()`
7. The method should return an empty list, and the corresponding "Quadtree Overlay" checkbox in should be disabled or removed, as the quadtree is no longer part of the core simulation. `getQuadtreeBounds()``index.html`

#### **5. Final Deliverables**
Provide the complete, modified source code for the following files:
1. `pom.xml`
2. `physics/CudaIntegrator.java` (New File)
3. `simulation/Simulator.java`
4. `resources/kernels/galaxy_kernel.cu` (New File)

Additionally, provide brief instructions in a comment block on the prerequisites for running the modified code (i.e., having an NVIDIA GPU and the CUDA Toolkit installed). The solution must be a drop-in replacement that works with the existing application structure.
