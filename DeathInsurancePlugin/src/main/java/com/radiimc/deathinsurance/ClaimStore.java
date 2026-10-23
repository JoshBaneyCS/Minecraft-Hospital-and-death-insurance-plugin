package com.radiimc.deathinsurance;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Belongings the hospital is holding for uninsured players until they pay the bill or give up,
 * persisted to claims.yml so a restart or disconnect never loses them.
 */
final class ClaimStore {

    record Claim(double bill, List<ItemStack> items, int secondsLeft) {
    }

    private final File file;
    private final Logger logger;
    private final Map<UUID, Claim> claims = new HashMap<>();

    ClaimStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                List<ItemStack> items = new ArrayList<>();
                for (String encoded : s.getStringList("items")) {
                    items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
                }
                claims.put(UUID.fromString(key), new Claim(s.getDouble("bill"), items, s.getInt("seconds-left")));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Skipping unreadable claim for " + key + " in " + file.getName(), e);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        claims.forEach((id, claim) -> {
            ConfigurationSection s = yaml.createSection(id.toString());
            s.set("bill", claim.bill());
            s.set("seconds-left", claim.secondsLeft());
            s.set("items", claim.items().stream()
                    .map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList());
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    Claim get(UUID id) {
        return claims.get(id);
    }

    Set<UUID> holders() {
        return Set.copyOf(claims.keySet());
    }

    /** Holds items for a player. Dying again before paying adds to the same bill and restarts the clock. */
    Claim hold(UUID id, double bill, List<ItemStack> items, int seconds) {
        Claim previous = claims.get(id);
        List<ItemStack> all = new ArrayList<>(items);
        double total = bill;
        if (previous != null) {
            all.addAll(0, previous.items());
            total += previous.bill();
        }
        Claim claim = new Claim(total, List.copyOf(all), seconds);
        claims.put(id, claim);
        save();
        return claim;
    }

    Claim remove(UUID id) {
        Claim removed = claims.remove(id);
        if (removed != null) {
            save();
        }
        return removed;
    }

    /** Counts one second off a claim; returns the updated claim, or null if it just ran out (and was removed). */
    Claim tick(UUID id) {
        Claim claim = claims.get(id);
        if (claim == null) {
            return null;
        }
        if (claim.secondsLeft() <= 1) {
            remove(id);
            return null;
        }
        Claim next = new Claim(claim.bill(), claim.items(), claim.secondsLeft() - 1);
        claims.put(id, next);
        return next;
    }
}
