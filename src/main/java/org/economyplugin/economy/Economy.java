package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

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


 @Override
 public void onEnable() {
  // --- 1. config.yml 기본값 설정 및 저장 ---
  FileConfiguration config = getConfig();
  config.addDefault("gamble-block.world", "world");
  config.addDefault("gamble-block.x", 100);
  config.addDefault("gamble-block.y", 78);
  config.addDefault("gamble-block.z", -100);
  config.options().copyDefaults(true);
  saveConfig();
  // ----------------------------------------------------
  instance = this;

  // ConfigManager 초기화 및 커스텀 리소스 파일 배포/로드 관리
  this.configManager = new ConfigManager(this);

  // 월드 스폰 위치 설정 (기존 로직 유지)
  World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
  if (world != null) {
   Location spawn = new Location(world, 0, world.getHighestBlockYAt(0, 0) + 1, 0);
   world.setSpawnLocation(spawn);
   getLogger().info("World spawn set to (0,0,0)");
  }

  // 로거 초기화
  EconomyLogger.init(this);

  // --- 아이템 최대 스택 크기 99로 변경 시도 ---
  // [FATAL ERROR FIX]: ItemMaxStackUtil에서 NoSuchFieldException 발생.
  // 서버 버전 호환성 문제이므로 주석 처리합니다.
  // ItemMaxStackUtil.setMaxStackSizeTo99(this);

  // managers
  this.data = new Data(this);
  this.money = new Money(this);
  // NicknameManager 초기화
  this.nicknameManager = new NicknameManager(this);

  // --- GUI 초기화 (ConfigManager를 통해 설정 파일에 접근하도록 수정) ---
  this.buyMenu = new BuyMenu(this, money);
  this.sellMenu = new SellMenu(this, money);

  // Menu 생성 (buyMenu, sellMenu가 먼저 생성되어야 함)
  this.menu = new Menu(this, money, buyMenu, sellMenu);
  // ----------------------------------------------------

  // commands
  Cmds cmds = new Cmds(this, money, data, menu, nicknameManager);

  // *** [수정] 상점 명령어는 /menu로 통일합니다. ***
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

  // listeners 등록
  Bukkit.getPluginManager().registerEvents(
          new BuySellMenuListener(menu, buyMenu, sellMenu),
          this
  );
  Bukkit.getPluginManager().registerEvents(new NicknameListener(nicknameManager), this);
  Bukkit.getPluginManager().registerEvents(new BlockGambleListener(this, money), this);
  Bukkit.getPluginManager().registerEvents(new GameSpeedListener(this), this);

  // actionbar task
  this.actionbarTask = new ActionBarTask(money).runTaskTimer(this, 0L, 5L);

  getLogger().info("EconomyPlugin enabled");
 }

 @Override
 public void onDisable() {
  if (actionbarTask != null) actionbarTask.cancel();
  if (money != null) money.saveAll();
  if (data != null) data.saveAll();
  getLogger().info("EconomyPlugin disabled");
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

 // ConfigManager가 내부에서 처리하므로 이 메서드는 이제 사용되지 않을 수 있지만,
 // 기존 구조를 최대한 유지하기 위해 남겨둡니다.
 private void saveResourceSafe(String path) {
  try {
   File f = new File(getDataFolder(), path);
   if (!f.exists()) {
    saveResource(path, false);
   }
  } catch (IllegalArgumentException ignored) {
  }
 }
}