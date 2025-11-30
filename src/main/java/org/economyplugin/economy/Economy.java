package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.economyplugin.listeners.SleepListener; // <-- 새로 추가된 임포트

import java.io.File;
import java.util.logging.Level;

public class Economy extends JavaPlugin {
 private static Economy instance;
 private Money money;
 private Data data;
 private Menu menu;
 private BuyMenu buyMenu;
 private SellMenu sellMenu;
 private NicknameManager nicknameManager;
 private ConfigManager configManager;
 private BukkitTask actionbarTask;
 private BukkitTask autoSaveTask;

 @Override
 public void onEnable() {
  // 1. Instance and Logger Setup (Must be first)
  instance = this;
  EconomyLogger.init(this);

  // 2. Configuration and Resource Loading
  setupConfigAndResources();

  // 3. Initialize Managers and GUIs
  initializeComponents();

  // 4. Register Handlers
  registerHandlers();

  // 5. Start Scheduled Tasks
  startScheduledTasks();

  getLogger().info("EconomyPlugin enabled");
 }

 @Override
 public void onDisable() {
  // 1. Cancel Tasks
  if (actionbarTask != null) actionbarTask.cancel();
  if (autoSaveTask != null) autoSaveTask.cancel();

  // 2. Final Data Save (Synchronous save is crucial before shutdown)
  getLogger().info("[Save] Saving all data before shutdown...");
  if (money != null) money.saveAll();
  if (data != null) data.saveAll();

  getLogger().info("EconomyPlugin disabled");
 }

 /**
  * Handles setting up the main configuration file and required resources.
  */
 private void setupConfigAndResources() {
  // config.yml defaults and save
  FileConfiguration config = getConfig();
  config.addDefault("gamble-block.world", "world");
  config.addDefault("gamble-block.x", 100);
  config.addDefault("gamble-block.y", 78);
  config.addDefault("gamble-block.z", -100);
  config.options().copyDefaults(true);
  saveConfig();

  // Distribute default resource files (safe: only saves if they don't exist)
  saveResourceSafe("items.yml");
  saveResourceSafe("potions.yml");
  saveResourceSafe("enchanted_books.yml");
 }

 /**
  * Initializes all core managers and GUI components.
  */
 private void initializeComponents() {
  // ConfigManager initializes first to handle custom configs
  this.configManager = new ConfigManager(this);

  // Data Managers (rely on ConfigManager for file paths)
  this.data = new Data(this);
  this.money = new Money(this);
  this.nicknameManager = new NicknameManager(this);

  // GUIs
  this.buyMenu = new BuyMenu(this, money);
  this.sellMenu = new SellMenu(this, money);

  // Main Menu
  this.menu = new Menu(this, money, buyMenu, sellMenu);
 }

 /**
  * Registers all plugin commands and event listeners.
  */
 private void registerHandlers() {
  // Command Registration
  Cmds cmds = new Cmds(this, money, data, menu, nicknameManager);
  if (getCommand("menu") != null) getCommand("menu").setExecutor(cmds);
  if (getCommand("money") != null) getCommand("money").setExecutor(cmds);
  if (getCommand("pay") != null) getCommand("pay").setExecutor(cmds);
  if (getCommand("sethome") != null) getCommand("sethome").setExecutor(cmds);
  if (getCommand("home") != null) getCommand("home").setExecutor(cmds);
  if (getCommand("tpa") != null) getCommand("tpa").setExecutor(cmds);
  if (getCommand("tpaccept") != null) getCommand("tpaccept").setExecutor(cmds);
  if (getCommand("tpdeny") != null) getCommand("tpdeny").setExecutor(cmds);
  if (getCommand("spawn") != null) getCommand("spawn").setExecutor(cmds);
  if (getCommand("setmoney") != null) getCommand("setmoney").setExecutor(cmds);
  if (getCommand("addmoney") != null) getCommand("addmoney").setExecutor(cmds);
  if (getCommand("removemoney") != null) getCommand("removemoney").setExecutor(cmds);
  if (getCommand("jail") != null) getCommand("jail").setExecutor(cmds);
  if (getCommand("unjail") != null) getCommand("unjail").setExecutor(cmds);
  if (getCommand("name") != null) getCommand("name").setExecutor(cmds);
  if (getCommand("setgamble") != null) getCommand("setgamble").setExecutor(cmds);

  // Listener Registration
  Bukkit.getPluginManager().registerEvents(new BuySellMenuListener(menu, buyMenu, sellMenu), this);
  Bukkit.getPluginManager().registerEvents(new NicknameListener(nicknameManager), this);
  Bukkit.getPluginManager().registerEvents(new BlockGambleListener(this, money), this);
  Bukkit.getPluginManager().registerEvents(new GameSpeedListener(this), this);
  Bukkit.getPluginManager().registerEvents(new SleepListener(), this); // <-- 추가된 부분
 }

 /**
  * Starts the repeating background tasks (Action Bar and Auto Save).
  */
 private void startScheduledTasks() {
  // Action Bar Task (runs synchronously every 5 ticks)
  this.actionbarTask = new ActionBarTask(money).runTaskTimer(this, 0L, 5L);

  // Auto Save Task (runs asynchronously every 5 minutes / 6000 ticks)
  this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
   if (money != null) money.saveAll();
   if (data != null) data.saveAll();
   getLogger().info("[AutoSave] Data saved.");
  }, 6000L, 6000L);
 }

 // getters
 public Money getMoney() { return money; }
 public Data getData() { return data; }
 public Menu getMenu() { return menu; }
 public BuyMenu getBuyMenu() { return buyMenu; }
 public SellMenu getSellMenu() { return sellMenu; }
 public static Economy getInstance() { return instance; }
 public NicknameManager getNicknameManager() { return nicknameManager; }
 public ConfigManager getConfigManager() { return configManager; }

 /**
  * Saves a resource file only if it does not already exist in the data folder.
  * @param path The path to the resource.
  */
 private void saveResourceSafe(String path) {
  File f = new File(getDataFolder(), path);
  if (!f.exists()) {
   try {
    // saveResource(path, false) only saves if the file does not exist.
    saveResource(path, false);
   } catch (IllegalArgumentException e) {
    // Log if the resource path is invalid or the file is missing from the JAR.
    getLogger().log(Level.WARNING, "Could not save resource: " + path + ". It might be missing in the JAR.", e);
   }
  }
 }
}