package com.minicloud.api.domain;

public enum InstanceType {
    T2_MICRO(0.0116),
    T2_SMALL(0.023),
    T2_MEDIUM(0.046),
    T3_LARGE(0.096),
    M5_XLARGE(0.192);

    private final double costPerHour;

    InstanceType(double costPerHour) {
        this.costPerHour = costPerHour;
    }

    public double getCostPerHour() {
        return costPerHour;
    }
}
