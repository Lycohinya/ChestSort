package de.jeff_media.chestsort.api;

import de.jeff_media.chestsort.ChestSortPlugin;
import de.jeff_media.chestsort.utils.SchedulerUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * On Folia, sorting runs on the thread owning the inventory: immediately when called from that thread,
 * otherwise on a later tick of the owning entity or region.
 */
public final class ChestSortAPI {

    private ChestSortAPI() {
    }

    public static void sortInventory(@NotNull Inventory inventory) {
        SchedulerUtils.runForInventory(inventory, () -> ChestSortPlugin.getInstance().getOrganizer().sortInventory(inventory));
    }

    public static void sortInventory(@NotNull Inventory inventory, int startSlot, int endSlot) {
        SchedulerUtils.runForInventory(inventory, () -> ChestSortPlugin.getInstance().getOrganizer().sortInventory(inventory, startSlot, endSlot));
    }

    public static boolean hasSortingEnabled(@NotNull Player player) {
        return ChestSortPlugin.getInstance().isSortingEnabled(player);
    }

    public static void setSortable(@NotNull Inventory inv) {
        ChestSortPlugin.getInstance().getOrganizer().setSortable(inv);
    }

    public static void setUnsortable(@NotNull Inventory inv) {
        ChestSortPlugin.getInstance().getOrganizer().setUnsortable(inv);
    }
}
