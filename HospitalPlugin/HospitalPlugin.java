package com.baneymc.hospital;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HospitalPlugin extends JavaPlugin implements Listener {
    private Map<UUID, Location> hospitals = new HashMap<>();
    private Location mainHospital;
    private FileConfiguration settings;

    @Override
    public void onEnable() {
        // Register events for respawn handling
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Load the configuration
        loadConfig();
    }

    private void loadConfig() {
        // Load or create the settings.yml file
        File configFile = new File(getDataFolder(), "settings.yml");
        if (!configFile.exists()) {
            saveResource("settings.yml", false);  // Copy from plugin resources to data folder
        }
        settings = YamlConfiguration.loadConfiguration(configFile);

        // Add default values if they don't exist
        if (!settings.contains("hospital-radius")) {
            settings.set("hospital-radius", 500);  // Default 500 blocks
            saveSettings();
        }
    }

    private void saveSettings() {
        try {
            settings.save(new File(getDataFolder(), "settings.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;
        Location playerLoc = player.getLocation();
        UUID playerUUID = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("hospital")) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "set":
                        if (args.length > 1 && args[1].equalsIgnoreCase("main")) {
                            if (player.hasPermission("hospital.setmain")) {
                                mainHospital = playerLoc;
                                player.sendMessage("Main hospital set at your location.");
                            } else {
                                player.sendMessage("You do not have permission to set the main hospital.");
                            }
                        } else if (args.length > 1 && args[1].equalsIgnoreCase("hospital")) {
                            if (player.hasPermission("hospital.sethospital")) {
                                hospitals.put(playerUUID, playerLoc);
                                player.sendMessage("Hospital set at your location.");
                            } else {
                                player.sendMessage("You do not have permission to set hospitals.");
                            }
                        }
                        break;
                    case "remove":
                        if (player.hasPermission("hospital.removehospital")) {
                            hospitals.remove(playerUUID);
                            player.sendMessage("Removed the closest hospital.");
                        } else {
                            player.sendMessage("You do not have permission to remove hospitals.");
                        }
                        break;
                    case "removemain":
                        if (player.hasPermission("hospital.removemain")) {
                            mainHospital = null;
                            player.sendMessage("Main hospital removed.");
                        } else {
                            player.sendMessage("You do not have permission to remove the main hospital.");
                        }
                        break;
                    default:
                        player.sendMessage("Invalid command. Use /hospital set or /hospital remove.");
                        break;
                }
            } else {
                player.sendMessage("Specify a subcommand.");
            }
        }
        return true;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location closestHospital = getClosestHospital(player);

        if (closestHospital != null) {
            event.setRespawnLocation(closestHospital);
        } else if (mainHospital != null) {
            event.setRespawnLocation(mainHospital);
        }
    }

    private Location getClosestHospital(Player player) {
        Location playerLoc = player.getLocation();
        Location closest = null;
        double minDistance = settings.getDouble("hospital-radius", 500);  // Retrieve from settings.yml

        for (Location hospital : hospitals.values()) {
            double distance = playerLoc.distance(hospital);
            if (distance < minDistance) {
                minDistance = distance;
                closest = hospital;
            }
        }

        return closest;
    }
}
