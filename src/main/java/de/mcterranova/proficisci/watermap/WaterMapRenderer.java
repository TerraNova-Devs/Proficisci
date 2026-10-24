package de.mcterranova.proficisci.watermap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class WaterMapRenderer {
    public BufferedImage renderRegion(WaterMapRegion region) {
        BufferedImage image = new BufferedImage(WaterMapConstants.REGION_SIZE, WaterMapConstants.REGION_SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int z = 0; z < WaterMapConstants.REGION_SIZE; z++) {
            for (int x = 0; x < WaterMapConstants.REGION_SIZE; x++) {
                image.setRGB(x, z, rgbForRegionPixel(region, x, z));
            }
        }

        return image;
    }

    public void writeOverview(List<WaterMapRegion> regions, Path overviewPath) throws IOException {
        if (regions.isEmpty()) {
            return;
        }

        int minRegionX = regions.stream().map(WaterMapRegion::regionX).min(Comparator.naturalOrder()).orElseThrow();
        int maxRegionX = regions.stream().map(WaterMapRegion::regionX).max(Comparator.naturalOrder()).orElseThrow();
        int minRegionZ = regions.stream().map(WaterMapRegion::regionZ).min(Comparator.naturalOrder()).orElseThrow();
        int maxRegionZ = regions.stream().map(WaterMapRegion::regionZ).max(Comparator.naturalOrder()).orElseThrow();

        int width = (maxRegionX - minRegionX + 1) * WaterMapConstants.REGION_SIZE;
        int height = (maxRegionZ - minRegionZ + 1) * WaterMapConstants.REGION_SIZE;
        BufferedImage overview = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (WaterMapRegion region : regions) {
            BufferedImage tile = renderRegion(region);
            int x = (region.regionX() - minRegionX) * WaterMapConstants.REGION_SIZE;
            int z = (region.regionZ() - minRegionZ) * WaterMapConstants.REGION_SIZE;
            overview.getGraphics().drawImage(tile, x, z, null);
        }

        Files.createDirectories(overviewPath.getParent());
        ImageIO.write(overview, "png", overviewPath.toFile());
    }

    private int rgbForRegionPixel(WaterMapRegion region, int x, int z) {
        byte center = region.valueAt(x, z);
        if (!WaterMapConstants.isOceanWater(center)) {
            return WaterMapConstants.rgbForDepth(center);
        }

        int red = 0;
        int green = 0;
        int blue = 0;
        int totalWeight = 0;

        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                int sampleX = x + offsetX;
                int sampleZ = z + offsetZ;
                if (sampleX < 0 || sampleZ < 0 || sampleX >= WaterMapConstants.REGION_SIZE || sampleZ >= WaterMapConstants.REGION_SIZE) {
                    continue;
                }

                byte sample = region.valueAt(sampleX, sampleZ);
                if (!WaterMapConstants.isOceanWater(sample)) {
                    continue;
                }

                int weight = sampleWeight(offsetX, offsetZ);
                int rgb = WaterMapConstants.rgbForDepth(sample);
                red += ((rgb >> 16) & 0xFF) * weight;
                green += ((rgb >> 8) & 0xFF) * weight;
                blue += (rgb & 0xFF) * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return WaterMapConstants.rgbForDepth(center);
        }

        return 0xFF000000
                | ((red / totalWeight) << 16)
                | ((green / totalWeight) << 8)
                | (blue / totalWeight);
    }

    private int sampleWeight(int offsetX, int offsetZ) {
        if (offsetX == 0 && offsetZ == 0) {
            return 8;
        }
        if (offsetX == 0 || offsetZ == 0) {
            return 3;
        }
        return 1;
    }
}
