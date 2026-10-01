package de.jeff_media.chestsort.data;

import org.bukkit.inventory.Inventory;

import java.util.concurrent.TimeUnit;

/**
 * A player's sorting settings. Only read and modified on the thread owning that player.
 */
public class PlayerSetting {

    // Second click of a fill/unload double click must follow within 10 ticks.
    private static final long DOUBLE_CLICK_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    public boolean sortingEnabled;
    public boolean invSortingEnabled;
    public boolean middleClick;
    public boolean shiftClick;
    public boolean doubleClick;
    public boolean shiftRightClick;
    public boolean leftClick;
    public boolean rightClick;
    public boolean leftClickOutside;
    public boolean hasSeenMessage;
    public boolean changed;
    public Inventory guiInventory = null;
    // Settings fingerprint the values were loaded under, so a reset never receives a stale write.
    public final String fingerprint;

    DoubleClickType currentDoubleClick = DoubleClickType.NONE;
    private long currentDoubleClickTime;

    public enum DoubleClickType {
        NONE, RIGHT_CLICK, LEFT_CLICK
    }

    public PlayerSetting(boolean sortingEnabled, boolean invSortingEnabled, boolean middleClick, boolean shiftClick,
                          boolean doubleClick, boolean shiftRightClick, boolean leftClick, boolean rightClick,
                          boolean leftClickOutside, boolean changed, boolean hasSeenMessage, String fingerprint) {
        this.sortingEnabled = sortingEnabled;
        this.invSortingEnabled = invSortingEnabled;
        this.middleClick = middleClick;
        this.shiftClick = shiftClick;
        this.doubleClick = doubleClick;
        this.shiftRightClick = shiftRightClick;
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.leftClickOutside = leftClickOutside;
        this.changed = changed;
        this.hasSeenMessage = hasSeenMessage;
        this.fingerprint = fingerprint;
    }

    public DoubleClickType getCurrentDoubleClick(DoubleClickType click) {
        if (click == DoubleClickType.NONE) {
            return DoubleClickType.NONE;
        }
        long now = System.nanoTime();
        if (currentDoubleClick == click && now - currentDoubleClickTime <= DOUBLE_CLICK_WINDOW_NANOS) {
            currentDoubleClick = DoubleClickType.NONE;
            return click;
        }
        currentDoubleClick = click;
        currentDoubleClickTime = now;
        return DoubleClickType.NONE;
    }

    public void toggleMiddleClick() {
        middleClick = !middleClick;
        changed = true;
    }

    public void toggleShiftClick() {
        shiftClick = !shiftClick;
        changed = true;
    }

    public void toggleDoubleClick() {
        doubleClick = !doubleClick;
        changed = true;
    }

    public void toggleShiftRightClick() {
        shiftRightClick = !shiftRightClick;
        changed = true;
    }

    public void toggleLeftClickOutside() {
        leftClickOutside = !leftClickOutside;
        changed = true;
    }

    public void toggleLeftClick() {
        leftClick = !leftClick;
        changed = true;
    }

    public void toggleRightClick() {
        rightClick = !rightClick;
        changed = true;
    }

    public void enableChestSorting() {
        sortingEnabled = true;
        changed = true;
    }

    public void disableChestSorting() {
        sortingEnabled = false;
        changed = true;
    }

    public void toggleChestSorting() {
        sortingEnabled = !sortingEnabled;
        changed = true;
    }

    public void enableInvSorting() {
        invSortingEnabled = true;
        changed = true;
    }

    public void disableInvSorting() {
        invSortingEnabled = false;
        changed = true;
    }

    public void toggleInvSorting() {
        invSortingEnabled = !invSortingEnabled;
        changed = true;
    }
}
