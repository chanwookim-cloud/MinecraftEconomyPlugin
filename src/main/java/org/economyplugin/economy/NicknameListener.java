package org.economyplugin.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class NicknameListener implements Listener {

    private final NicknameManager nicknameManager;

    public NicknameListener(NicknameManager nicknameManager) {
        this.nicknameManager = nicknameManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 플레이어가 접속하면 저장된 닉네임을 적용
        nicknameManager.refresh(event.getPlayer());
    }
}