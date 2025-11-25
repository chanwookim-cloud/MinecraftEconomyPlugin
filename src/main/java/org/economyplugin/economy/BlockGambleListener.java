package org.economyplugin.economy;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

public class BlockGambleListener implements Listener {

    private final Economy plugin;
    private final Money money;
    private final Random random = new Random();

    // 플레이어별 도박 쿨타임 및 중복 진행 방지
    private final Set<UUID> playersInGamble = new HashSet<>();
    private static final int BET_AMOUNT = 100; // 고정 베팅액

    public BlockGambleListener(Economy plugin, Money money) {
        this.plugin = plugin;
        this.money = money;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        FileConfiguration config = plugin.getConfig();

        // --- 1. 설정에서 도박 블럭 좌표 로드 ---
        int targetX = config.getInt("gamble-block.x", Integer.MIN_VALUE);
        int targetY = config.getInt("gamble-block.y", Integer.MIN_VALUE);
        int targetZ = config.getInt("gamble-block.z", Integer.MIN_VALUE);

        // --- 1-1. 도박 블럭이 설정되었는지 확인 ---
        if (targetX == Integer.MIN_VALUE || targetY == Integer.MIN_VALUE || targetZ == Integer.MIN_VALUE) {
            // 관리자가 설정하지 않은 경우 (일반 플레이어에게는 아무 메시지도 보내지 않음)
            if (player.isOp()) {
                player.sendMessage("§c[도박 시스템 알림] 도박 블럭 위치가 설정되지 않았습니다. 기반암을 바라보고 §e/setgamble§c 명령어를 사용해주세요.");
            }
            return;
        }

        // --- 2. X, Y, Z 좌표가 일치하는지 확인 (필수 조건) ---
        // 월드 이름은 확인하지 않습니다. 해당 좌표에 있는 블럭이면 됨.
        if (clickedBlock.getX() != targetX || clickedBlock.getY() != targetY || clickedBlock.getZ() != targetZ) {
            return;
        }

        // 블럭 타입 확인: 기반암인지
        if (clickedBlock.getType() != Material.BEDROCK) return;

        event.setCancelled(true); // 상호작용 취소

        // --- 4. 플레이어별 진행 중인지 확인 ---
        if (playersInGamble.contains(player.getUniqueId())) {
            player.sendMessage("§c이미 도박이 진행 중입니다. 잠시만 기다려주세요!");
            return;
        }

        // 5. 잔액 확인 (100원)
        int BET_AMOUNT = 100;
        if (!money.hasEnough(player, BET_AMOUNT)) {
            player.sendMessage("§c잔액이 부족합니다. (필요 금액: §e" + BET_AMOUNT + "원§c)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f); // 실패 효과음
            return;
        }

        // --- 게임 시작 ---
        playersInGamble.add(player.getUniqueId()); // 플레이어별 쿨타임 시작
        money.takeMoney(player, BET_AMOUNT); // 100원 차감
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f); // 도박 시작 효과음

        // 결과 블럭 위치 (기반암 바로 위)
        Location targetLoc = clickedBlock.getLocation(); // 클릭된 블럭의 위치를 도박 블럭 위치로 사용
        Location resultLoc = targetLoc.clone().add(0, 1, 0);
        Block resultBlock = resultLoc.getBlock();

        // 확률 계산 (0.0 ~ 100.0)
        double chance = random.nextDouble() * 100.0;

        Material blockType;
        double multiplier;
        String rankName;
        Sound sound;

        // 확률표 적용 (기반암 10%, 철 35%, 금 25%, 에메랄드 20%, 다이아 9.5%, 네더라이트 0.5%)
        if (chance < 10.0) { // 0 ~ 10 (10%) - 기반암 (0배)
            blockType = Material.BEDROCK;
            multiplier = 0.0;
            rankName = "§8[꽝] 기반암";
            sound = Sound.BLOCK_ANVIL_LAND;
        } else if (chance < 45.0) { // 10 ~ 45 (35%) - 철 (1배)
            blockType = Material.IRON_BLOCK;
            multiplier = 1.0;
            rankName = "§f[일반] 철 블럭";
            sound = Sound.BLOCK_IRON_DOOR_OPEN;
        } else if (chance < 70.0) { // 45 ~ 70 (25%) - 금 (2배)
            blockType = Material.GOLD_BLOCK;
            multiplier = 2.0;
            rankName = "§e[희귀] 금 블럭";
            sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        } else if (chance < 90.0) { // 70 ~ 90 (20%) - 에메랄드 (3배)
            blockType = Material.EMERALD_BLOCK;
            multiplier = 3.0;
            rankName = "§a[초희귀] 에메랄드 블럭";
            sound = Sound.ENTITY_PLAYER_LEVELUP;
        } else if (chance < 99.5) { // 90 ~ 99.5 (9.5%) - 다이아 (5배)
            blockType = Material.DIAMOND_BLOCK;
            multiplier = 5.0;
            rankName = "§b[전설] 다이아 블럭";
            sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
        } else { // 99.5 ~ 100.0 (0.5%) - 네더라이트 (10배)
            blockType = Material.NETHERITE_BLOCK;
            multiplier = 10.0;
            rankName = "§5§l[신화] 네더라이트 블럭";
            sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
        }

        // 블럭 설치 및 보상 지급
        new BukkitRunnable() {
            @Override
            public void run() {
                // 이 코드는 기반암 바로 위에 결과 블럭을 설치합니다.
                resultBlock.setType(blockType);
            }
        }.runTask(plugin);

        int prize = (int) (BET_AMOUNT * multiplier);

        if (prize > 0) {
            money.addMoney(player, prize);
        }

        // --- 메시지 및 효과 강화 ---
        player.sendMessage("§7-----------------------------");
        player.sendMessage("§e사용 금액: §f-" + BET_AMOUNT + "원");
        player.sendMessage("§e결과: " + rankName + " §7(" + (int)multiplier + "배)");

        if (multiplier > 1.0) {
            player.sendMessage("§a획득: §6+" + prize + "원 §e(이득: " + (prize - BET_AMOUNT) + "원)");
        } else if (multiplier == 1.0) {
            player.sendMessage("§f획득: §f+" + prize + "원 §7(본전)");
        } else {
            player.sendMessage("§c획득: §40원 §7(손해: -" + BET_AMOUNT + "원)");
        }
        player.sendMessage("§e잔액: §6" + money.getBalance(player) + "원");
        player.sendMessage("§7-----------------------------");

        player.playSound(resultLoc, sound, 1.0f, 1.0f);

        // 결과에 따른 파티클 효과 추가 (파티클 오류 방지를 위해 0.0 속도 인자를 명시적으로 추가)
        if (multiplier == 0.0) { // 꽝
            resultLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, resultLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.0);
            resultLoc.getWorld().spawnParticle(Particle.SOUL, resultLoc.clone().add(0.5, 0.5, 0.5), 20, 0.2, 0.2, 0.2, 0.0);
        } else if (multiplier == 1.0) { // 본전 (철)
            resultLoc.getWorld().spawnParticle(Particle.CRIT, resultLoc.clone().add(0.5, 1.0, 0.5), 15, 0.3, 0.3, 0.3, 0.0);
        } else if (multiplier == 2.0) { // 희귀 (금)
            resultLoc.getWorld().spawnParticle(Particle.FIREWORK, resultLoc.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.0);
            resultLoc.getWorld().spawnParticle(Particle.GLOW, resultLoc.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.2, 0.2, 0.0);
        } else if (multiplier == 3.0) { // 초희귀 (에메랄드)
            resultLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, resultLoc.clone().add(0.5, 1.0, 0.5), 30, 0.5, 0.5, 0.5, 0.0);
            resultLoc.getWorld().spawnParticle(Particle.COMPOSTER, resultLoc.clone().add(0.5, 1.0, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
        } else if (multiplier == 5.0) { // 전설 (다이아)
            resultLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, resultLoc.clone().add(0.5, 1.0, 0.5), 40, 0.5, 0.5, 0.5, 0.0);
            resultLoc.getWorld().spawnParticle(Particle.EXPLOSION, resultLoc.clone().add(0.5, 1.0, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
        } else if (multiplier == 10.0) { // 신화 (네더라이트)
            // 전체 서버에 네더라이트 획득 메시지 방송
            Bukkit.broadcastMessage("§5§l[도박 알림] §e" + player.getName() + "§5§l님이 도박에서 §410배§5§l 네더라이트 블럭을 획득했습니다!");

            resultLoc.getWorld().spawnParticle(Particle.FIREWORK, resultLoc.clone().add(0.5, 1.0, 0.5), 100, 1.0, 1.0, 1.0, 0.0);
            resultLoc.getWorld().spawnParticle(Particle.FLAME, resultLoc.clone().add(0.5, 1.0, 0.5), 50, 0.5, 0.5, 0.5, 0.0);
            resultLoc.getWorld().playSound(resultLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 5.0f, 1.0f); // 웅장한 효과음
        }

        // Log
        EconomyLogger.log("BLOCK_GAMBLE: " + player.getName() + " | Used: " + BET_AMOUNT + " | Result: " + blockType.name() + " | Prize: " + prize);

        // Remove block and lift cooldown after 3 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                // --- [수정된 핵심 로직] 블럭 제거 및 클라이언트 잔상 제거 ---
                // 1. 서버 측에서 블럭을 공기로 설정 (제거)
                resultBlock.setType(Material.AIR);

                // 2. 주변 플레이어들에게 해당 블럭이 공기로 변경되었음을 강제로 전송하여 잔상 제거
                BlockData airData = Material.AIR.createBlockData();
                for (Player p : resultBlock.getWorld().getPlayers()) {
                    p.sendBlockChange(resultLoc, airData);
                }

                playersInGamble.remove(player.getUniqueId()); // 쿨타임 해제
            }
        }.runTaskLater(plugin, 60L); // 3 seconds (60 ticks)
    }
}