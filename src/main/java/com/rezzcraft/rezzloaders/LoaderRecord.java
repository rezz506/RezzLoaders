package com.rezzcraft.rezzloaders;

import org.bukkit.Location;

import java.util.UUID;

public class LoaderRecord {
    public final UUID id;
    public final UUID owner;
    public final String world;
    public final int x;
    public final int y;
    public final int z;
    public final LoaderSize size;
    public final long createdAtMs;
    public final long expiresAtMs;
    public UUID hologramEntityId;

    public boolean suspended;

    public LoaderRecord(UUID id, UUID owner, String world, int x, int y, int z, LoaderSize size,
                        long createdAtMs, long expiresAtMs) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    public Location getBlockLocation(org.bukkit.Server server) {
        var w = server.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }
}
