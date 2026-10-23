package com.radiimc.hospital;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Hospitals persisted to hospitals.yml so they survive restarts. */
final class HospitalStore {

    private final File file;
    private final Logger logger;
    private final Map<String, Hospital> hospitals = new LinkedHashMap<>();
    private Hospital main;

    HospitalStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        main = read(yaml.getConfigurationSection("main"), "main");
        hospitals.clear();
        ConfigurationSection section = yaml.getConfigurationSection("hospitals");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Hospital h = read(section.getConfigurationSection(key), key);
                if (h != null) {
                    hospitals.put(key.toLowerCase(Locale.ROOT), h);
                }
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (main != null) {
            write(yaml.createSection("main"), main);
        }
        ConfigurationSection section = yaml.createSection("hospitals");
        hospitals.values().forEach(h -> write(section.createSection(h.name()), h));
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    Collection<Hospital> hospitals() {
        return Collections.unmodifiableCollection(hospitals.values());
    }

    Hospital get(String name) {
        return hospitals.get(name.toLowerCase(Locale.ROOT));
    }

    void put(Hospital hospital) {
        hospitals.put(hospital.name().toLowerCase(Locale.ROOT), hospital);
        save();
    }

    void remove(Hospital hospital) {
        hospitals.remove(hospital.name().toLowerCase(Locale.ROOT));
        save();
    }

    Hospital main() {
        return main;
    }

    void setMain(Hospital hospital) {
        main = hospital;
        save();
    }

    private static Hospital read(ConfigurationSection s, String name) {
        if (s == null || s.getString("world") == null) {
            return null;
        }
        return new Hospital(name, s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void write(ConfigurationSection s, Hospital h) {
        s.set("world", h.world());
        s.set("x", h.x());
        s.set("y", h.y());
        s.set("z", h.z());
        s.set("yaw", h.yaw());
        s.set("pitch", h.pitch());
    }
}
