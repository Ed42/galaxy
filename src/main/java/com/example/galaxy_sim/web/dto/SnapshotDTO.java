package com.example.galaxy_sim.web.dto;

import java.util.List;

public record SnapshotDTO(
        double simTimeYears,
        double yearsPerSecond,
        boolean paused,
        boolean barEnabled,
        long steps,
        int stride,
        float[] x,
        float[] y,
        byte[] type,
        float[] smbh,
        double energyApprox
) {}

