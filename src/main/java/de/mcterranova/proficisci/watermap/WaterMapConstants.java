package de.mcterranova.proficisci.watermap;

import java.awt.Color;

public final class WaterMapConstants {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    public static final int REGION_CHUNKS = 32;
    public static final int REGION_SIZE = CHUNK_SIZE * REGION_CHUNKS;
    public static final int REGION_AREA = REGION_SIZE * REGION_SIZE;
    public static final double WATER_HEIGHT = 62.5D;
    public static final int WATER_BLOCK_Y = (int) Math.floor(WATER_HEIGHT);

    public static final byte LAND = 0;
    public static final byte SHALLOW_WATER = 1;
    public static final byte MEDIUM_WATER = 2;
    public static final byte DEEP_WATER = 3;
    public static final byte SHALLOW_RIVER = 4;
    public static final byte MEDIUM_RIVER = 5;
    public static final byte DEEP_RIVER = 6;
    public static final byte BLOCKED_RIVER = 7;
    public static final byte SWAMP_WATER = 8;
    public static final byte FRESH_WATER = 9;
    public static final byte MAX_VALUE = FRESH_WATER;

    public static final int LAND_SEARCH_RADIUS = 192;
    public static final int SHALLOW_DISTANCE = 45;
    public static final int MEDIUM_DISTANCE = 135;
    public static final int MIN_LAND_MASS_BLOCKS = 768;
    public static final int MIN_NAVIGABLE_RIVER_WIDTH = 12;
    public static final int OCEAN_SHORE_BIOME_RADIUS = 24;
    public static final int OCEAN_SHORE_BIOME_STEP = 4;
    public static final int WATERWAY_BIOME_RADIUS = 24;
    public static final int WATERWAY_BIOME_STEP = 4;

    public static final Color LAND_COLOR = new Color(93, 125, 57);
    public static final Color SHALLOW_WATER_COLOR = new Color(103, 190, 232);
    public static final Color MEDIUM_WATER_COLOR = new Color(32, 103, 205);
    public static final Color DEEP_WATER_COLOR = new Color(15, 38, 117);
    public static final Color RIVER_COLOR = new Color(255, 64, 190);
    public static final Color BLOCKED_RIVER_COLOR = new Color(230, 32, 32);
    public static final Color SWAMP_WATER_COLOR = new Color(24, 92, 42);
    public static final Color FRESH_WATER_COLOR = new Color(236, 204, 57);

    private WaterMapConstants() {
    }

    public static int index(int localX, int localZ) {
        return localZ * CHUNK_SIZE + localX;
    }

    public static int rgbForDepth(byte depth) {
        return switch (depth) {
            case SHALLOW_WATER -> SHALLOW_WATER_COLOR.getRGB();
            case MEDIUM_WATER -> MEDIUM_WATER_COLOR.getRGB();
            case DEEP_WATER -> DEEP_WATER_COLOR.getRGB();
            case SHALLOW_RIVER, MEDIUM_RIVER, DEEP_RIVER -> RIVER_COLOR.getRGB();
            case BLOCKED_RIVER -> BLOCKED_RIVER_COLOR.getRGB();
            case SWAMP_WATER -> SWAMP_WATER_COLOR.getRGB();
            case FRESH_WATER -> FRESH_WATER_COLOR.getRGB();
            default -> LAND_COLOR.getRGB();
        };
    }

    public static boolean isOceanWater(byte depth) {
        return depth >= SHALLOW_WATER && depth <= DEEP_WATER;
    }

    public static byte waterValueForDistance(int distanceToLand, boolean river) {
        if (distanceToLand <= SHALLOW_DISTANCE) {
            return river ? SHALLOW_RIVER : SHALLOW_WATER;
        }
        if (distanceToLand <= MEDIUM_DISTANCE) {
            return river ? MEDIUM_RIVER : MEDIUM_WATER;
        }
        return river ? DEEP_RIVER : DEEP_WATER;
    }
}
