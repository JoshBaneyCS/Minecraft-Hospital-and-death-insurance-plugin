package com.radiimc.deathinsurance;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Insurance policies (player -> coverage expiry, epoch millis), persisted to policies.yml. */
final class PolicyStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Long> expiries = new HashMap<>();

    PolicyStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                expiries.put(UUID.fromString(key), yaml.getLong(key));
            } catch (IllegalArgumentException e) {
                logger.warning("Skipping invalid player id in " + file.getName() + ": " + key);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        expiries.forEach((id, expiry) -> {
            if (expiry > now) {
                yaml.set(id.toString(), expiry);
            }
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    boolean isInsured(UUID id, long now) {
        return expiry(id) > now;
    }

    long expiry(UUID id) {
        return expiries.getOrDefault(id, 0L);
    }

    /** Adds {@code durationMillis} of cover and returns the new expiry. */
    long extend(UUID id, long now, long durationMillis) {
        long expiry = extendedExpiry(now, expiry(id), durationMillis);
        expiries.put(id, expiry);
        save();
        return expiry;
    }

    /** Buying while still covered stacks on top of the remaining time; otherwise cover starts now. */
    static long extendedExpiry(long now, long currentExpiry, long durationMillis) {
        return Math.max(now, currentExpiry) + durationMillis;
    }
}
