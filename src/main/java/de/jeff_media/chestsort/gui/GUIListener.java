package de.jeff_media.chestsort.gui;

import de.jeff_media.chestsort.ChestSortPlugin;
import de.jeff_media.chestsort.data.PlayerSetting;
import de.jeff_media.chestsort.gui.tracker.CustomGUITracker;
import de.jeff_media.chestsort.gui.tracker.CustomGUIType;
import de.jeff_media.chestsort.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class GUIListener implements Listener {

    private static final ChestSortPlugin plugin = ChestSortPlugin.getInstance();

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (CustomGUITracker.getType(event.getView()) != CustomGUIType.NEW) {
            // Buttons only work inside ChestSort's GUI, never as items carried into other inventories
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Inventory events run on the clicking player's thread
        PlayerSetting setting = plugin.registerPlayerIfNeeded(player);
        ItemMeta meta = clicked.getItemMeta();

        String function = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "function"), PersistentDataType.STRING, "");
        List<String> userCommands = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "user-commands"), PersistentDataType.LIST.strings(), List.of());
        List<String> adminCommands = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "admin-commands"), PersistentDataType.LIST.strings(), List.of());

        executeCommands(player, player, userCommands);
        executeCommands(player, Bukkit.getConsoleSender(), adminCommands);

        switch (function) {
            case "leftclick" -> setting.toggleLeftClick();
            case "rightclick" -> setting.toggleRightClick();
            case "shiftclick" -> setting.toggleShiftClick();
            case "middleclick" -> setting.toggleMiddleClick();
            case "shiftrightclick" -> setting.toggleShiftRightClick();
            case "doubleclick" -> setting.toggleDoubleClick();
            case "outside" -> setting.toggleLeftClickOutside();
            case "autosorting" -> setting.toggleChestSorting();
            case "autoinvsorting" -> setting.toggleInvSorting();
            default -> {
                return;
            }
        }
        plugin.savePlayerSetting(player, setting);

        // Inventories must not be opened from inside a click event; reopen on the player's next tick
        player.getScheduler().run(plugin, task -> {
            if (CustomGUITracker.getType(player.getOpenInventory()) == CustomGUIType.NEW) {
                new NewUI(player).showGUI();
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        CustomGUITracker.close(event.getView());
    }

    private void executeCommands(Player player, CommandSender sender, List<String> commands) {
        for (String command : commands) {
            String commandLine = command.replace("{player}", player.getName());
            if (sender instanceof Player) {
                plugin.getServer().dispatchCommand(sender, commandLine);
            } else {
                // Console commands execute on the global region, not on the player's region
                SchedulerUtils.runGlobal(() -> plugin.getServer().dispatchCommand(sender, commandLine));
            }
        }
    }
}
