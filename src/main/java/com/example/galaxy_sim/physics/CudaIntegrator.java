package com.example.galaxy_sim.physics;

import com.example.galaxy_sim.model.Particle;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class CudaIntegrator {

	private final CUcontext context;
	private final CUfunction kernelFunction;
	private CUdeviceptr deviceParticles;
	private int particleCount;
	private boolean initialized = false;

	private static final int BLOCK_SIZE = 256;
	private static final int PARTICLE_STRIDE = 6;

	private float smbhMass = 4.0e6f;
	private boolean barEnabled = false;

	public CudaIntegrator() {
		try {
			JCudaDriver.setExceptionsEnabled(true);
			JCudaDriver.cuInit(0);
			CUdevice device = new CUdevice();
			JCudaDriver.cuDeviceGet(device, 0);
			this.context = new CUcontext();
			JCudaDriver.cuCtxCreate(this.context, 0, device);
			String ptxCode = compileCudaKernel();
			CUmodule module = new CUmodule();
			JCudaDriver.cuModuleLoadData(module, ptxCode);
			this.kernelFunction = new CUfunction();
			JCudaDriver.cuModuleGetFunction(this.kernelFunction, module, "integrate_step");
		} catch (Exception e) {
			throw new RuntimeException("CRITICAL: Failed to initialize CUDA. Check driver and toolkit. Error: " + e.getMessage(), e);
		}
	}

	private void setCurrentContext() {
		try {
			JCudaDriver.cuCtxSetCurrent(this.context);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set CUDA context for the current thread.", e);
		}
	}

	private String compileCudaKernel() throws IOException, InterruptedException {
		try (InputStream cuStream = getClass().getClassLoader().getResourceAsStream("kernels/galaxy_kernel.cu")) {
			if (cuStream == null) throw new IOException("galaxy_kernel.cu not found in resources.");
			File tempCuFile = File.createTempFile("galaxy_kernel", ".cu");
			File tempPtxFile = File.createTempFile("galaxy_kernel", ".ptx");
			tempCuFile.deleteOnExit(); tempPtxFile.deleteOnExit();

			Files.copy(cuStream, tempCuFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			// Updated to a more modern compute capability for better performance on recent GPUs.
			// This value can be tuned depending on the target hardware (e.g., sm_86 for Ampere).
			ProcessBuilder pb = new ProcessBuilder("nvcc", "--ptx", "-arch=sm_75", "--use_fast_math", "-o", tempPtxFile.getAbsolutePath(), tempCuFile.getAbsolutePath());
			Process process = pb.start();
			if (process.waitFor() != 0) {
				try (InputStream errorStream = process.getErrorStream()) {
					throw new RuntimeException("nvcc compilation failed: " + new String(errorStream.readAllBytes()));
				}
			}
			return new String(Files.readAllBytes(tempPtxFile.toPath()));
		}
	}

	public void initialize(List<Particle> particles) {
		setCurrentContext();
		this.particleCount = particles.size();
		if (initialized) JCudaDriver.cuMemFree(deviceParticles);

		float[] hostData = new float[particleCount * PARTICLE_STRIDE];
		for (int i = 0; i < particleCount; i++) {
			Particle p = particles.get(i);
			int base = i * PARTICLE_STRIDE;
			hostData[base] = (float) p.x(); hostData[base + 1] = (float) p.y();
			hostData[base + 2] = (float) p.vx(); hostData[base + 3] = (float) p.vy();
			hostData[base + 4] = (float) p.mass(); hostData[base + 5] = p.stellarType().ordinal();
		}

		deviceParticles = new CUdeviceptr();
		JCudaDriver.cuMemAlloc(deviceParticles, (long) particleCount * PARTICLE_STRIDE * Sizeof.FLOAT);
		JCudaDriver.cuMemcpyHtoD(deviceParticles, Pointer.to(hostData), (long) particleCount * PARTICLE_STRIDE * Sizeof.FLOAT);
		initialized = true;
	}

	public void step(double dtMyr, double currentTimeMyr) {
		if (!initialized) return;
		setCurrentContext();
		int gridSize = (particleCount + BLOCK_SIZE - 1) / BLOCK_SIZE;
		Pointer kernelParameters = Pointer.to(
			Pointer.to(deviceParticles), Pointer.to(new int[]{particleCount}),
			Pointer.to(new float[]{(float) dtMyr}), Pointer.to(new float[]{(float) currentTimeMyr}),
			Pointer.to(new float[]{smbhMass}), Pointer.to(new int[]{barEnabled ? 1 : 0})
		);
		// Allocate shared memory for the particle data tile
		int sharedMemBytes = BLOCK_SIZE * PARTICLE_STRIDE * Sizeof.FLOAT;
		JCudaDriver.cuLaunchKernel(kernelFunction, gridSize, 1, 1, BLOCK_SIZE, 1, 1, sharedMemBytes, null, kernelParameters, null);
	}

	public float[] getRawData() {
		if (!initialized) return new float[0];
		setCurrentContext();
		float[] hostData = new float[particleCount * PARTICLE_STRIDE];
		JCudaDriver.cuMemcpyDtoH(Pointer.to(hostData), deviceParticles, (long) particleCount * PARTICLE_STRIDE * Sizeof.FLOAT);
		return hostData;
	}

	public void shutdown() {
		if (context != null) {
			setCurrentContext();
			if (initialized) JCudaDriver.cuMemFree(deviceParticles);
			JCudaDriver.cuCtxDestroy(context);
		}
	}

	public void setSmbhMass(double mass) { this.smbhMass = (float) mass; }
	public void setBarEnabled(boolean enabled) { this.barEnabled = enabled; }
}
