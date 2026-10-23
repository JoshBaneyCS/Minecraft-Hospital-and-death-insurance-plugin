package com.radiimc.hospital;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class HospitalPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private HospitalStore store;
    private FileConfiguration settings;

    @Override
    public void onEnable() {
        loadSettings();
        store = new HospitalStore(new File(getDataFolder(), "hospitals.yml"), getLogger());
        store.load();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("hospital")).setExecutor(this);

        getLogger().info("Loaded " + store.hospitals().size() + " hospital(s); main hospital "
                + (store.main() != null ? "is " + store.main().describe() : "is not set") + ".");
    }

    private void loadSettings() {
        File file = new File(getDataFolder(), "settings.yml");
        if (!file.exists()) {
            saveResource("settings.yml", false);
        }
        settings = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Where a player who died at {@code deathLocation} wakes up: the closest hospital within
     * {@code hospital-radius}, otherwise the main hospital. Empty when neither applies.
     */
    public Optional<HospitalMatch> findHospital(Location deathLocation) {
        if (deathLocation != null && deathLocation.getWorld() != null) {
            Optional<Hospital> nearby = HospitalFinder.closest(store.hospitals(), deathLocation.getWorld().getName(),
                    deathLocation.getX(), deathLocation.getY(), deathLocation.getZ(),
                    settings.getDouble("hospital-radius", 500));
            if (nearby.isPresent()) {
                return Optional.of(new HospitalMatch(nearby.get(), false));
            }
        }
        Hospital main = store.main();
        if (main != null && main.toLocation() != null) {
            return Optional.of(new HospitalMatch(main, true));
        }
        return Optional.empty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        deathLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation());
    }

    // HIGHEST so we run after EssentialsSpawn (which uses HIGH) and the hospital wins.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location died = deathLocations.remove(event.getPlayer().getUniqueId());
        if (died == null || event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }
        if (settings.getBoolean("respect-bed-spawns", false) && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        findHospital(died).map(match -> match.hospital().toLocation()).ifPresent(event::setRespawnLocation);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendRichMessage("<gray>Usage: <white>/hospital <set main|set hospital [name]|remove [name]|removemain|list>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(sender, args);
            case "remove" -> remove(sender, args);
            case "removemain" -> {
                if (!check(sender, "hospital.removemain")) return true;
                store.setMain(null);
                sender.sendRichMessage("<green>Main hospital removed.");
            }
            case "list" -> list(sender);
            default -> sender.sendRichMessage("<red>Unknown subcommand. Use /hospital set, remove, removemain or list.");
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players can place hospitals.");
            return;
        }
        String kind = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (kind.equals("main")) {
            if (!check(sender, "hospital.setmain")) return;
            store.setMain(Hospital.at("main", player.getLocation()));
            sender.sendRichMessage("<green>Main hospital set at your location.");
        } else if (kind.equals("hospital")) {
            if (!check(sender, "hospital.sethospital")) return;
            String name = args.length > 2 ? args[2] : nextFreeName();
            boolean replaced = store.get(name) != null;
            store.put(Hospital.at(name, player.getLocation()));
            sender.sendRichMessage("<green>Hospital <white>" + name + "</white> " + (replaced ? "moved to" : "set at") + " your location.");
        } else {
            sender.sendRichMessage("<red>Use /hospital set main or /hospital set hospital [name].");
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (!check(sender, "hospital.removehospital")) return;
        Hospital target;
        if (args.length > 1) {
            target = store.get(args[1]);
        } else if (sender instanceof Player player) {
            Location here = player.getLocation();
            target = HospitalFinder.closest(store.hospitals(), here.getWorld().getName(),
                    here.getX(), here.getY(), here.getZ(), Double.MAX_VALUE).orElse(null);
        } else {
            sender.sendRichMessage("<red>From the console, name the hospital: /hospital remove <name>");
            return;
        }
        if (target == null) {
            sender.sendRichMessage("<red>No such hospital.");
            return;
        }
        store.remove(target);
        sender.sendRichMessage("<green>Removed hospital <white>" + target.describe() + "</white>.");
    }

    private void list(CommandSender sender) {
        if (!check(sender, "hospital.list")) return;
        sender.sendRichMessage("<aqua>Main hospital: <white>" + (store.main() != null ? store.main().describe() : "not set"));
        if (store.hospitals().isEmpty()) {
            sender.sendRichMessage("<gray>No other hospitals. Add one with /hospital set hospital <name>.");
        }
        store.hospitals().stream().sorted(Comparator.comparing(Hospital::name))
                .forEach(h -> sender.sendRichMessage("<gray> - <white>" + h.describe()));
    }

    private String nextFreeName() {
        int n = store.hospitals().size() + 1;
        while (store.get("hospital-" + n) != null) {
            n++;
        }
        return "hospital-" + n;
    }

    private static boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendRichMessage("<red>You do not have permission to do that.");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        Stream<String> options = switch (args.length) {
            case 1 -> Stream.of("set", "remove", "removemain", "list");
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> Stream.of("main", "hospital");
                case "remove" -> store.hospitals().stream().map(Hospital::name);
                default -> Stream.empty();
            };
            default -> Stream.empty();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
