package org.economyplugin.economy;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public class NicknameManager {

    private final Economy plugin;
    private final File file;
    private final FileConfiguration cfg;

    public NicknameManager(Economy plugin) {
        this.plugin = plugin;
        // nicknames.yml 파일 생성 및 로드
        this.file = new File(plugin.getDataFolder(), "nicknames.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    // 닉네임 설정 및 저장
    public void setNickname(Player player, String nickname) {
        String uuid = player.getUniqueId().toString();

        if (nickname == null) {
            // 닉네임 초기화 (삭제)
            cfg.set(uuid, null);
        } else {
            // 색상 코드 변환 (& -> §)
            String coloredName = ChatColor.translateAlternateColorCodes('&', nickname);
            cfg.set(uuid, coloredName + "&r"); // 뒤에 리셋 코드 붙임
        }
        save();

        // 즉시 적용
        refresh(player);
    }

    // 저장된 닉네임 가져오기
    public String getNickname(Player player) {
        return cfg.getString(player.getUniqueId().toString());
    }

    // 플레이어에게 닉네임 적용 (접속 시, 변경 시 호출)
    public void refresh(Player player) {
        String customName = getNickname(player);

        if (customName != null) {
            // 닉네임이 있을 경우
            String finalName = ChatColor.translateAlternateColorCodes('&', customName);

            player.setDisplayName(finalName); // 채팅 이름
            player.setPlayerListName(finalName); // 탭 리스트 이름
            // player.setCustomName(finalName); // (선택 사항) 머리 위 이름 - 별도 플러그인 없으면 잘 안 보일 수 있음
            // player.setCustomNameVisible(true);
        } else {
            // 닉네임이 없을 경우 (원래 이름으로 복구)
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}