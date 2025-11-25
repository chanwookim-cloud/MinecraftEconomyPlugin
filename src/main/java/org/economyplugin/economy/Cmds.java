package org.economyplugin.economy;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cmds implements CommandExecutor {
    private final Map<UUID, BossBar> jailBars = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Economy plugin;
    private final Money money;
    private final Data data;
    private final Menu menu;
    private final NicknameManager nicknameManager;

    public Cmds(Economy plugin, Money money, Data data, Menu menu, NicknameManager nicknameManager) {
        this.plugin = plugin;
        this.money = money;
        this.data = data;
        this.menu = menu;
        this.nicknameManager = nicknameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "name":
                if (args.length == 0) {
                    player.sendMessage("§c사용법: /name <닉네임> 또는 /name reset");
                    return true;
                }

                String input = args[0];

                // 초기화 명령어
                if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("초기화")) {
                    nicknameManager.setNickname(player, null);
                    player.sendMessage("§a닉네임이 초기화되었습니다.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f); // 효과음 추가
                    player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.0); // 파티클 추가
                    return true;
                }

                String newNick = String.join(" ", args);

                // --- 닉네임 설정 보안 검사 ---
                boolean containsColorCode = newNick.contains("&") || newNick.contains("§");

                if (containsColorCode && !player.isOp() && !player.hasPermission("economy.nickname.color")) {
                    player.sendMessage("§c색상 코드를 사용하려면 관리자 권한이 필요합니다.");
                    newNick = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', newNick));
                }

                String strippedNick = newNick.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (strippedNick.contains("관리자") && !player.isOp() && !player.hasPermission("economy.nickname.admin")) {
                    player.sendMessage("§c'관리자'와 유사한 닉네임은 사용할 수 없습니다.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.5f); // 실패 효과음
                    return true;
                }

                if (newNick.length() > 20) {
                    player.sendMessage("§c닉네임은 20자 이내로 설정해주세요.");
                    return true;
                }
                // -----------------------------

                nicknameManager.setNickname(player, newNick);
                player.sendMessage("§a닉네임이 변경되었습니다: " + ChatColor.translateAlternateColorCodes('&', newNick));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2.0f); // 성공 효과음
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 20, 0.3, 0.3, 0.3, 0.0); // 파티클 추가
                return true;

            case "setgamble": // 도박 블럭 위치 설정
                if (!player.isOp()) {
                    player.sendMessage("§c관리자만 사용 가능합니다.");
                    return true;
                }

                Block targetBlock = player.getTargetBlock(null, 5);

                if (targetBlock == null || targetBlock.getType() == Material.AIR) {
                    player.sendMessage("§c바라보는 블럭이 없거나 너무 멀리 있습니다.");
                    return true;
                }

                if (targetBlock.getType() != Material.BEDROCK) {
                    player.sendMessage("§c도박 블럭은 기반암(BEDROCK)이어야 합니다. 기반암을 바라봐주세요.");
                    return true;
                }

                Location blockLoc = targetBlock.getLocation();

                // 월드 정보 저장 제거 (BlockGambleListener에서 월드 체크를 제거했기 때문)
                // plugin.getConfig().set("gamble-block.world", blockLoc.getWorld().getName());
                plugin.getConfig().set("gamble-block.x", blockLoc.getBlockX());
                plugin.getConfig().set("gamble-block.y", blockLoc.getBlockY());
                plugin.getConfig().set("gamble-block.z", blockLoc.getBlockZ());
                plugin.saveConfig();

                player.sendMessage("§a도박 블럭 위치(좌표)가 성공적으로 설정되었습니다. 이제 모든 월드에서 작동합니다.");
                player.sendMessage("§7X: " + blockLoc.getBlockX() +
                        ", Y: " + blockLoc.getBlockY() +
                        ", Z: " + blockLoc.getBlockZ());
                player.playSound(blockLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f); // 설정 효과음
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, blockLoc.clone().add(0.5, 1.5, 0.5), 30, 0.5, 0.5, 0.5, 0.0); // 설정 파티클
                return true;

            case "menu":
                menu.open(player);
                return true;

            case "money":
                if (args.length == 0) {
                    player.sendMessage("§e잔액: §6" + money.getBalance(player) + "원");
                } else {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t == null) {
                        player.sendMessage("플레이어를 찾을 수 없습니다.");
                        return true;
                    }
                    player.sendMessage("§e" + t.getName() + "님의 잔액: §6" + money.getBalance(t) + "원");
                }
                return true;

            case "pay":
                if (args.length != 2) {
                    player.sendMessage("사용법: /pay <닉네임> <금액>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("금액은 숫자여야 합니다.");
                    return true;
                }
                if (amount <= 0) {
                    player.sendMessage("금액은 0보다 커야 합니다.");
                    return true;
                }
                if (!money.hasEnough(player, amount)) {
                    player.sendMessage("잔액이 부족합니다.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f); // 실패 효과음
                    return true;
                }
                money.takeMoney(player, amount);
                money.addMoney(target, amount);

                EconomyLogger.log("PAY: " + player.getName() + " -> " + target.getName() + " : " + amount);

                player.sendMessage("§a송금 완료: §e" + amount + "원 §a-> " + target.getName());
                target.sendMessage("§a" + player.getName() + "님이 §e" + amount + "원§a을 송금했습니다.");

                // 송금 성공 효과음 및 파티클
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.5f);
                target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.5f);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.0);
                target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.0);

                return true;

            case "sethome":
                Location loc = player.getLocation();
                data.setHome(player, loc);
                player.setRespawnLocation(loc);
                player.sendMessage("§a홈과 스폰포인트가 설정되었습니다.");
                player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f); // 효과음 추가
                player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1, 0.5), 50, 0.5, 0.5, 0.5, 0.0); // 파티클 추가
                return true;

            case "home":
                Location home = data.getHome(player);
                if (home == null) {
                    player.sendMessage("집도 절도 없으시네욬ㅋ");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.5f);
                    return true;
                }
                player.teleport(home);
                player.sendMessage("§a홈으로 이동했습니다.");
                player.playSound(home, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.PORTAL, home, 50, 1.0, 1.0, 1.0, 0.0);
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, home, 30, 0.5, 0.5, 0.5, 0.0); // 새로운 파티클 추가
                return true;

            case "tpa":
                if (args.length != 1) {
                    player.sendMessage("사용법: /tpa <닉네임>");
                    return true;
                }
                Player tpTarget = Bukkit.getPlayerExact(args[0]);
                if (tpTarget == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }
                tpaRequests.put(tpTarget.getUniqueId(), player.getUniqueId());
                player.sendMessage("§a" + tpTarget.getName() + "님에게 텔레포트 요청을 보냈습니다. (60초 만료)");
                tpTarget.sendMessage("§e" + player.getName() + "님이 텔레포트 요청을 보냈습니다. /tpaccept 또는 /tpdeny 입력");

                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 2.0f); // 요청음
                tpTarget.playSound(tpTarget.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f); // 수신음

                // 60 second expiration
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (tpaRequests.remove(tpTarget.getUniqueId(), player.getUniqueId())) {
                            if (player.isOnline()) player.sendMessage("§c요청이 만료되었습니다.");
                            if (player.isOnline()) player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                        }
                    }
                }.runTaskLater(plugin, 20L * 60);

                return true;

            case "tpaccept":
                UUID requesterId = tpaRequests.remove(player.getUniqueId());
                if (requesterId == null) {
                    player.sendMessage("수락할 텔레포트 요청이 없습니다.");
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester == null) {
                    player.sendMessage("요청한 플레이어가 오프라인입니다.");
                    return true;
                }

                // 텔레포트 및 효과 강화
                Location reqLoc = requester.getLocation();
                Location targetLoc = player.getLocation();

                requester.teleport(targetLoc);
                requester.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                requester.getWorld().spawnParticle(Particle.PORTAL, targetLoc, 70, 0.8, 0.8, 0.8, 0.0);
                requester.getWorld().spawnParticle(Particle.REVERSE_PORTAL, reqLoc, 50, 0.5, 0.5, 0.5, 0.0);

                requester.sendMessage("§a텔레포트 성공!");
                player.sendMessage("§a요청을 수락했습니다.");
                player.playSound(targetLoc, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.5f); // 수락 효과음
                return true;

            case "tpdeny":
                UUID deniedId = tpaRequests.remove(player.getUniqueId());
                if (deniedId == null) {
                    player.sendMessage("거절할 텔레포트 요청이 없습니다.");
                    return true;
                }
                Player denied = Bukkit.getPlayer(deniedId);
                if (denied != null) denied.sendMessage("§c요청이 거절되었습니다.");
                if (denied != null) denied.playSound(denied.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage("§c요청을 거절했습니다.");
                return true;

            case "spawn":
                var world = player.getServer().getWorlds().get(0);
                if (world == null) {
                    player.sendMessage("스폰 월드를 찾을 수 없습니다.");
                    return true;
                }
                int highestY = world.getHighestBlockYAt(0, 0) + 1;
                Location spawnLoc = new Location(world, 0.5, highestY, 0.5);
                player.teleport(spawnLoc);
                player.sendMessage("§a서버 스폰(0,0)으로 이동했습니다.");

                // 스폰 이동 효과 강화
                player.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f); // 웅장한 효과음
                world.spawnParticle(Particle.FIREWORK, spawnLoc, 100, 2.0, 2.0, 2.0, 0.0);
                world.spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 50, 1.0, 1.0, 1.0, 0.0);
                return true;

            case "setmoney":
                if (!player.isOp()) {
                    player.sendMessage("관리자만 사용 가능합니다.");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("사용법: /setmoney <닉네임> <금액>");
                    return true;
                }
                Player pSet = Bukkit.getPlayerExact(args[0]);
                if (pSet == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                try {
                    int v = Integer.parseInt(args[1]);
                    money.setBalance(pSet, v);
                    player.sendMessage("§a설정 완료: " + pSet.getName() + " -> " + v + "원");
                } catch (NumberFormatException e) {
                    player.sendMessage("금액은 숫자여야 합니다.");
                }
                return true;

            case "addmoney":
                if (!player.isOp()) {
                    player.sendMessage("관리자만 사용 가능합니다.");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("사용법: /addmoney <닉네임> <금액>");
                    return true;
                }
                Player pAdd = Bukkit.getPlayerExact(args[0]);
                if (pAdd == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                try {
                    int v = Integer.parseInt(args[1]);
                    money.addMoney(pAdd, v);
                    player.sendMessage("§a추가 완료: " + pAdd.getName() + " + " + v + "원");
                } catch (NumberFormatException e) {
                    player.sendMessage("금액은 숫자여야 합니다.");
                }
                return true;

            case "removemoney":
                if (!player.isOp()) {
                    player.sendMessage("관리자만 사용 가능합니다.");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("사용법: /removemoney <닉네임> <금액>");
                    return true;
                }
                Player pRem = Bukkit.getPlayerExact(args[0]);
                if (pRem == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                try {
                    int v = Integer.parseInt(args[1]);
                    money.takeMoney(pRem, v);
                    player.sendMessage("§a차감 완료: " + pRem.getName() + " - " + v + "원");
                } catch (NumberFormatException e) {
                    player.sendMessage("금액은 숫자여야 합니다.");
                }
                return true;

            case "jail":
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /jail <닉네임> <틱>");
                    return true;
                }
                Player target1 = Bukkit.getPlayer(args[0]);
                if (target1 == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다.");
                    return true;
                }
                int ticks;
                try {
                    ticks = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c틱은 숫자여야 합니다.");
                    return true;
                }

                Location jailLoc = new Location(target1.getWorld(), -100, 72, -100);
                target1.teleport(jailLoc);
                target1.setGameMode(GameMode.ADVENTURE);

                // 감옥 효과 강화
                target1.playSound(jailLoc, Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
                target1.playSound(jailLoc, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.5f); // 드라마틱한 사운드 추가
                // 파티클 오류 방지를 위해 0.0 속도 인자를 명시적으로 추가
                target1.getWorld().spawnParticle(Particle.ASH, jailLoc, 50, 1.0, 1.0, 1.0, 0.0);
                target1.getWorld().spawnParticle(Particle.SQUID_INK, jailLoc.clone().add(0.5, 0.5, 0.5), 20, 0.2, 0.2, 0.2, 0.0);


                BossBar bar = Bukkit.createBossBar("석방", BarColor.BLUE, BarStyle.SOLID);
                bar.addPlayer(target1);
                bar.setProgress(1.0);
                jailBars.put(target1.getUniqueId(), bar);

                final int totalTicks = ticks;
                new BukkitRunnable() {
                    int remaining = totalTicks;

                    @Override
                    public void run() {
                        if (!target1.isOnline()) {
                            bar.removeAll();
                            jailBars.remove(target1.getUniqueId());
                            cancel();
                            return;
                        }
                        remaining--;
                        bar.setProgress(Math.max(0, (double) remaining / totalTicks));

                        if (remaining <= 0) {
                            unjail(target1);
                            target1.playSound(target1.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                            target1.getWorld().spawnParticle(Particle.FIREWORK, target1.getLocation(), 30, 1.0, 1.0, 1.0, 0.0);
                            cancel();
                        }
                    }
                }.runTaskTimer(Economy.getInstance(), 1L, 1L);

                sender.sendMessage("§a" + target1.getName() + "을(를) " + ticks + "틱 동안 감옥에 가둡니다.");
                return true;

            case "unjail":
                if (args.length < 1) {
                    sender.sendMessage("§c사용법: /unjail <닉네임>");
                    return true;
                }
                Player target2 = Bukkit.getPlayer(args[0]);
                if (target2 == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다.");
                    return true;
                }
                unjail(target2);
                sender.sendMessage("§a" + target2.getName() + "을(를) 석방했습니다.");
                target2.sendMessage("§a당신은 자유의 몸이 되었습니다!");
                target2.playSound(target2.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f); // 폭죽 효과음
                target2.getWorld().spawnParticle(Particle.FIREWORK, target2.getLocation(), 50, 1.0, 1.0, 1.0, 0.0);
                return true;

            default:
                return false;
        }
    }

    private void unjail(Player player) {
        BossBar bar = jailBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        player.setGameMode(GameMode.SURVIVAL);
        Location releaseLoc = new Location(player.getWorld(), -100,
                player.getWorld().getHighestBlockYAt(-100, -105) + 1, -105);
        player.teleport(releaseLoc);
    }
}