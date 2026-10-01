package de.jeff_media.chestsort;

import de.jeff_media.chestsort.commands.ChestSortCommand;
import de.jeff_media.chestsort.commands.InvSortCommand;
import de.jeff_media.chestsort.commands.TabCompleter;
import de.jeff_media.chestsort.config.ConfigUpdater;
import de.jeff_media.chestsort.config.Messages;
import de.jeff_media.chestsort.data.Category;
import de.jeff_media.chestsort.data.PlayerSetting;
import de.jeff_media.chestsort.gui.GUIListener;
import de.jeff_media.chestsort.handlers.ChestSortOrganizer;
import de.jeff_media.chestsort.handlers.ChestSortPermissionsHandler;
import de.jeff_media.chestsort.handlers.Debugger;
import de.jeff_media.chestsort.handlers.GenericGuiDetector;
import de.jeff_media.chestsort.handlers.Logger;
import de.jeff_media.chestsort.listeners.ChestSortListener;
import de.jeff_media.chestsort.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ChestSortPlugin extends JavaPlugin {

    private static ChestSortPlugin instance;

    // Region threads read these concurrently; a reload builds new values and publishes them in one write.
    public volatile ChestSortOrganizer organizer;
    public volatile List<Pattern> blacklistedInventoryHolderClassNames = List.of();

    private volatile FileConfiguration config;
    private volatile GenericGuiDetector genericGuiDetector;
    private volatile boolean debug = false;
    private volatile List<String> disabledWorlds = List.of();
    private final Map<UUID, Long> hotkeyCooldown = new ConcurrentHashMap<>();
    private volatile Logger lgr;
    private volatile ChestSortListener chestSortListener;
    private final Map<String, PlayerSetting> perPlayerSettings = new ConcurrentHashMap<>();
    private final ChestSortPermissionsHandler permissionsHandler = new ChestSortPermissionsHandler(this);
    private volatile String sortingMethod;
    private volatile boolean usingMatchingConfig = true;
    private volatile boolean verbose = true;
    private volatile YamlConfiguration guiConfig = new YamlConfiguration();
    private volatile int settingsFingerprint = 0;

    public static ChestSortPlugin getInstance() {
        return instance;
    }

    public YamlConfiguration getGuiConfig() {
        return guiConfig;
    }

    void createConfig() {
        saveDefaultConfig();
        createGUIConfig();
        reloadConfig();

        setDisabledWorlds(getConfig().getStringList("disabled-worlds"));

        ConfigUpdater.updateConfig();

        createDirectories();
    }

    @Override
    public FileConfiguration getConfig() {
        FileConfiguration current = config;
        if (current == null) {
            reloadConfig();
            current = config;
        }
        return current;
    }

    /**
     * Loads config.yml into a new object and publishes it only once it is complete, so threads reading the
     * config while a reload runs never see a half-loaded or concurrently mutated configuration.
     */
    @Override
    public void reloadConfig() {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        InputStream defaults = getResource("config.yml");
        if (defaults != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        setDefaultConfigValues(loaded);
        config = loaded;
    }

    private void createGUIConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    private void createDirectories() {
        File categoriesFolder = new File(getDataFolder(), "categories");
        if (!categoriesFolder.exists()) {
            categoriesFolder.mkdir();
        }
    }

    public void debug(String message) {
        if (isDebug()) {
            getLogger().warning("[DEBUG] " + message);
        }
    }

    public void debug2(String message) {
        if (getConfig().getBoolean("debug2")) {
            getLogger().warning("[DEBUG2] " + message);
        }
    }

    void dump() {
        File file = new File(getDataFolder(), "dump.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (Material mat : Material.values()) {
                bw.write(mat.name() + "," + getOrganizer().getCategoryLinePair(mat.name()).categoryName());
                bw.newLine();
            }
        } catch (IOException e) {
            getLogger().warning("Could not write dump.csv: " + e.getMessage());
        }
    }

    private String getCategoryList() {
        Category[] categories = getOrganizer().categories.toArray(new Category[0]);
        Arrays.sort(categories);
        StringBuilder list = new StringBuilder();
        for (Category category : categories) {
            list.append(category.name).append(" (").append(category.typeMatches.length).append("), ");
        }
        if (list.length() > 2) {
            list.setLength(list.length() - 2);
        }
        return list.toString();
    }

    public List<String> getDisabledWorlds() {
        return disabledWorlds;
    }

    public void setDisabledWorlds(List<String> disabledWorlds) {
        this.disabledWorlds = disabledWorlds == null ? List.of() : List.copyOf(disabledWorlds);
    }

    public GenericGuiDetector getGenericGuiDetector() {
        return genericGuiDetector;
    }

    public Map<UUID, Long> getHotkeyCooldown() {
        return hotkeyCooldown;
    }

    public Logger getLgr() {
        return lgr;
    }

    public void setLgr(Logger lgr) {
        this.lgr = lgr;
    }

    public ChestSortListener getListener() {
        return chestSortListener;
    }

    public void setListener(ChestSortListener chestSortListener) {
        this.chestSortListener = chestSortListener;
    }

    public ChestSortOrganizer getOrganizer() {
        return organizer;
    }

    public void setOrganizer(ChestSortOrganizer organizer) {
        this.organizer = organizer;
    }

    public Map<String, PlayerSetting> getPerPlayerSettings() {
        return perPlayerSettings;
    }

    public ChestSortPermissionsHandler getPermissionsHandler() {
        return permissionsHandler;
    }

    /**
     * Returns the player's settings. On the player's own thread they are loaded if needed; from any other
     * thread an unloaded player gets the configured defaults while loading is scheduled on their thread.
     */
    public PlayerSetting getPlayerSetting(Player p) {
        PlayerSetting setting = perPlayerSettings.get(p.getUniqueId().toString());
        if (setting != null && setting.fingerprint.equals(getFingerprint())) {
            return setting;
        }
        if (Bukkit.isOwnedByCurrentRegion(p)) {
            return registerPlayerIfNeeded(p);
        }
        SchedulerUtils.runForEntity(p, () -> registerPlayerIfNeeded(p));
        return createPlayerSetting(null, getFingerprint());
    }

    public String getSortingMethod() {
        return sortingMethod;
    }

    public void setSortingMethod(String sortingMethod) {
        this.sortingMethod = sortingMethod;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isInHotkeyCooldown(UUID uuid) {
        double cooldown = getConfig().getDouble("hotkey-cooldown") * 1000;
        if (cooldown == 0) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        Long lastUsage = getHotkeyCooldown().put(uuid, currentTime);
        long difference = currentTime - (lastUsage == null ? 0L : lastUsage);
        debug("Difference: " + difference);
        return difference <= cooldown;
    }

    public boolean isSortingEnabled(Player p) {
        return getPlayerSetting(p).sortingEnabled;
    }

    public boolean isUsingMatchingConfig() {
        return usingMatchingConfig;
    }

    public void setUsingMatchingConfig(boolean usingMatchingConfig) {
        this.usingMatchingConfig = usingMatchingConfig;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // Reloads can be started from several threads at once (console and players); run them one at a time.
    public synchronized void load(boolean reload) {
        int fingerprint = 0;
        File fingerprintFile = new File(getDataFolder(), "settings.fingerprint");
        if (fingerprintFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(fingerprintFile);
            fingerprint = yaml.getInt("v", 0);
        }
        settingsFingerprint = fingerprint;

        // Player settings are written to the PDC when they change, so a reload keeps the loaded settings.
        createConfig();
        setDebug(getConfig().getBoolean("debug"));

        HandlerList.unregisterAll(this);

        if (isDebug()) {
            getServer().getPluginManager().registerEvents(new Debugger(this), this);
        }

        genericGuiDetector = new GenericGuiDetector(this);

        saveDefaultCategories();

        List<Pattern> blacklist = new ArrayList<>();
        for (String line : getConfig().getStringList("blocked-inventory-holders-regex")) {
            try {
                blacklist.add(Pattern.compile(line));
            } catch (Exception e) {
                getLogger().warning("Invalid regex in blocked-inventory-holders-regex: " + line);
            }
        }
        blacklistedInventoryHolderClassNames = List.copyOf(blacklist);

        setVerbose(getConfig().getBoolean("verbose"));
        setLgr(new Logger(this, getConfig().getBoolean("log")));
        Messages.reload();
        setOrganizer(new ChestSortOrganizer(this, getOrganizer()));
        setListener(new ChestSortListener(this));
        setSortingMethod(getConfig().getString("sorting-method"));

        getServer().getPluginManager().registerEvents(getListener(), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        ChestSortCommand chestSortCommandExecutor = new ChestSortCommand(this);
        TabCompleter tabCompleter = new TabCompleter();
        getCommand("sort").setExecutor(chestSortCommandExecutor);
        getCommand("sort").setTabCompleter(tabCompleter);

        InvSortCommand invSortCommandExecutor = new InvSortCommand(this);
        getCommand("isort").setExecutor(invSortCommandExecutor);
        getCommand("isort").setTabCompleter(tabCompleter);

        if (isVerbose()) {
            getLogger().info("Use permissions: " + getConfig().getBoolean("use-permissions"));
            getLogger().info("Current sorting method: " + getSortingMethod());
            getLogger().info("Allow automatic chest sorting: " + getConfig().getBoolean("allow-automatic-sorting"));
            getLogger().info("  |- Chest sorting enabled by default: " + getConfig().getBoolean("sorting-enabled-by-default"));
            getLogger().info("  |- Sort time: " + getConfig().getString("sort-time"));
            getLogger().info("Allow automatic inventory sorting: " + getConfig().getBoolean("allow-automatic-inventory-sorting"));
            getLogger().info("  |- Inventory sorting enabled by default: " + getConfig().getBoolean("inv-sorting-enabled-by-default"));
            getLogger().info("Auto generate category files: " + getConfig().getBoolean("auto-generate-category-files"));
            getLogger().info("Allow hotkeys: " + getConfig().getBoolean("allow-sorting-hotkeys"));
            if (getConfig().getBoolean("allow-sorting-hotkeys")) {
                getLogger().info("Hotkeys enabled by default:");
                getLogger().info("  |- Middle-Click: " + getConfig().getBoolean("sorting-hotkeys.middle-click"));
                getLogger().info("  |- Shift-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-click"));
                getLogger().info("  |- Double-Click: " + getConfig().getBoolean("sorting-hotkeys.double-click"));
                getLogger().info("  |- Shift-Right-Click: " + getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
            }
            getLogger().info("Allow additional hotkeys: " + getConfig().getBoolean("allow-additional-hotkeys"));
            if (getConfig().getBoolean("allow-additional-hotkeys")) {
                getLogger().info("Additional hotkeys enabled by default:");
                getLogger().info("  |- Left-Click: " + getConfig().getBoolean("additional-hotkeys.left-click"));
                getLogger().info("  |- Right-Click: " + getConfig().getBoolean("additional-hotkeys.right-click"));
            }
            getLogger().info("Categories: " + getCategoryList());
        }

        if (getConfig().getBoolean("dump")) {
            dump();
        }

        // Permission attachments and the PDC belong to each player's own thread.
        for (Player p : getServer().getOnlinePlayers()) {
            SchedulerUtils.runForEntity(p, () -> {
                getPermissionsHandler().removePermissions(p);
                getPermissionsHandler().addPermissions(p);
                registerPlayerIfNeeded(p);
            });
        }
    }

    @Override
    public void onDisable() {
        // The plugin is already disabled here, so nothing can be scheduled. During shutdown this thread owns
        // every player; a player owned by another region is skipped (settings are already saved on change).
        for (Player player : getServer().getOnlinePlayers()) {
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                unregisterPlayer(player);
                getPermissionsHandler().removePermissions(player);
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        load(false);
    }

    public synchronized void incrementFingerprint() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v", settingsFingerprint + 1);
        try {
            yaml.save(new File(getDataFolder(), "settings.fingerprint"));
            // Settings loaded under the old fingerprint are replaced by the defaults the next time they are used.
            settingsFingerprint++;
            load(true);
        } catch (IOException e) {
            getLogger().warning("Could not save settings.fingerprint: " + e.getMessage());
        }
    }

    /**
     * Loads the player's settings from their PDC unless they are loaded under the current fingerprint.
     * Must run on the player's thread, which is the only thread that stores that player's settings.
     */
    public PlayerSetting registerPlayerIfNeeded(Player p) {
        String uniqueId = p.getUniqueId().toString();
        String fingerprint = getFingerprint();
        PlayerSetting existing = getPerPlayerSettings().get(uniqueId);
        if (existing != null && existing.fingerprint.equals(fingerprint)) {
            return existing;
        }
        PlayerSetting settings = createPlayerSetting(p.getPersistentDataContainer(), fingerprint);
        getPerPlayerSettings().put(uniqueId, settings);
        return settings;
    }

    /**
     * Reads settings stored under the given fingerprint, or the configured defaults if {@code pdc} is null.
     */
    private PlayerSetting createPlayerSetting(PersistentDataContainer pdc, String fingerprint) {

        boolean sortingEnabled = getStoredBoolean(pdc, "sortingEnabled" + fingerprint, getConfig().getBoolean("sorting-enabled-by-default"));
        boolean invSortingEnabled = getStoredBoolean(pdc, "invSortingEnabled" + fingerprint, getConfig().getBoolean("inv-sorting-enabled-by-default"));
        boolean middleClick = getStoredBoolean(pdc, "middleClick" + fingerprint, getConfig().getBoolean("sorting-hotkeys.middle-click"));
        boolean shiftClick = getStoredBoolean(pdc, "shiftClick" + fingerprint, getConfig().getBoolean("sorting-hotkeys.shift-click"));
        boolean doubleClick = getStoredBoolean(pdc, "doubleClick" + fingerprint, getConfig().getBoolean("sorting-hotkeys.double-click"));
        boolean shiftRightClick = getStoredBoolean(pdc, "shiftRightClick" + fingerprint, getConfig().getBoolean("sorting-hotkeys.shift-right-click"));
        boolean leftClick = getStoredBoolean(pdc, "leftClick" + fingerprint, getConfig().getBoolean("additional-hotkeys.left-click"));
        boolean rightClick = getStoredBoolean(pdc, "rightClick" + fingerprint, getConfig().getBoolean("additional-hotkeys.right-click"));
        boolean leftClickOutside = getStoredBoolean(pdc, "leftClickOutside" + fingerprint, getConfig().getBoolean("left-click-to-sort-enabled-by-default"));
        boolean hasSeenMessage = !getConfig().getBoolean("show-message-again-after-logout")
                && getStoredBoolean(pdc, "hasSeenMessage" + fingerprint, false);

        return new PlayerSetting(sortingEnabled, invSortingEnabled, middleClick, shiftClick, doubleClick,
                shiftRightClick, leftClick, rightClick, leftClickOutside, true, hasSeenMessage, fingerprint);
    }

    private boolean getStoredBoolean(PersistentDataContainer pdc, String key, boolean fallback) {
        if (pdc == null) {
            return fallback;
        }
        NamespacedKey namespacedKey = new NamespacedKey(this, key);
        try {
            Boolean stored = pdc.get(namespacedKey, PersistentDataType.BOOLEAN);
            return stored != null ? stored : fallback;
        } catch (IllegalArgumentException e) {
            // Legacy versions of ChestSort stored these values as a String (e.g. via NBTAPI),
            // so an existing player's PDC entry may still be a StringTag instead of a ByteTag.
            String legacy = pdc.get(namespacedKey, PersistentDataType.STRING);
            if (legacy != null) {
                boolean migrated = Boolean.parseBoolean(legacy);
                pdc.set(namespacedKey, PersistentDataType.BOOLEAN, migrated);
                return migrated;
            }
            return fallback;
        }
    }

    private void setStoredBoolean(PersistentDataContainer pdc, String key, boolean value) {
        pdc.set(new NamespacedKey(this, key), PersistentDataType.BOOLEAN, value);
    }

    private String getFingerprint() {
        return settingsFingerprint > 0 ? "-" + settingsFingerprint : "";
    }

    private void saveDefaultCategories() {
        if (!getConfig().getBoolean("auto-generate-category-files", true)) {
            return;
        }

        String[] defaultCategories = {"900-weapons", "905-common-tools", "907-other-tools", "909-food", "910-valuables",
                "920-armor-and-arrows", "930-brewing", "950-redstone", "960-wood", "970-stone", "980-plants", "981-corals",
                "_ReadMe - Category files"};

        File categoriesFolder = new File(getDataFolder(), "categories");
        File[] existingDefaultFiles = categoriesFolder.listFiles((directory, fileName) ->
                fileName.endsWith(".txt") && fileName.matches("(?i)9\\d\\d.*\\.txt$"));

        if (existingDefaultFiles != null) {
            for (File file : existingDefaultFiles) {
                boolean stillShipped = Arrays.stream(defaultCategories).anyMatch(name -> (name + ".txt").equalsIgnoreCase(file.getName()));
                if (!stillShipped) {
                    file.delete();
                    getLogger().warning("Deleting deprecated default category file " + file.getName());
                }
            }
        }

        for (String category : defaultCategories) {
            try (InputStream in = getClass().getResourceAsStream("/categories/" + category + ".default.txt")) {
                if (in == null) {
                    continue;
                }
                File target = new File(categoriesFolder, category + ".txt");
                Files.write(target.toPath(), in.readAllBytes());
            } catch (IOException e) {
                getLogger().warning("Could not save default category file " + category + ": " + e.getMessage());
            }
        }
    }

    private static void setDefaultConfigValues(FileConfiguration config) {
        config.addDefault("use-permissions", true);
        config.addDefault("allow-automatic-sorting", true);
        config.addDefault("allow-automatic-inventory-sorting", true);
        config.addDefault("allow-left-click-to-sort", true);
        config.addDefault("left-click-to-sort-enabled-by-default", false);
        config.addDefault("sorting-enabled-by-default", false);
        config.addDefault("inv-sorting-enabled-by-default", false);
        config.addDefault("show-message-when-using-chest", true);
        config.addDefault("show-message-when-using-chest-and-sorting-is-enabled", false);
        config.addDefault("show-message-again-after-logout", true);
        config.addDefault("sorting-method", "{category},{itemsFirst},{name},{color}");
        config.addDefault("auto-generate-category-files", true);
        config.addDefault("sort-time", "close");
        config.addDefault("allow-sorting-hotkeys", true);
        config.addDefault("allow-additional-hotkeys", true);
        config.addDefault("sorting-hotkeys.middle-click", true);
        config.addDefault("sorting-hotkeys.shift-click", true);
        config.addDefault("sorting-hotkeys.double-click", true);
        config.addDefault("sorting-hotkeys.shift-right-click", true);
        config.addDefault("additional-hotkeys.left-click", false);
        config.addDefault("additional-hotkeys.right-click", false);
        config.addDefault("dump", false);
        config.addDefault("log", false);
        config.addDefault("allow-commands", true);
        config.addDefault("prevent-sorting-null-inventories", false);
        config.addDefault("mute-protection-plugins", false);
        config.addDefault("verbose", true);
    }

    /**
     * Saves the player's settings and forgets them. Must run on the player's thread.
     */
    public void unregisterPlayer(Player p) {
        UUID uniqueId = p.getUniqueId();
        getHotkeyCooldown().remove(uniqueId);
        PlayerSetting setting = getPerPlayerSettings().remove(uniqueId.toString());
        if (setting != null) {
            savePlayerSetting(p, setting);
        }
    }

    /**
     * Writes the settings to the player's PDC. Must run on the player's thread.
     */
    public void savePlayerSetting(Player p, PlayerSetting setting) {
        String fingerprint = setting.fingerprint;
        PersistentDataContainer pdc = p.getPersistentDataContainer();

        setStoredBoolean(pdc, "sortingEnabled" + fingerprint, setting.sortingEnabled);
        setStoredBoolean(pdc, "invSortingEnabled" + fingerprint, setting.invSortingEnabled);
        setStoredBoolean(pdc, "hasSeenMessage" + fingerprint, setting.hasSeenMessage);
        setStoredBoolean(pdc, "middleClick" + fingerprint, setting.middleClick);
        setStoredBoolean(pdc, "shiftClick" + fingerprint, setting.shiftClick);
        setStoredBoolean(pdc, "doubleClick" + fingerprint, setting.doubleClick);
        setStoredBoolean(pdc, "shiftRightClick" + fingerprint, setting.shiftRightClick);
        setStoredBoolean(pdc, "leftClick" + fingerprint, setting.leftClick);
        setStoredBoolean(pdc, "rightClick" + fingerprint, setting.rightClick);
        setStoredBoolean(pdc, "leftClickOutside" + fingerprint, setting.leftClickOutside);
    }
}
