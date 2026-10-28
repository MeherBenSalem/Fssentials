package dev.nightbeam.donutessentials.rtp;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Finds a safe random spot: picks a point in the world's ring, loads its chunk asynchronously, checks the ground on
 * the region's own thread, and tries again (up to {@code max-attempts}). The surface is used in the overworld and the
 * End; in the Nether the search scans down from y=120 for a two-block gap on solid ground.
 */
final class RtpSearch {

    private final DonutEssentialsPlugin plugin;

    RtpSearch(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    /** A safe location in {@code world}, or null after every attempt failed. */
    CompletableFuture<Location> find(World world, RtpSettings settings, RtpSettings.WorldSettings area) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        int centerX;
        int centerZ;
        if (area.center() != null) {
            centerX = area.center()[0];
            centerZ = area.center()[1];
        } else {
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getBlockX();
            centerZ = spawn.getBlockZ();
        }
        attempt(world, settings, area, centerX, centerZ, 0, result);
        return result;
    }

    private void attempt(World world, RtpSettings settings, RtpSettings.WorldSettings area, int centerX, int centerZ, int attempt,
                         CompletableFuture<Location> result) {
        if (attempt >= settings.maxAttempts()) {
            result.complete(null);
            return;
        }
        int[] point = RtpMath.point(ThreadLocalRandom.current(), centerX, centerZ, area.minRadius(), area.radius());
        Runnable retry = () -> attempt(world, settings, area, centerX, centerZ, attempt + 1, result);
        world.getChunkAtAsync(point[0] >> 4, point[1] >> 4).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                retry.run();
                return;
            }
            plugin.scheduler().runAtLocation(new Location(world, point[0], 64, point[1]), () -> {
                Location found = null;
                try {
                    found = check(world, point[0], point[1], settings);
                } catch (Throwable ignored) {
                    // A broken chunk is just another failed attempt.
                }
                if (found != null) {
                    result.complete(found);
                } else {
                    retry.run();
                }
            });
        });
    }

    /** The spot at x/z when it is safe, else null. Runs on the region's thread. */
    private Location check(World world, int x, int z, RtpSettings settings) {
        Block ground;
        if (world.getEnvironment() == World.Environment.NETHER) {
            ground = netherGround(world, x, z, settings);
        } else {
            ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            if (ground.getY() <= world.getMinHeight() || !safeGround(ground, settings)) {
                ground = null;
            }
        }
        if (ground == null) {
            return null;
        }
        Object biome = world.getBiome(x, ground.getY() + 1, z);
        String key = biome instanceof Keyed keyed ? keyed.getKey().toString() : String.valueOf(biome);
        if (RtpMath.biomeBlocked(key, settings.blockedBiomes())) {
            return null;
        }
        Location location = new Location(world, x + 0.5, ground.getY() + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f - 180f, 0f);
        return world.getWorldBorder().isInside(location) ? location : null;
    }

    private Block netherGround(World world, int x, int z, RtpSettings settings) {
        int top = Math.min(120, world.getMaxHeight() - 3);
        for (int y = top; y > world.getMinHeight() + 1; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (safeGround(block, settings)) {
                return block;
            }
        }
        return null;
    }

    /** Solid, safe ground with two free, safe blocks above it. */
    private static boolean safeGround(Block ground, RtpSettings settings) {
        if (!ground.getType().isSolid() || settings.unsafeBlocks().contains(ground.getType())) {
            return false;
        }
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        return free(feet, settings) && free(head, settings);
    }

    private static boolean free(Block block, RtpSettings settings) {
        return block.isPassable() && !block.isLiquid() && !settings.unsafeBlocks().contains(block.getType());
    }
}
