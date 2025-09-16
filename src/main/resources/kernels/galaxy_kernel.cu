#include <cuda_runtime.h>

// Particle struct now uses single-precision floats for performance
struct Particle {
    float x, y;
    float vx, vy;
    float mass;
    float stellarType; // Using float to represent the enum ordinal
};

// --- Background Potential Device Functions (now using float) ---

__device__ float diskAcceleration(float r) {
    const float G = 4.30091e-3f;
    const float M_DISK = 1.0e11f;
    const float A_DISK = 5000.0f;
    const float B_DISK = 300.0f;
    float term = A_DISK + B_DISK;
    return (G * M_DISK * r) / powf(r * r + term * term, 1.5f);
}

__device__ float bulgeAcceleration(float r) {
    const float G = 4.30091e-3f;
    const float M_BULGE = 1.0e10f;
    const float A_BULGE = 1000.0f;
    float term = r + A_BULGE;
    return (G * M_BULGE) / (term * term);
}

__device__ float haloAcceleration(float r) {
    const float V_HALO = 200.0f;
    return (V_HALO * V_HALO) / r;
}

__device__ float smbhAcceleration(float r, float M_SMBH) {
    const float G = 4.30091e-3f;
    const float EPSILON_SMBH = 1.0f;
    return (G * M_SMBH) / (r * r + EPSILON_SMBH * EPSILON_SMBH);
}

__device__ void calculateBackgroundForce(const Particle& p, float& fx, float& fy, float M_SMBH, float currentTimeMyr, bool barEnabled) {
    float r = sqrtf(p.x * p.x + p.y * p.y);
    if (r < 1e-9f) return;

    // --- Axisymmetric Forces ---
    float ux = p.x / r, uy = p.y / r;
    float total_accel_mag = -(diskAcceleration(r) + bulgeAcceleration(r) + haloAcceleration(r) + smbhAcceleration(r, M_SMBH));
    fx += total_accel_mag * p.mass * ux;
    fy += total_accel_mag * p.mass * uy;

    // --- Spiral Arm Forces ---
    const float K_SPIRAL = 2.0f / tanf(12.0f * 3.1415926535f / 180.0f);
	const float AMP_SPIRAL = 0.1f;
	const float OMEGA_PATTERN = 0.025f; // rad/Myr
    const float A_DISK = 5000.0f;
    const float N_SPIRAL = 2.0f;
    float theta = atan2f(p.y, p.x);
	float A = AMP_SPIRAL * diskAcceleration(r) * r;
	float phase = N_SPIRAL * (theta - OMEGA_PATTERN * currentTimeMyr) - K_SPIRAL * logf(r / A_DISK);
	float Fr_spiral = -A * (K_SPIRAL / r) * sinf(phase);
	float F_theta_spiral = A * (N_SPIRAL / r) * sinf(phase);
    fx += (Fr_spiral * cosf(theta) - F_theta_spiral * sinf(theta)) * p.mass;
    fy += (Fr_spiral * sinf(theta) + F_theta_spiral * cosf(theta)) * p.mass;

    // --- Bar Force ---
    if (barEnabled && r < A_DISK) {
        const float AMP_BAR = 0.1f;
        const float OMEGA_BAR = 0.04f;
        float barAngle = OMEGA_BAR * currentTimeMyr;
        float barPhase = 2.0f * (theta - barAngle);
        float barStrength = diskAcceleration(r) * AMP_BAR * powf(1.0f - (r/A_DISK), 2.0f);
        float Fr_bar = -barStrength * cosf(barPhase);
		float F_theta_bar = barStrength * sinf(barPhase);
        fx += (Fr_bar * cosf(theta) - F_theta_bar * sinf(theta)) * p.mass;
        fy += (Fr_bar * sinf(theta) + F_theta_bar * cosf(theta)) * p.mass;
    }
}


extern "C" __global__ void integrate_step(Particle* particles, int numParticles, float dtMyr, float currentTimeMyr, float smbhMass, int barEnabled) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= numParticles) return;

    Particle p = particles[idx];
    float fx = 0.0f, fy = 0.0f;

    // --- Particle-Particle Forces (Shared Memory Optimization) ---
    const float G = 4.30091e-3f;
    const float softening_sq = 15.0f * 15.0f; // Epsilon^2
    extern __shared__ Particle shared_particles[];

    for (int tile_start = 0; tile_start < numParticles; tile_start += blockDim.x) {
        int load_idx = tile_start + threadIdx.x;
        if (load_idx < numParticles) {
            shared_particles[threadIdx.x] = particles[load_idx];
        }
        __syncthreads();

        // Ensure we don't read past the end of the array in the last tile
        int tile_end = min((int)blockDim.x, numParticles - tile_start);

        for (int j = 0; j < tile_end; j++) {
            // Avoid self-interaction
            if (tile_start + j == idx) continue;

            Particle other = shared_particles[j];
            float dx = other.x - p.x;
            float dy = other.y - p.y;
            float distSq = dx * dx + dy * dy;

            float invDist = rsqrtf(distSq + softening_sq);
            float force = G * p.mass * other.mass * invDist * invDist * invDist;
            fx += force * dx;
            fy += force * dy;
        }
        __syncthreads();
    }

    // --- Background Forces ---
    calculateBackgroundForce(p, fx, fy, smbhMass, currentTimeMyr, barEnabled != 0);

    // --- Leapfrog Integration ---
    // (A simple and stable integrator for this kind of problem)
    float ax = fx / p.mass;
    float ay = fy / p.mass;

    // Update velocities to the full step
    p.vx += ax * dtMyr;
    p.vy += ay * dtMyr;

    // Update positions using the new velocities
    p.x += p.vx * dtMyr;
    p.y += p.vy * dtMyr;

    particles[idx] = p;
}
