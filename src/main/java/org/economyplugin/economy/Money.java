package org.economyplugin.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Money {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> balances = new HashMap<>();
    private final File file;
    private final FileConfiguration cfg;

    public Money(Economy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "money.yml");

        // 데이터 폴더 및 파일 생성
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }

        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
        plugin.getLogger().info("Money system initialized. Loaded " + balances.size() + " accounts.");
    }

    // 모든 잔액 관련 메서드를 OfflinePlayer로 통일
    public int getBalance(OfflinePlayer player) {
        if (player == null) return 0;
        // 기본 잔액을 1000으로 설정하여 신규 플레이어에게 초기 자금 지급 (필요에 따라 0으로 변경 가능)
        return balances.getOrDefault(player.getUniqueId(), 100);
    }

    public void setBalance(OfflinePlayer player, int amount) {
        if (player == null) return;
        int newAmount = Math.max(0, amount); // 잔액은 0 미만으로 내려가지 않도록 보장

        // 메모리(Map)에만 저장
        balances.put(player.getUniqueId(), newAmount);
    }

    public void addMoney(OfflinePlayer player, int amount) {
        if (player == null || amount <= 0) return;
        setBalance(player, getBalance(player) + amount);
    }

    public boolean takeMoney(OfflinePlayer player, int amount) {
        if (player == null || amount <= 0) return false;
        int cur = getBalance(player);

        if (cur < amount) {
            return false; // 잔액 부족
        }

        setBalance(player, cur - amount);
        return true;
    }

    public boolean hasEnough(OfflinePlayer player, int amount) {
        return getBalance(player) >= amount;
    }

    // [중요] 모든 데이터를 파일에 저장하는 메서드 (서버 종료 시/오토세이브 시 사용)
    public void saveAll() {
        for (Map.Entry<UUID, Integer> e : balances.entrySet()) {
            cfg.set(e.getKey().toString(), e.getValue());
        }
        try {
            cfg.save(file);
            plugin.getLogger().info("Successfully saved " + balances.size() + " money balances.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save money.yml!");
            e.printStackTrace();
        }
    }

    public void loadAll() {
        for (String key : cfg.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                int v = cfg.getInt(key, 0);
                balances.put(u, v);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid entry in money.yml: " + key);
            }
        }
    }
}