package de.jeff_media.chestsort.utils;

import de.jeff_media.chestsort.ChestSortPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Runs work on the thread that owns the state it touches (Folia region ownership).
 * Work that is already on the owning thread runs immediately.
 */
public final class SchedulerUtils {

    // An entity container may cross a region border between scheduling and running; re-check a few times.
    private static final int MAX_INVENTORY_HOPS = 3;

    private SchedulerUtils() {
    }

    /**
     * Runs the task on the thread owning the entity. Does nothing if the entity has been removed
     * (e.g. the player logged out) before the task could run.
     */
    public static void runForEntity(@NotNull Entity entity, @NotNull Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }
        if (!ChestSortPlugin.getInstance().isEnabled()) {
            return;
        }
        entity.getScheduler().run(ChestSortPlugin.getInstance(), scheduledTask -> task.run(), null);
    }

    /**
     * Runs the task on the global region, which owns console command execution and other server-wide state.
     */
    public static void runGlobal(@NotNull Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
            return;
        }
        if (!ChestSortPlugin.getInstance().isEnabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(ChestSortPlugin.getInstance(), task);
    }

    /**
     * Runs the task on the thread owning the given inventory: the holding entity for player and ender chest
     * inventories, the region of the inventory's location for blocks and entity containers, or any tick thread
     * for virtual inventories without a location.
     */
    public static void runForInventory(@NotNull Inventory inventory, @NotNull Runnable task) {
        runForInventory(inventory, task, 0);
    }

    private static void runForInventory(Inventory inventory, Runnable task, int hops) {
        // Never call getHolder() on block inventories here: it snapshots the block, which is an off-region read.
        if (inventory instanceof PlayerInventory playerInventory) {
            runForEntity(playerInventory.getHolder(), task);
            return;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof Entity entity) {
                runForEntity(entity, task);
                return;
            }
        }

        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            // Virtual inventory: owned by no region, except that viewers modify it from their own thread
            List<HumanEntity> viewers = inventory.getViewers();
            if (!viewers.isEmpty()) {
                runForEntity(viewers.getFirst(), task);
                return;
            }
            // ChestSortEvent must be called from a tick thread
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                runGlobal(task);
            }
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }
        if (!ChestSortPlugin.getInstance().isEnabled()) {
            return;
        }
        if (hops >= MAX_INVENTORY_HOPS) {
            ChestSortPlugin.getInstance().getLogger().warning("Skipped sorting an inventory at " + location
                    + ": its owning region kept moving.");
            return;
        }
        Bukkit.getRegionScheduler().execute(ChestSortPlugin.getInstance(), location,
                () -> runForInventory(inventory, task, hops + 1));
    }
}
