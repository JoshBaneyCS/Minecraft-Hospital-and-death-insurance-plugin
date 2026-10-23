package com.radiimc.hospital;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A named respawn point. Stored by world name so a hospital survives its world being unloaded. */
public record Hospital(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public static Hospital at(String name, Location location) {
        return new Hospital(name, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /** The hospital as a live location, or null when its world is not loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public String describe() {
        return String.format("%s (%s %.0f, %.0f, %.0f)", name, world, x, y, z);
    }
}
