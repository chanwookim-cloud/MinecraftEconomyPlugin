package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class Data {

    private final Economy plugin;
    private final File file;
    private final FileConfiguration cfg;

    public Data(Economy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void setHome(Player player, Location loc) {
        if (player == null || loc == null) return;
        String base = "homes." + player.getUniqueId().toString();
        cfg.set(base + ".world", loc.getWorld().getName());
        cfg.set(base + ".x", loc.getX());
        cfg.set(base + ".y", loc.getY());
        cfg.set(base + ".z", loc.getZ());
        cfg.set(base + ".yaw", loc.getYaw());
        cfg.set(base + ".pitch", loc.getPitch());
        save();
    }

    public Location getHome(Player player) {
        if (player == null) return null;
        String base = "homes." + player.getUniqueId().toString();
        if (!cfg.contains(base + ".world")) return null;
        String world = cfg.getString(base + ".world");
        double x = cfg.getDouble(base + ".x");
        double y = cfg.getDouble(base + ".y");
        double z = cfg.getDouble(base + ".z");
        float yaw = (float) cfg.getDouble(base + ".yaw");
        float pitch = (float) cfg.getDouble(base + ".pitch");
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    public void saveAll() { save(); }

    private void save() {
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}
