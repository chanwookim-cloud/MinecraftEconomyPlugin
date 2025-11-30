package org.economyplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

/**
 * 단일 플레이어 취침 로직을 처리하는 이벤트 리스너입니다.
 * 한 명만 자도 아침이 되도록 설정합니다.
 */
public class SleepListener implements Listener {

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        // 취침이 성공적일 때만 처리
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();

        // 오버월드(Overworld)가 아닌 다른 월드(네더, 엔드 등)는 무시합니다.
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // 현재 월드에 있는 플레이어 중 잠들 수 있는 플레이어 수 계산
        long onlinePlayers = world.getPlayers().stream()
                .filter(p -> p.getGameMode().name().equals("SURVIVAL") || p.getGameMode().name().equals("ADVENTURE"))
                .count();

        // 현재 시간이 밤인지 확인 (12541 ~ 23458)
        long time = world.getTime();
        boolean isNight = time >= 12541 && time < 23458;

        // 플레이어 수가 1명이고 밤이면 바로 아침으로 전환
        if (onlinePlayers <= 1 && isNight) {

            // 1. 시간을 아침으로 설정 (0 틱)
            world.setTime(0);

            // 2. 비 또는 천둥이 치는 경우 날씨를 맑게 변경
            if (world.hasStorm()) {
                world.setStorm(false);
                world.setThundering(false);
            }

            // 3. 서버에 메시지 전송
            String playerName = ChatColor.YELLOW + player.getName() + ChatColor.WHITE;
            String message = ChatColor.GREEN + "[수면] " + playerName + ChatColor.WHITE + " 님이 홀로 잠들었습니다. 모두 편안한 아침을 맞이하세요!";

            Bukkit.broadcastMessage(message);
        }
    }
}