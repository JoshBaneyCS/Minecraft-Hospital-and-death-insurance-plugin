package com.radiimc.deathinsurance;

import com.radiimc.hospital.HospitalMatch;
import com.radiimc.hospital.HospitalPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class DeathInsurancePlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, List<String>> pendingNotices = new HashMap<>();
    private HospitalPlugin hospitals;
    private PolicyStore policies;
    private ClaimStore claims;
    private YamlConfiguration settings;
    private Economy economy;

    @Override
    public void onEnable() {
        hospitals = JavaPlugin.getPlugin(HospitalPlugin.class);
        loadSettings();
        policies = new PolicyStore(new File(getDataFolder(), "policies.yml"), getLogger());
        policies.load();
        claims = new ClaimStore(new File(getDataFolder(), "claims.yml"), getLogger());
        claims.load();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("insurance")).setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tickClaims, 20L, 20L);

        // EssentialsX registers its Vault economy during its own enable, which may come after ours,
        // so only check for a provider once the server has finished starting.
        getServer().getScheduler().runTask(this, () -> {
            if (economy() == null) {
                getLogger().severe("No Vault economy provider found. Fees and policy purchases will fail until one is installed.");
            } else {
                getLogger().info("Using economy: " + economy.getName());
            }
        });
    }

    @Override
    public void onDisable() {
        if (claims != null) {
            claims.save();  // persist the remaining time on held belongings
        }
    }

    private void loadSettings() {
        File file = new File(getDataFolder(), "settings.yml");
        if (!file.exists()) {
            saveResource("settings.yml", false);
        }
        settings = YamlConfiguration.loadConfiguration(file);
        try (Reader defaults = new InputStreamReader(Objects.requireNonNull(getResource("settings.yml")), StandardCharsets.UTF_8)) {
            settings.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not read default settings", e);
        }
    }

    private Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            economy = rsp == null ? null : rsp.getProvider();
        }
        return economy;
    }

    // ---- deaths --------------------------------------------------------------------------------

    // HIGHEST so any plugin that keeps inventory first (EssentialsX applies essentials.keepinv at LOW) is
    // seen via getKeepInventory(); holding items for a death that keeps them anyway would duplicate them.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;  // keepInventory gamerule, or another plugin already protects this death
        }
        Player player = event.getEntity();
        Optional<HospitalMatch> match = hospitals.findHospital(player.getLocation());
        if (match.isEmpty()) {
            return;  // no hospital to take them to: an ordinary death
        }
        UUID id = player.getUniqueId();

        if (match.get().main()) {
            keepInventory(event);
            double fee = settings.getDouble("main-hospital-flat-fee");
            notice(id, charge(player, fee)
                    ? "<aqua>You were taken to the main hospital and paid a flat fee of <white>" + money(fee) + "</white>. You kept your belongings."
                    : "<aqua>You were taken to the main hospital. You couldn't cover the <white>" + money(fee) + "</white> fee, but you kept your belongings.");
        } else if (policies.isInsured(id, System.currentTimeMillis())) {
            keepInventory(event);
            double coPay = settings.getDouble("insurance-co-pay");
            notice(id, charge(player, coPay)
                    ? "<green>Your insurance covered you. You paid a co-pay of <white>" + money(coPay) + "</white> and kept your inventory."
                    : "<green>You couldn't afford the <white>" + money(coPay) + "</white> co-pay, but since you're insured, your inventory was kept.");
        } else {
            billUninsured(event, player);
        }
    }

    private void billUninsured(PlayerDeathEvent event, Player player) {
        List<ItemStack> items = event.getDrops().stream()
                .filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        UUID id = player.getUniqueId();
        double bill = player.getLevel() * settings.getDouble("payment-per-xp-level");
        if (bill <= 0) {
            keepInventory(event);
            notice(id, "<yellow>You're uninsured, but at your level the hospital waived the bill. You kept your belongings.");
            return;
        }
        Economy eco = economy();
        if (eco != null && eco.has(player, bill)) {
            event.getDrops().clear();
            ClaimStore.Claim claim = claims.hold(id, bill, items, settings.getInt("claim-timeout-seconds"));
            notice(id, claimMessage(claim));
        } else if (settings.getBoolean("destroy-unpaid-items")) {
            event.getDrops().clear();
            notice(id, "<red>You're uninsured and couldn't afford the <white>" + money(bill) + "</white> hospital bill. Your inventory was lost.");
        } else {
            notice(id, "<red>You're uninsured and couldn't afford the <white>" + money(bill) + "</white> hospital bill. Your belongings were left where you died.");
        }
    }

    private static void keepInventory(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Wait a tick so the message lands after the death screen closes.
        getServer().getScheduler().runTask(this, () -> {
            List<String> notices = pendingNotices.remove(player.getUniqueId());
            if (notices != null && player.isOnline()) {
                notices.forEach(player::sendRichMessage);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ClaimStore.Claim claim = claims.get(event.getPlayer().getUniqueId());
        if (claim != null && !event.getPlayer().isDead()) {
            event.getPlayer().sendRichMessage(claimMessage(claim));
        }
    }

    // The clock on held belongings only runs while the player is online and alive to pay.
    private void tickClaims() {
        for (UUID id : claims.holders()) {
            Player player = getServer().getPlayer(id);
            if (player == null || player.isDead()) {
                continue;
            }
            ClaimStore.Claim claim = claims.tick(id);
            if (claim == null) {
                player.sendRichMessage("<red>You didn't settle your hospital bill in time. Your held belongings are gone.");
            } else if (claim.secondsLeft() == 60) {
                player.sendRichMessage("<yellow>One minute left to settle your hospital bill: "
                        + "<click:run_command:'/insurance pay'><green>[Pay " + money(claim.bill()) + "]</green></click>");
            }
        }
    }

    // ---- commands ------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("insurance.admin")) {
                sender.sendRichMessage("<red>You do not have permission to do that.");
                return true;
            }
            loadSettings();
            sender.sendRichMessage("<green>DeathInsurance settings reloaded.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players can use this command.");
            return true;
        }
        switch (sub) {
            case "buy" -> buy(player);
            case "status" -> status(player);
            case "pay" -> pay(player);
            case "abandon" -> abandon(player);
            default -> player.sendRichMessage("<gray>Usage: <white>/insurance [buy|status|pay|abandon]");
        }
        return true;
    }

    private void buy(Player player) {
        double price = settings.getDouble("insurance-price");
        if (!charge(player, price)) {
            player.sendRichMessage("<red>A policy costs <white>" + money(price) + "</white>. You can't afford it right now.");
            return;
        }
        long now = System.currentTimeMillis();
        long expiry = policies.extend(player.getUniqueId(), now, Duration.ofHours(settings.getLong("insurance-duration-hours")).toMillis());
        player.sendRichMessage("<green>You're insured! You paid <white>" + money(price) + "</white>; cover lasts <white>"
                + formatDuration(expiry - now) + "</white>.");
    }

    private void status(Player player) {
        long left = policies.expiry(player.getUniqueId()) - System.currentTimeMillis();
        if (left > 0) {
            player.sendRichMessage("<green>You're insured for another <white>" + formatDuration(left)
                    + "</white>. Co-pay per death: <white>" + money(settings.getDouble("insurance-co-pay")) + "</white>.");
        } else {
            player.sendRichMessage("<yellow>You're not insured. <click:run_command:'/insurance buy'><green>[Buy a policy]</green></click>"
                    + " <gray>for <white>" + money(settings.getDouble("insurance-price")) + "</white>, covering <white>"
                    + formatDuration(Duration.ofHours(settings.getLong("insurance-duration-hours")).toMillis()) + "</white>.");
        }
        ClaimStore.Claim claim = claims.get(player.getUniqueId());
        if (claim != null) {
            player.sendRichMessage(claimMessage(claim));
        }
    }

    private void pay(Player player) {
        ClaimStore.Claim claim = claims.get(player.getUniqueId());
        if (claim == null) {
            player.sendRichMessage("<gray>The hospital isn't holding anything for you.");
            return;
        }
        if (player.isDead()) {
            player.sendRichMessage("<red>Respawn first, then settle your bill.");
            return;
        }
        if (!charge(player, claim.bill())) {
            player.sendRichMessage("<red>You can't afford the <white>" + money(claim.bill()) + "</white> bill yet.");
            return;
        }
        claims.remove(player.getUniqueId());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(claim.items().toArray(ItemStack[]::new));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendRichMessage("<green>You paid <white>" + money(claim.bill()) + "</white>. Your belongings were returned"
                + (overflow.isEmpty() ? "." : "; whatever didn't fit is at your feet."));
    }

    private void abandon(Player player) {
        if (claims.remove(player.getUniqueId()) == null) {
            player.sendRichMessage("<gray>The hospital isn't holding anything for you.");
        } else {
            player.sendRichMessage("<gray>You gave up your held belongings.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("buy", "status", "pay", "abandon"));
        if (sender.hasPermission("insurance.admin")) {
            options.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(prefix)).toList();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private boolean charge(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        Economy eco = economy();
        return eco != null && eco.has(player, amount) && eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    private String money(double amount) {
        Economy eco = economy();
        return eco != null ? eco.format(amount) : String.format(Locale.ROOT, "$%.2f", amount);
    }

    private String claimMessage(ClaimStore.Claim claim) {
        return "<red>You're uninsured. The hospital is holding your belongings until you pay <white>" + money(claim.bill())
                + "</white>. <click:run_command:'/insurance pay'><green>[Pay]</green></click> "
                + "<click:run_command:'/insurance abandon'><gray>[Give them up]</gray></click> "
                + "<dark_gray>(" + formatDuration(claim.secondsLeft() * 1000L) + " left while online)";
    }

    private void notice(UUID id, String message) {
        pendingNotices.computeIfAbsent(id, k -> new ArrayList<>()).add(message);
    }

    static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        List<String> parts = Stream.of(
                d.toDays() > 0 ? d.toDays() + "d" : null,
                d.toHoursPart() > 0 ? d.toHoursPart() + "h" : null,
                d.toMinutesPart() > 0 ? d.toMinutesPart() + "m" : null,
                d.toDays() == 0 && d.toHours() == 0 && d.toSecondsPart() > 0 ? d.toSecondsPart() + "s" : null
        ).filter(Objects::nonNull).toList();
        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }
}
