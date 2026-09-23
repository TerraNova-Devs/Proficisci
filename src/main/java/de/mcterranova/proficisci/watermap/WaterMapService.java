package de.mcterranova.proficisci.watermap;

import de.mcterranova.proficisci.Proficisci;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class WaterMapService {
    private static final int DEFAULT_CHUNKS_PER_TICK = 8;
    private static final int MAX_PENDING_CHUNKS = 64;
    private static final int BIOME_OTHER = 0;
    private static final int BIOME_OCEAN = 1;
    private static final int BIOME_RIVER = 2;
    private static final int BIOME_SWAMP = 3;

    private final Proficisci plugin;
    private final WaterMapStorage storage;
    private final ExecutorService renderExecutor;
    private BukkitTask activeRenderTask;

    public WaterMapService(Proficisci plugin) {
        this.plugin = plugin;
        Path storageRoot = plugin.getDataFolder().toPath().resolve("storage");
        this.storage = new WaterMapStorage(storageRoot);
        int workerThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        this.renderExecutor = Executors.newFixedThreadPool(workerThreads);
    }

    public void initialize() throws IOException {
        storage.initialize();
    }

    public void shutdown() {
        renderExecutor.shutdownNow();
    }

    public WaterMapChunk scanChunk(World world, int chunkX, int chunkZ) {
        byte[] depths = new byte[WaterMapConstants.CHUNK_AREA];
        int baseX = chunkX * WaterMapConstants.CHUNK_SIZE;
        int baseZ = chunkZ * WaterMapConstants.CHUNK_SIZE;
        int[][] distanceToLand = calculateDistanceToLand(world, baseX, baseZ);

        for (int localZ = 0; localZ < WaterMapConstants.CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < WaterMapConstants.CHUNK_SIZE; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int distance = distanceToLand[localX][localZ];
                depths[WaterMapConstants.index(localX, localZ)] = classify(world, worldX, worldZ, distance);
            }
        }

        return new WaterMapChunk(chunkX, chunkZ, depths);
    }

    public void scanAndSaveChunk(World world, int chunkX, int chunkZ) throws IOException {
        storage.saveChunk(scanChunk(world, chunkX, chunkZ));
    }

    private WaterMapSnapshot snapshotChunk(World world, int chunkX, int chunkZ) {
        int margin = WaterMapConstants.LAND_SEARCH_RADIUS;
        int size = WaterMapConstants.CHUNK_SIZE + margin * 2;
        boolean[] water = new boolean[size * size];
        byte[] biomes = new byte[size * size];
        int baseX = chunkX * WaterMapConstants.CHUNK_SIZE;
        int baseZ = chunkZ * WaterMapConstants.CHUNK_SIZE;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int worldX = baseX + x - margin;
                int worldZ = baseZ + z - margin;
                int index = snapshotIndex(size, x, z);
                water[index] = isWaterColumn(world, worldX, worldZ);
                biomes[index] = biomeValue(world, worldX, worldZ);
            }
        }

        return new WaterMapSnapshot(chunkX, chunkZ, size, margin, water, biomes);
    }

    private WaterMapChunk processSnapshot(WaterMapSnapshot snapshot) {
        byte[] depths = new byte[WaterMapConstants.CHUNK_AREA];
        int[][] distanceToLand = calculateDistanceToLand(snapshot);

        for (int localZ = 0; localZ < WaterMapConstants.CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < WaterMapConstants.CHUNK_SIZE; localX++) {
                int snapshotX = snapshot.margin() + localX;
                int snapshotZ = snapshot.margin() + localZ;
                int distance = distanceToLand[localX][localZ];
                depths[WaterMapConstants.index(localX, localZ)] = classify(snapshot, snapshotX, snapshotZ, distance);
            }
        }

        return new WaterMapChunk(snapshot.chunkX(), snapshot.chunkZ(), depths);
    }

    public boolean renderAroundOrigin(CommandSender sender, World world, int radiusBlocks) {
        if (activeRenderTask != null && !activeRenderTask.isCancelled()) {
            sender.sendMessage(Component.text("Watermap-Render laeuft bereits."));
            return false;
        }

        int minChunkX = Math.floorDiv(-radiusBlocks, WaterMapConstants.CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(radiusBlocks, WaterMapConstants.CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(-radiusBlocks, WaterMapConstants.CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(radiusBlocks, WaterMapConstants.CHUNK_SIZE);
        int totalChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

        sender.sendMessage(Component.text("Watermap-Render gestartet: " + totalChunks + " Chunks um 0/0 in Welt " + world.getName() + "."));

        BukkitRunnable runnable = new BukkitRunnable() {
            private final CompletionService<WaterMapChunk> completedChunks = new ExecutorCompletionService<>(renderExecutor);
            private int chunkX = minChunkX;
            private int chunkZ = minChunkZ;
            private int submittedChunks = 0;
            private int pendingChunks = 0;
            private int renderedChunks = 0;

            @Override
            public void run() {
                try {
                    drainCompletedChunks();

                    for (int i = 0; i < DEFAULT_CHUNKS_PER_TICK && chunkZ <= maxChunkZ && pendingChunks < MAX_PENDING_CHUNKS; i++) {
                        WaterMapSnapshot snapshot = snapshotChunk(world, chunkX, chunkZ);
                        completedChunks.submit(() -> processSnapshot(snapshot));
                        submittedChunks++;
                        pendingChunks++;
                        advanceCursor();
                    }

                    if (submittedChunks == totalChunks && renderedChunks == totalChunks) {
                        storage.renderOverview();
                        sender.sendMessage(Component.text("Watermap fertig: " + renderedChunks + "/" + totalChunks + " Chunks. Dateien liegen in " + storage.root() + "."));
                        activeRenderTask = null;
                        cancel();
                    }
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().severe("Watermap render failed: " + e.getMessage());
                    e.printStackTrace();
                    sender.sendMessage(Component.text("Watermap-Render abgebrochen. Fehler steht in der Konsole."));
                    activeRenderTask = null;
                    cancel();
                }
            }

            private void drainCompletedChunks() throws IOException {
                Future<WaterMapChunk> completed;
                while ((completed = completedChunks.poll()) != null) {
                    try {
                        storage.saveChunk(completed.get(), false);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Watermap worker interrupted.", e);
                    } catch (Exception e) {
                        throw new IOException("Watermap worker failed.", e);
                    }

                    renderedChunks++;
                    pendingChunks--;

                    if (renderedChunks % 500 == 0) {
                        sender.sendMessage(Component.text("Watermap: " + renderedChunks + "/" + totalChunks + " Chunks gerendert."));
                    }
                }
            }

            private void advanceCursor() {
                chunkX++;
                if (chunkX > maxChunkX) {
                    chunkX = minChunkX;
                    chunkZ++;
                }
            }
        };

        activeRenderTask = runnable.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    public WaterMapStorage storage() {
        return storage;
    }

    private byte classify(World world, int worldX, int worldZ, int distanceToLand) {
        if (distanceToLand == 0 || !isWaterColumn(world, worldX, worldZ)) {
            return WaterMapConstants.LAND;
        }

        String biomeKey = biomeKey(world, worldX, worldZ);
        boolean river = isRiver(biomeKey);
        boolean oceanFacing = isOcean(biomeKey) || isNearOceanBiome(world, worldX, worldZ);
        boolean riverFacing = river || isNearRiverBiome(world, worldX, worldZ);

        if (river && !oceanFacing) {
            return WaterMapConstants.FRESH_WATER;
        }

        if (river && !isNavigableRiverWidth(world, worldX, worldZ)) {
            return WaterMapConstants.BLOCKED_RIVER;
        }

        if (river) {
            return WaterMapConstants.waterValueForDistance(distanceToLand, true);
        }
        if (isSwamp(biomeKey)) {
            return WaterMapConstants.SWAMP_WATER;
        }
        if (!oceanFacing && !riverFacing) {
            return WaterMapConstants.FRESH_WATER;
        }

        return WaterMapConstants.waterValueForDistance(distanceToLand, false);
    }

    private byte classify(WaterMapSnapshot snapshot, int x, int z, int distanceToLand) {
        if (distanceToLand == 0 || !snapshotWater(snapshot, x, z)) {
            return WaterMapConstants.LAND;
        }

        byte biome = snapshotBiome(snapshot, x, z);
        boolean river = biome == BIOME_RIVER;
        boolean oceanFacing = biome == BIOME_OCEAN || isNearOceanBiome(snapshot, x, z);
        boolean riverFacing = river || isNearRiverBiome(snapshot, x, z);

        if (river && !oceanFacing) {
            return WaterMapConstants.FRESH_WATER;
        }

        if (river && !isNavigableRiverWidth(snapshot, x, z)) {
            return WaterMapConstants.BLOCKED_RIVER;
        }

        if (river) {
            return WaterMapConstants.waterValueForDistance(distanceToLand, true);
        }
        if (biome == BIOME_SWAMP) {
            return WaterMapConstants.SWAMP_WATER;
        }
        if (!oceanFacing && !riverFacing) {
            return WaterMapConstants.FRESH_WATER;
        }

        return WaterMapConstants.waterValueForDistance(distanceToLand, false);
    }

    private int[][] calculateDistanceToLand(World world, int baseX, int baseZ) {
        int margin = WaterMapConstants.LAND_SEARCH_RADIUS;
        int size = WaterMapConstants.CHUNK_SIZE + margin * 2;
        boolean[][] land = new boolean[size][size];
        int[][] distances = new int[size][size];
        Queue<GridPoint> queue = new ArrayDeque<>();

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int worldX = baseX + x - margin;
                int worldZ = baseZ + z - margin;
                land[x][z] = isLand(world, worldX, worldZ);
            }
        }

        removeSmallLandMasses(land);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (land[x][z]) {
                    distances[x][z] = 0;
                    queue.add(new GridPoint(x, z));
                } else {
                    distances[x][z] = Integer.MAX_VALUE;
                }
            }
        }

        while (!queue.isEmpty()) {
            GridPoint point = queue.poll();
            int nextDistance = distances[point.x()][point.z()] + 1;
            visit(point.x() + 1, point.z(), nextDistance, distances, queue);
            visit(point.x() - 1, point.z(), nextDistance, distances, queue);
            visit(point.x(), point.z() + 1, nextDistance, distances, queue);
            visit(point.x(), point.z() - 1, nextDistance, distances, queue);
        }

        int[][] chunkDistances = new int[WaterMapConstants.CHUNK_SIZE][WaterMapConstants.CHUNK_SIZE];
        for (int localX = 0; localX < WaterMapConstants.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < WaterMapConstants.CHUNK_SIZE; localZ++) {
                int distance = distances[localX + margin][localZ + margin];
                chunkDistances[localX][localZ] = distance == Integer.MAX_VALUE ? margin + 1 : distance;
            }
        }

        return chunkDistances;
    }

    private int[][] calculateDistanceToLand(WaterMapSnapshot snapshot) {
        int size = snapshot.size();
        boolean[][] land = new boolean[size][size];
        int[][] distances = new int[size][size];
        Queue<GridPoint> queue = new ArrayDeque<>();

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                land[x][z] = !snapshotWater(snapshot, x, z);
            }
        }

        removeSmallLandMasses(land);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (land[x][z]) {
                    distances[x][z] = 0;
                    queue.add(new GridPoint(x, z));
                } else {
                    distances[x][z] = Integer.MAX_VALUE;
                }
            }
        }

        while (!queue.isEmpty()) {
            GridPoint point = queue.poll();
            int nextDistance = distances[point.x()][point.z()] + 1;
            visit(point.x() + 1, point.z(), nextDistance, distances, queue);
            visit(point.x() - 1, point.z(), nextDistance, distances, queue);
            visit(point.x(), point.z() + 1, nextDistance, distances, queue);
            visit(point.x(), point.z() - 1, nextDistance, distances, queue);
        }

        int[][] chunkDistances = new int[WaterMapConstants.CHUNK_SIZE][WaterMapConstants.CHUNK_SIZE];
        for (int localX = 0; localX < WaterMapConstants.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < WaterMapConstants.CHUNK_SIZE; localZ++) {
                int distance = distances[localX + snapshot.margin()][localZ + snapshot.margin()];
                chunkDistances[localX][localZ] = distance == Integer.MAX_VALUE ? snapshot.margin() + 1 : distance;
            }
        }

        return chunkDistances;
    }

    private void removeSmallLandMasses(boolean[][] land) {
        boolean[][] visited = new boolean[land.length][land[0].length];
        Queue<GridPoint> queue = new ArrayDeque<>();
        ArrayDeque<GridPoint> component = new ArrayDeque<>();

        for (int x = 0; x < land.length; x++) {
            for (int z = 0; z < land[x].length; z++) {
                if (!land[x][z] || visited[x][z]) {
                    continue;
                }

                component.clear();
                queue.clear();
                queue.add(new GridPoint(x, z));
                visited[x][z] = true;
                boolean touchesBorder = false;

                while (!queue.isEmpty()) {
                    GridPoint point = queue.poll();
                    component.add(point);
                    touchesBorder = touchesBorder || touchesBorder(point, land.length, land[0].length);
                    visitLand(point.x() + 1, point.z(), land, visited, queue);
                    visitLand(point.x() - 1, point.z(), land, visited, queue);
                    visitLand(point.x(), point.z() + 1, land, visited, queue);
                    visitLand(point.x(), point.z() - 1, land, visited, queue);
                }

                if (!touchesBorder && component.size() < WaterMapConstants.MIN_LAND_MASS_BLOCKS) {
                    for (GridPoint point : component) {
                        land[point.x()][point.z()] = false;
                    }
                }
            }
        }
    }

    private void visitLand(int x, int z, boolean[][] land, boolean[][] visited, Queue<GridPoint> queue) {
        if (x < 0 || z < 0 || x >= land.length || z >= land[x].length || visited[x][z] || !land[x][z]) {
            return;
        }

        visited[x][z] = true;
        queue.add(new GridPoint(x, z));
    }

    private boolean touchesBorder(GridPoint point, int width, int height) {
        return point.x() == 0 || point.z() == 0 || point.x() == width - 1 || point.z() == height - 1;
    }

    private void visit(int x, int z, int distance, int[][] distances, Queue<GridPoint> queue) {
        if (x < 0 || z < 0 || x >= distances.length || z >= distances[x].length || distance >= distances[x][z]) {
            return;
        }

        distances[x][z] = distance;
        queue.add(new GridPoint(x, z));
    }

    private boolean isLand(World world, int worldX, int worldZ) {
        return !isWaterColumn(world, worldX, worldZ);
    }

    private boolean isWaterColumn(World world, int worldX, int worldZ) {
        Block surfaceBlock = world.getHighestBlockAt(worldX, worldZ);
        if (surfaceBlock.getY() > WaterMapConstants.WATER_BLOCK_Y && !isWater(surfaceBlock.getType())) {
            return false;
        }

        return isWater(world.getBlockAt(worldX, WaterMapConstants.WATER_BLOCK_Y, worldZ).getType());
    }

    private boolean isRiver(World world, int worldX, int worldZ) {
        return isRiver(biomeKey(world, worldX, worldZ));
    }

    private boolean isRiver(String biomeKey) {
        return biomeKey.contains("river");
    }

    private boolean isSwamp(String biomeKey) {
        return biomeKey.contains("swamp");
    }

    private boolean isOcean(String biomeKey) {
        return biomeKey.contains("ocean");
    }

    private boolean isNearOceanBiome(World world, int worldX, int worldZ) {
        int radius = WaterMapConstants.OCEAN_SHORE_BIOME_RADIUS;
        int step = WaterMapConstants.OCEAN_SHORE_BIOME_STEP;

        for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                if (isOcean(biomeKey(world, worldX + offsetX, worldZ + offsetZ))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNearRiverBiome(World world, int worldX, int worldZ) {
        int radius = WaterMapConstants.WATERWAY_BIOME_RADIUS;
        int step = WaterMapConstants.WATERWAY_BIOME_STEP;

        for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                if (isRiver(biomeKey(world, worldX + offsetX, worldZ + offsetZ))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNearOceanBiome(WaterMapSnapshot snapshot, int x, int z) {
        int radius = WaterMapConstants.OCEAN_SHORE_BIOME_RADIUS;
        int step = WaterMapConstants.OCEAN_SHORE_BIOME_STEP;

        for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                if (snapshotBiome(snapshot, x + offsetX, z + offsetZ) == BIOME_OCEAN) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isNearRiverBiome(WaterMapSnapshot snapshot, int x, int z) {
        int radius = WaterMapConstants.WATERWAY_BIOME_RADIUS;
        int step = WaterMapConstants.WATERWAY_BIOME_STEP;

        for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                if (snapshotBiome(snapshot, x + offsetX, z + offsetZ) == BIOME_RIVER) {
                    return true;
                }
            }
        }

        return false;
    }

    private String biomeKey(World world, int worldX, int worldZ) {
        Biome biome = world.getBiome(worldX, WaterMapConstants.WATER_BLOCK_Y, worldZ);
        return biome.key().value();
    }

    private byte biomeValue(World world, int worldX, int worldZ) {
        String biome = biomeKey(world, worldX, worldZ);
        if (isOcean(biome)) {
            return BIOME_OCEAN;
        }
        if (isRiver(biome)) {
            return BIOME_RIVER;
        }
        if (isSwamp(biome)) {
            return BIOME_SWAMP;
        }
        return BIOME_OTHER;
    }

    private boolean isNavigableRiverWidth(World world, int worldX, int worldZ) {
        int horizontalWidth = riverLineWidth(world, worldX, worldZ, 1, 0);
        int verticalWidth = riverLineWidth(world, worldX, worldZ, 0, 1);
        int diagonalWidth = riverLineWidth(world, worldX, worldZ, 1, 1);
        int antiDiagonalWidth = riverLineWidth(world, worldX, worldZ, 1, -1);
        int estimatedWidth = Math.min(Math.min(horizontalWidth, verticalWidth), Math.min(diagonalWidth, antiDiagonalWidth));
        return estimatedWidth >= WaterMapConstants.MIN_NAVIGABLE_RIVER_WIDTH;
    }

    private boolean isNavigableRiverWidth(WaterMapSnapshot snapshot, int x, int z) {
        int horizontalWidth = riverLineWidth(snapshot, x, z, 1, 0);
        int verticalWidth = riverLineWidth(snapshot, x, z, 0, 1);
        int diagonalWidth = riverLineWidth(snapshot, x, z, 1, 1);
        int antiDiagonalWidth = riverLineWidth(snapshot, x, z, 1, -1);
        int estimatedWidth = Math.min(Math.min(horizontalWidth, verticalWidth), Math.min(diagonalWidth, antiDiagonalWidth));
        return estimatedWidth >= WaterMapConstants.MIN_NAVIGABLE_RIVER_WIDTH;
    }

    private int riverLineWidth(World world, int worldX, int worldZ, int stepX, int stepZ) {
        return 1
                + countRiverWater(world, worldX, worldZ, stepX, stepZ)
                + countRiverWater(world, worldX, worldZ, -stepX, -stepZ);
    }

    private int riverLineWidth(WaterMapSnapshot snapshot, int x, int z, int stepX, int stepZ) {
        return 1
                + countRiverWater(snapshot, x, z, stepX, stepZ)
                + countRiverWater(snapshot, x, z, -stepX, -stepZ);
    }

    private int countRiverWater(World world, int worldX, int worldZ, int stepX, int stepZ) {
        int count = 0;
        int maxDistance = WaterMapConstants.MIN_NAVIGABLE_RIVER_WIDTH;

        for (int distance = 1; distance <= maxDistance; distance++) {
            int sampleX = worldX + stepX * distance;
            int sampleZ = worldZ + stepZ * distance;
            if (!isWaterColumn(world, sampleX, sampleZ) || !isRiver(world, sampleX, sampleZ)) {
                break;
            }
            count++;
        }

        return count;
    }

    private int countRiverWater(WaterMapSnapshot snapshot, int x, int z, int stepX, int stepZ) {
        int count = 0;
        int maxDistance = WaterMapConstants.MIN_NAVIGABLE_RIVER_WIDTH;

        for (int distance = 1; distance <= maxDistance; distance++) {
            int sampleX = x + stepX * distance;
            int sampleZ = z + stepZ * distance;
            if (!snapshotWater(snapshot, sampleX, sampleZ) || snapshotBiome(snapshot, sampleX, sampleZ) != BIOME_RIVER) {
                break;
            }
            count++;
        }

        return count;
    }

    private boolean isWater(Material material) {
        return material == Material.WATER || material == Material.KELP || material == Material.KELP_PLANT || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS;
    }

    private boolean snapshotWater(WaterMapSnapshot snapshot, int x, int z) {
        if (x < 0 || z < 0 || x >= snapshot.size() || z >= snapshot.size()) {
            return false;
        }
        return snapshot.water()[snapshotIndex(snapshot.size(), x, z)];
    }

    private byte snapshotBiome(WaterMapSnapshot snapshot, int x, int z) {
        if (x < 0 || z < 0 || x >= snapshot.size() || z >= snapshot.size()) {
            return BIOME_OTHER;
        }
        return snapshot.biomes()[snapshotIndex(snapshot.size(), x, z)];
    }

    private int snapshotIndex(int size, int x, int z) {
        return z * size + x;
    }

    private record GridPoint(int x, int z) {
    }

    private record WaterMapSnapshot(int chunkX, int chunkZ, int size, int margin, boolean[] water, byte[] biomes) {
    }
}
