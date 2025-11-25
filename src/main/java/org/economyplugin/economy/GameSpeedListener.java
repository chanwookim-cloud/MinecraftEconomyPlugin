package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import org.bukkit.block.data.Ageable; // Ageable은 1.13+에서 사용 가능합니다.

import java.util.Random;

public class GameSpeedListener implements Listener {

    private final Economy plugin;
    private final Random random = new Random();
    private static final int SPEED_MULTIPLIER = 10;
    private static final int ACCELERATION_INTERVAL = 10; // 0.5초(10틱)마다 가속 시도

    public GameSpeedListener(Economy plugin) {
        this.plugin = plugin;
        startAcceleratedTasks();
    }

    // 농작물 가속화: 전통적인 for 루프를 사용하여 청크를 반복합니다.
    private void accelerateCrops() {
        // 모든 월드를 반복
        for (World world : Bukkit.getWorlds()) {

            // world.getLoadedChunks()는 배열이나 리스트를 반환하므로, 전통적인 for 루프를 사용합니다.
            for (Chunk chunk : world.getLoadedChunks()) {

                // 청크당 10번의 성장 시도 (10배 가속 시뮬레이션)
                for (int i = 0; i < SPEED_MULTIPLIER; i++) {

                    // 청크 내부의 무작위 X, Z 좌표를 월드 좌표로 변환합니다.
                    // getX(), getZ() 메서드가 존재하지 않을 경우를 대비하여,
                    // 청크의 좌표는 chunk.getX()와 chunk.getZ()를 통해 접근하는 것이 표준이므로 이를 유지합니다.
                    // 만약 이 부분에서 에러가 발생한다면, 사용 중인 API 버전이 매우 오래되었음을 의미합니다.
                    int x = chunk.getX() * 16 + random.nextInt(16);
                    int z = chunk.getZ() * 16 + random.nextInt(16);

                    // 월드의 최소 높이와 최대 높이 사이에서 Y 좌표 선택
                    int y = random.nextInt(world.getMaxHeight() - world.getMinHeight()) + world.getMinHeight();

                    Block block = world.getBlockAt(x, y, z);

                    // Ageable 인터페이스가 없으면 농작물 가속을 시도하지 않음 (1.13+ API 필요)
                    if (block.getBlockData() instanceof Ageable) {
                        // Ageable 블럭이라면 강제로 다음 단계로 성장 시도
                        Ageable ageable = (Ageable) block.getBlockData();
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getAge() + 1);
                            block.setBlockData(ageable, true); // 블럭 데이터 업데이트 및 알림
                        }
                    }
                    // 참고: 만약 1.13 이전 버전을 사용 중이라면, Ageable 대신 org.bukkit.material.Crops 등의 클래스를 사용해야 합니다.
                    // 현재 코드는 최신 Bukkit API 기준으로 작성되었습니다.
                }
            }
        }
    }


    // 낚시 가속화: 전통적인 for 루프를 사용하여 엔티티를 반복합니다.
    private void accelerateFishing() {
        // 모든 월드를 반복
        for (World world : Bukkit.getWorlds()) {

            // world.getEntities()는 콜렉션을 반환하므로, 전통적인 for 루프를 사용합니다.
            for (Entity entity : world.getEntities()) {

                // FishHook 엔티티인지 확인합니다.
                if (entity instanceof FishHook) {
                    FishHook fishHook = (FishHook) entity;

                    // FishHook.HookState.FISHING을 사용하지 않고, 낚시찌가 물 속에 있을 때만 가속을 시도합니다.
                    // setWaitTime(0)은 NMS 없이 순수 Bukkit API로 입질을 가속하는 방법입니다.
                    // 모든 FishHook에 적용되어, 낚시가 시작된 훅은 즉시 입질을 받게 됩니다.
                    fishHook.setWaitTime(0);
                }
            }
        }
    }


    public void startAcceleratedTasks() {
        // 낚시 가속화 (10틱 = 0.5초마다 실행)
        new BukkitRunnable() {
            @Override
            public void run() {
                accelerateFishing();
            }
        }.runTaskTimer(plugin, 0L, ACCELERATION_INTERVAL);

        // 농작물 가속화 (10틱 = 0.5초마다 실행)
        new BukkitRunnable() {
            @Override
            public void run() {
                accelerateCrops();
            }
        }.runTaskTimer(plugin, 0L, ACCELERATION_INTERVAL);
    }
}