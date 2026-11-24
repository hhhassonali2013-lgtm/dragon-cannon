package me.dogs.dragoncannon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DragonCannonPlugin extends JavaPlugin implements TabExecutor {

    private final Map<UUID, Long> lastFired = new HashMap<>();
    private NamespacedKey modeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        modeKey = new NamespacedKey(this, "cannon_mode");

        PluginCommand command = getCommand("dragoncannon");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(new CannonListener(this), this);
        getLogger().info("DragonBreathCannon enabled.");
    }

    NamespacedKey modeKey() {
        return modeKey;
    }

    static Component msg(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("dragoncannon.reload")) {
                noPermission(sender);
                return true;
            }
            reloadConfig();
            sender.sendMessage(msg("DragonBreathCannon config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("give")) {
            return handleGive(sender, args);
        }

        CannonMode mode = CannonMode.fromString(sub);
        if (mode == null) {
            sendUsage(sender, label);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("Only players can fire the cannon.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("dragoncannon.use")) {
            noPermission(sender);
            return true;
        }

        Location target;
        if (args.length >= 4) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                target = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException ex) {
                player.sendMessage(msg("Coordinates must be numbers: /" + label + " " + sub + " <x> <y> <z>",
                        NamedTextColor.RED));
                return true;
            }
        } else {
            target = findTarget(player);
            if (target == null) {
                player.sendMessage(msg("Look at a block in range, or use /" + label + " " + sub + " <x> <y> <z>.",
                        NamedTextColor.RED));
                return true;
            }
        }

        tryFire(player, mode, target);
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dragoncannon.give")) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg("Usage: /dbc give <normal|stab> [player]", NamedTextColor.YELLOW));
            return true;
        }
        CannonMode mode = CannonMode.fromString(args[1]);
        if (mode == null) {
            sender.sendMessage(msg("Mode must be normal or stab.", NamedTextColor.RED));
            return true;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else {
            target = (sender instanceof Player self) ? self : null;
        }
        if (target == null) {
            sender.sendMessage(msg("Player not found.", NamedTextColor.RED));
            return true;
        }

        ItemStack item = createCannonItem(mode);
        if (!target.getInventory().addItem(item).isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }
        sender.sendMessage(msg("Gave the " + mode.label() + " cannon to " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(msg("/" + label + " normal [x y z]  - circles (orbital strike style)", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(msg("/" + label + " stab [x y z]    - stab shot (tight column)", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(msg("/" + label + " give <normal|stab> [player]", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(msg("/" + label + " reload", NamedTextColor.LIGHT_PURPLE));
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(msg("You don't have permission to do that.", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            addMatches(out, args[0], List.of("normal", "stab", "give", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            addMatches(out, args[1], List.of("normal", "stab"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            addMatches(out, args[2], names);
        }
        return out;
    }

    private static void addMatches(List<String> out, String typed, List<String> options) {
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
    }

    // ------------------------------------------------------------------
    // Firing
    // ------------------------------------------------------------------
    Location findTarget(Player player) {
        double range = getConfig().getDouble("max-target-distance", 256.0);
        RayTraceResult hit = player.rayTraceBlocks(range);
        if (hit == null) {
            return null;
        }
        Vector pos = hit.getHitPosition();
        return new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    boolean tryFire(Player shooter, CannonMode mode, Location target) {
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (getConfig().getDouble("cooldown-seconds", 10.0) * 1000.0);
        Long last = lastFired.get(shooter.getUniqueId());
        if (last != null && now - last < cooldownMs && !shooter.hasPermission("dragoncannon.bypasscooldown")) {
            long secondsLeft = (cooldownMs - (now - last) + 999) / 1000;
            shooter.sendMessage(msg("Cannon recharging: " + secondsLeft + "s left.", NamedTextColor.RED));
            return false;
        }
        lastFired.put(shooter.getUniqueId(), now);

        target.getWorld().playSound(target, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.8f);
        new CannonFiring(this, shooter, mode, target).start();

        shooter.sendMessage(msg(String.format(Locale.ROOT,
                "Orbital Dragon Breath Cannon (%s) fired at %.0f, %.0f, %.0f.",
                mode.label(), target.getX(), target.getY(), target.getZ()), NamedTextColor.DARK_PURPLE));
        return true;
    }

    // ------------------------------------------------------------------
    // Cannon item (right click to fire at the block you look at)
    // ------------------------------------------------------------------
    ItemStack createCannonItem(CannonMode mode) {
        ItemStack item = new ItemStack(Material.DRAGON_BREATH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Orbital Dragon Breath Cannon (" + mode.label() + ")",
                NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right click to fire at the block you look at.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.configKey());
        item.setItemMeta(meta);
        return item;
    }
}
