package com.example.deathinsurance;

import com.example.hospital.HospitalPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathInsurancePlugin extends JavaPlugin implements Listener {
    private Economy economy;
    private Map<UUID, Boolean> hasInsurance = new HashMap<>();
    private HospitalPlugin hospitalPlugin;
    private FileConfiguration settings;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        hospitalPlugin = (HospitalPlugin) Bukkit.getPluginManager().getPlugin("HospitalPlugin");
        if (hospitalPlugin == null) {
            getLogger().severe("HospitalPlugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        loadConfig();
    }

    private void loadConfig() {
        saveDefaultConfig();
        settings = getConfig();

        if (!settings.contains("insurance-co-pay")) {
            settings.set("insurance-co-pay", 50.0); // default co-pay
        }
        if (!settings.contains("payment-per-xp-level")) {
            settings.set("payment-per-xp-level", 10.0); // default payment per XP level
        }
        if (!settings.contains("main-hospital-flat-fee")) {
            settings.set("main-hospital-flat-fee", 20.0); // default flat fee at main hospital
        }

        saveConfig();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        boolean isInsured = hasInsurance.getOrDefault(playerUUID, false);

        Location respawnLocation = hospitalPlugin.getClosestHospital(player);
        if (respawnLocation == null) {
            respawnLocation = hospitalPlugin.getMainHospital();
        }

        // If respawn is at the main hospital, only charge the flat fee
        if (respawnLocation.equals(hospitalPlugin.getMainHospital())) {
            handleMainHospitalRespawn(player);
        } else {
            if (isInsured) {
                handleInsuredPlayer(player);
            } else {
                handleUninsuredPlayer(player);
            }
        }
    }

    private void handleMainHospitalRespawn(Player player) {
        double flatFee = settings.getDouble("main-hospital-flat-fee", 20.0);

        if (economy.has(player, flatFee)) {
            economy.withdrawPlayer(player, flatFee);
            player.sendMessage("You have been respawned at the main hospital. You paid a flat fee of " + flatFee + ".");
        } else {
            player.sendMessage("You do not have enough money to pay the flat fee for the main hospital respawn.");
        }
    }

    private void handleInsuredPlayer(Player player) {
        double coPay = settings.getDouble("insurance-co-pay", 50.0);

        if (economy.has(player, coPay)) {
            economy.withdrawPlayer(player, coPay);
            player.sendMessage("You paid a co-pay of " + coPay + and kept your inventory.");
        } else {
            player.sendMessage("You cannot afford the co-pay, but since you have insurance, your inventory is kept.");
        }
    }

    private void handleUninsuredPlayer(Player player) {
        int xpLevel = player.getLevel();
        double amountToPay = xpLevel * settings.getDouble("payment-per-xp-level", 10.0);

        player.sendMessage("You are uninsured. You must pay " + amountToPay + " or lose your inventory.");

        if (economy.has(player, amountToPay)) {
            player.sendMessage("Type /pay or /clearinv to proceed.");
            // Implement the logic to wait for the player's choice (out of bank or clear inventory)
        } else {
            player.getInventory().clear();
            player.sendMessage("You couldn't afford the payment. Your inventory has been cleared.");
        }
    }

    // Command to set insurance
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("insurance")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("buy")) {
                UUID playerUUID = player.getUniqueId();
                hasInsurance.put(playerUUID, true);
                player.sendMessage("You now have health insurance.");
                return true;
            }
        }

        return false;
    }
}
