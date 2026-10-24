package de.mcterranova.proficisci.watermap;

import java.util.Arrays;

public final class WaterMapRegion {
    private final int regionX;
    private final int regionZ;
    private final byte[] values;

    public WaterMapRegion(int regionX, int regionZ, byte[] values) {
        if (values.length != WaterMapConstants.REGION_AREA) {
            throw new IllegalArgumentException("Water map regions must contain exactly " + WaterMapConstants.REGION_AREA + " cells.");
        }

        this.regionX = regionX;
        this.regionZ = regionZ;
        this.values = Arrays.copyOf(values, values.length);
    }

    public int regionX() {
        return regionX;
    }

    public int regionZ() {
        return regionZ;
    }

    public byte[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public byte valueAt(int x, int z) {
        return values[index(x, z)];
    }

    public static int index(int x, int z) {
        return z * WaterMapConstants.REGION_SIZE + x;
    }
}
