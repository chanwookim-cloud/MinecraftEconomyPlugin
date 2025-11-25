package org.economyplugin.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * 플러그인의 모든 설정 파일(config.yml, items.yml, enchanted_books.yml, potions.yml)을
 * 로드하고 관리하는 클래스입니다.
 */
public class ConfigManager {

    private final Economy plugin;

    // FileConfiguration 객체들을 저장할 필드
    private FileConfiguration itemsConfig;
    private FileConfiguration enchantedBooksConfig;
    private FileConfiguration potionsConfig;

    // File 객체들을 저장할 필드 (재로드 및 저장을 위해 필요)
    private File itemsFile;
    private File enchantedBooksFile;
    private File potionsFile;

    public ConfigManager(Economy plugin) {
        this.plugin = plugin;
        saveDefaultConfigs(); // 기본 설정 파일 저장
        loadConfigs();        // 설정 파일 로드
    }

    /**
     * 리소스로 포함된 기본 설정 파일들을 플러그인 폴더에 저장합니다.
     * 파일이 이미 존재하면 저장하지 않습니다.
     */
    private void saveDefaultConfigs() {
        // config.yml은 Bukkit의 기본 메서드인 saveDefaultConfig()를 사용합니다.
        plugin.saveDefaultConfig();

        // 커스텀 설정 파일들
        itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        enchantedBooksFile = new File(plugin.getDataFolder(), "enchanted_books.yml");
        if (!enchantedBooksFile.exists()) {
            plugin.saveResource("enchanted_books.yml", false);
        }

        potionsFile = new File(plugin.getDataFolder(), "potions.yml");
        if (!potionsFile.exists()) {
            plugin.saveResource("potions.yml", false);
        }
    }

    /**
     * 모든 설정 파일을 파일 시스템에서 메모리로 로드합니다.
     */
    public void loadConfigs() {
        // config.yml은 Bukkit의 기본 메서드인 getConfig()를 통해 접근합니다.
        plugin.reloadConfig();

        // 커스텀 설정 파일 로드
        try {
            itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
            enchantedBooksConfig = YamlConfiguration.loadConfiguration(enchantedBooksFile);
            potionsConfig = YamlConfiguration.loadConfiguration(potionsFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "설정 파일 로드 중 오류 발생:", e);
        }
    }

    /**
     * 모든 설정 파일을 다시 로드합니다.
     */
    public void reloadConfigs() {
        plugin.getLogger().info("모든 설정 파일 재로드 중...");
        saveDefaultConfigs();
        loadConfigs();
        plugin.getLogger().info("모든 설정 파일 재로드 완료.");
    }

    // --- Getter Methods ---

    /**
     * config.yml 파일을 반환합니다.
     * @return config.yml의 FileConfiguration
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * items.yml 파일을 반환합니다.
     * @return items.yml의 FileConfiguration
     */
    public FileConfiguration getItemsConfig() {
        return itemsConfig;
    }

    /**
     * items.yml 파일을 반환합니다. (itemsConfig()는 이전 코드 호환성을 위한 대체 메서드)
     * @return items.yml의 FileConfiguration
     */
    public FileConfiguration itemsConfig() {
        return itemsConfig;
    }

    /**
     * enchanted_books.yml 파일을 반환합니다.
     * @return enchanted_books.yml의 FileConfiguration
     */
    public FileConfiguration getEnchantedBooksConfig() {
        return enchantedBooksConfig;
    }

    /**
     * potions.yml 파일을 반환합니다.
     * @return potions.yml의 FileConfiguration
     */
    public FileConfiguration getPotionsConfig() {
        return potionsConfig;
    }
}