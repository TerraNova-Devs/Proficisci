package de.mcterranova.proficisci.watermap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class WaterMapStorage {
    private static final String REGION_FILE_SUFFIX = ".bin";

    private final Path root;
    private final Path regionsDirectory;
    private final Path rendersDirectory;
    private final WaterMapRenderer renderer;

    public WaterMapStorage(Path root) {
        this.root = root;
        this.regionsDirectory = root.resolve("regions");
        this.rendersDirectory = root.resolve("renders");
        this.renderer = new WaterMapRenderer();
    }

    public void initialize() throws IOException {
        Files.createDirectories(regionsDirectory);
        Files.createDirectories(rendersDirectory);
        writeMetaFile(countRegions());
    }

    public void saveChunk(WaterMapChunk chunk) throws IOException {
        saveChunk(chunk, true);
    }

    public void saveChunk(WaterMapChunk chunk, boolean updateRenderCache) throws IOException {
        int regionX = regionCoordinate(chunk.chunkX());
        int regionZ = regionCoordinate(chunk.chunkZ());
        writeChunkToRegionFile(regionX, regionZ, chunk);

        if (updateRenderCache) {
            renderOverview();
        }
    }

    public void renderOverview() throws IOException {
        renderer.writeOverview(loadRegions(), rendersDirectory.resolve("overview.png"));
        writeMetaFile(countRegions());
    }

    public WaterMapChunk loadChunk(int chunkX, int chunkZ) throws IOException {
        int regionX = regionCoordinate(chunkX);
        int regionZ = regionCoordinate(chunkZ);
        byte[] regionValues = readRegionValues(regionX, regionZ);
        byte[] chunkValues = new byte[WaterMapConstants.CHUNK_AREA];

        int localChunkX = localChunkCoordinate(chunkX);
        int localChunkZ = localChunkCoordinate(chunkZ);
        int startX = localChunkX * WaterMapConstants.CHUNK_SIZE;
        int startZ = localChunkZ * WaterMapConstants.CHUNK_SIZE;

        for (int z = 0; z < WaterMapConstants.CHUNK_SIZE; z++) {
            int regionOffset = WaterMapRegion.index(startX, startZ + z);
            int chunkOffset = z * WaterMapConstants.CHUNK_SIZE;
            System.arraycopy(regionValues, regionOffset, chunkValues, chunkOffset, WaterMapConstants.CHUNK_SIZE);
        }

        return new WaterMapChunk(chunkX, chunkZ, chunkValues);
    }

    public List<WaterMapRegion> loadRegions() throws IOException {
        List<WaterMapRegion> regions = new ArrayList<>();
        if (!Files.isDirectory(regionsDirectory)) {
            return regions;
        }

        try (Stream<Path> files = Files.list(regionsDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(REGION_FILE_SUFFIX)).toList()) {
                RegionCoordinate coordinate = parseRegionCoordinate(file);
                regions.add(new WaterMapRegion(coordinate.x(), coordinate.z(), Files.readAllBytes(file)));
            }
        }

        return regions;
    }

    public Path root() {
        return root;
    }

    public Path regionPath(int regionX, int regionZ) {
        return regionsDirectory.resolve(fileName(regionX, regionZ, REGION_FILE_SUFFIX));
    }

    public static int regionCoordinate(int chunkCoordinate) {
        return Math.floorDiv(chunkCoordinate, WaterMapConstants.REGION_CHUNKS);
    }

    public static int localChunkCoordinate(int chunkCoordinate) {
        return Math.floorMod(chunkCoordinate, WaterMapConstants.REGION_CHUNKS);
    }

    private byte[] readRegionValues(int regionX, int regionZ) throws IOException {
        Path path = regionPath(regionX, regionZ);
        if (!Files.exists(path)) {
            return new byte[WaterMapConstants.REGION_AREA];
        }

        byte[] values = Files.readAllBytes(path);
        if (values.length != WaterMapConstants.REGION_AREA) {
            throw new IOException("Invalid water map region size in " + path + ": " + values.length + " bytes.");
        }
        return values;
    }

    private void writeRegionValues(int regionX, int regionZ, byte[] values) throws IOException {
        if (values.length != WaterMapConstants.REGION_AREA) {
            throw new IOException("Invalid water map region write size: " + values.length + " bytes.");
        }

        Files.createDirectories(regionsDirectory);
        Files.write(regionPath(regionX, regionZ), values);
    }

    private void writeChunkToRegionFile(int regionX, int regionZ, WaterMapChunk chunk) throws IOException {
        Files.createDirectories(regionsDirectory);
        Path path = regionPath(regionX, regionZ);
        Set<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

        try (SeekableByteChannel channel = Files.newByteChannel(path, options)) {
            if (channel.size() < WaterMapConstants.REGION_AREA) {
                channel.position(WaterMapConstants.REGION_AREA - 1L);
                channel.write(ByteBuffer.wrap(new byte[]{0}));
            }

            int localChunkX = localChunkCoordinate(chunk.chunkX());
            int localChunkZ = localChunkCoordinate(chunk.chunkZ());
            int startX = localChunkX * WaterMapConstants.CHUNK_SIZE;
            int startZ = localChunkZ * WaterMapConstants.CHUNK_SIZE;
            byte[] chunkValues = chunk.depths();

            for (int z = 0; z < WaterMapConstants.CHUNK_SIZE; z++) {
                long regionOffset = WaterMapRegion.index(startX, startZ + z);
                int chunkOffset = z * WaterMapConstants.CHUNK_SIZE;
                channel.position(regionOffset);
                channel.write(ByteBuffer.wrap(chunkValues, chunkOffset, WaterMapConstants.CHUNK_SIZE));
            }
        }
    }

    private void writeChunkIntoRegion(byte[] regionValues, WaterMapChunk chunk) {
        int localChunkX = localChunkCoordinate(chunk.chunkX());
        int localChunkZ = localChunkCoordinate(chunk.chunkZ());
        int startX = localChunkX * WaterMapConstants.CHUNK_SIZE;
        int startZ = localChunkZ * WaterMapConstants.CHUNK_SIZE;
        byte[] chunkValues = chunk.depths();

        for (int z = 0; z < WaterMapConstants.CHUNK_SIZE; z++) {
            int regionOffset = WaterMapRegion.index(startX, startZ + z);
            int chunkOffset = z * WaterMapConstants.CHUNK_SIZE;
            System.arraycopy(chunkValues, chunkOffset, regionValues, regionOffset, WaterMapConstants.CHUNK_SIZE);
        }
    }

    private long countRegions() throws IOException {
        if (!Files.isDirectory(regionsDirectory)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(regionsDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(REGION_FILE_SUFFIX)).count();
        }
    }

    private void writeMetaFile(long regionCount) throws IOException {
        Files.createDirectories(root);
        String json = """
                {
                  "schema": 3,
                  "chunkSize": %d,
                  "regionChunks": %d,
                  "regionSize": %d,
                  "regionBytes": %d,
                  "waterHeight": %.1f,
                  "landSearchRadius": %d,
                  "minLandMassBlocks": %d,
                  "minNavigableRiverWidth": %d,
                  "oceanShoreBiomeRadius": %d,
                  "oceanShoreBiomeStep": %d,
                  "waterwayBiomeRadius": %d,
                  "waterwayBiomeStep": %d,
                  "depthByDistanceToLand": {
                    "shallowMaxDistance": %d,
                    "mediumMaxDistance": %d
                  },
                  "values": {
                    "0": "Land",
                    "1": "flaches Wasser",
                    "2": "mittleres Wasser",
                    "3": "tiefes Wasser",
                    "4": "flacher Fluss",
                    "5": "mittlerer Fluss",
                    "6": "tiefer Fluss",
                    "7": "nicht passierbarer Fluss",
                    "8": "Sumpfwasser",
                    "9": "Suesswasser"
                  },
                  "regionCount": %d,
                  "updatedAt": "%s"
                }
                """.formatted(
                WaterMapConstants.CHUNK_SIZE,
                WaterMapConstants.REGION_CHUNKS,
                WaterMapConstants.REGION_SIZE,
                WaterMapConstants.REGION_AREA,
                WaterMapConstants.WATER_HEIGHT,
                WaterMapConstants.LAND_SEARCH_RADIUS,
                WaterMapConstants.MIN_LAND_MASS_BLOCKS,
                WaterMapConstants.MIN_NAVIGABLE_RIVER_WIDTH,
                WaterMapConstants.OCEAN_SHORE_BIOME_RADIUS,
                WaterMapConstants.OCEAN_SHORE_BIOME_STEP,
                WaterMapConstants.WATERWAY_BIOME_RADIUS,
                WaterMapConstants.WATERWAY_BIOME_STEP,
                WaterMapConstants.SHALLOW_DISTANCE,
                WaterMapConstants.MEDIUM_DISTANCE,
                regionCount,
                Instant.now()
        );
        Files.writeString(root.resolve("meta.json"), json);
    }

    private static String fileName(int x, int z, String suffix) {
        return x + "_" + z + suffix;
    }

    private static RegionCoordinate parseRegionCoordinate(Path file) {
        String name = file.getFileName().toString();
        String baseName = name.substring(0, name.length() - REGION_FILE_SUFFIX.length());
        int separator = baseName.indexOf('_');
        if (separator < 1 || separator == baseName.length() - 1) {
            throw new IllegalArgumentException("Invalid water map region file name: " + name);
        }

        return new RegionCoordinate(
                Integer.parseInt(baseName.substring(0, separator)),
                Integer.parseInt(baseName.substring(separator + 1))
        );
    }

    private record RegionCoordinate(int x, int z) {
    }

}
