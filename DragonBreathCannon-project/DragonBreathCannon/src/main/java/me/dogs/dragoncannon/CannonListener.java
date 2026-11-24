package me.dogs.dragoncannon;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class CannonListener implements Listener {

    private final DragonCannonPlugin plugin;

    CannonListener(DragonCannonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DRAGON_BREATH || !item.hasItemMeta()) {
            return;
        }
        String tag = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.modeKey(), PersistentDataType.STRING);
        CannonMode mode = CannonMode.fromString(tag);
        if (mode == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("dragoncannon.use")) {
            player.sendMessage(DragonCannonPlugin.msg("You don't have permission to use this.", NamedTextColor.RED));
            return;
        }
        Location target = plugin.findTarget(player);
        if (target == null) {
            player.sendMessage(DragonCannonPlugin.msg("Look at a block in range to fire.", NamedTextColor.RED));
            return;
        }
        plugin.tryFire(player, mode, target);
    }
}
