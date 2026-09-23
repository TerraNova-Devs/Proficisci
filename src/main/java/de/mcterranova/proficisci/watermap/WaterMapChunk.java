package de.mcterranova.proficisci.watermap;

import java.util.Arrays;

public final class WaterMapChunk {
    private final int chunkX;
    private final int chunkZ;
    private final byte[] depths;

    public WaterMapChunk(int chunkX, int chunkZ, byte[] depths) {
        if (depths.length != WaterMapConstants.CHUNK_AREA) {
            throw new IllegalArgumentException("Water map chunks must contain exactly " + WaterMapConstants.CHUNK_AREA + " cells.");
        }

        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.depths = Arrays.copyOf(depths, depths.length);
        validateDepths(this.depths);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public byte[] depths() {
        return Arrays.copyOf(depths, depths.length);
    }

    public byte depthAt(int localX, int localZ) {
        return depths[WaterMapConstants.index(localX, localZ)];
    }

    private static void validateDepths(byte[] depths) {
        for (byte depth : depths) {
            if (depth < WaterMapConstants.LAND || depth > WaterMapConstants.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid water depth value: " + depth);
            }
        }
    }
}
