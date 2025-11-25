package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import java.lang.reflect.Field;
import java.util.logging.Level;

public class ItemMaxStackUtil {

    private static final int NEW_MAX_STACK = 99;

    /**
     * 리플렉션을 사용하여 모든 아이템의 최대 스택 크기를 99로 변경합니다.
     * 이 메서드는 플러그인 시작 시 한 번만 호출되어야 합니다.
     * @param plugin Economy 플러그인 인스턴스
     */
    public static void setMaxStackSizeTo99(Economy plugin) {

        try {
            // NMS Item 클래스를 담을 변수를 초기화합니다.
            Class<?> nmsItemClass = null;

            // Bukkit API 버전 정보를 사용하여 NMS 클래스 경로를 추측합니다.
            if (Bukkit.getBukkitVersion().contains("1.16") || Bukkit.getBukkitVersion().contains("1.15") || Bukkit.getBukkitVersion().contains("1.14") || Bukkit.getBukkitVersion().contains("1.13")) {
                // 1.13~1.16 버전: 이전 NMS 경로 사용
                String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                nmsItemClass = Class.forName("net.minecraft.server." + version + ".Item");
            }
            else if (Bukkit.getBukkitVersion().contains("1.17") || Bukkit.getBukkitVersion().contains("1.18") || Bukkit.getBukkitVersion().contains("1.19") || Bukkit.getBukkitVersion().contains("1.20") || Bukkit.getBukkitVersion().contains("1.21")) {
                // 1.17+ 버전: Mojang 매핑 경로 추측 (Paper/Spigot 환경에서는 재매핑되지만, Item 클래스 이름은 동일)
                try {
                    // net.minecraft.world.item.Item 경로를 시도합니다.
                    nmsItemClass = Class.forName("net.minecraft.world.item.Item");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().log(Level.WARNING, "NMS Item class (net.minecraft.world.item.Item) not found. Cannot set custom stack size.");
                    return;
                }
            } else {
                plugin.getLogger().log(Level.WARNING, "Unsupported Minecraft version for custom max stack size.");
                return;
            }

            if (nmsItemClass == null) return;

            // --- 2. 최대 스택 크기 필드 찾기 ---
            // 'maxStackSize' 필드 이름은 대부분의 NMS 버전에서 동일합니다.
            Field maxStackField = nmsItemClass.getDeclaredField("maxStackSize");
            maxStackField.setAccessible(true); // private 필드에 접근 허용

            // --- 3. 모든 Material을 반복하며 값 설정 ---
            int changeCount = 0;
            for (Material material : Material.values()) {

                // 1개, 16개 등 (예: 도구, 포션, 엔더 진주) 64개가 아닌 아이템은 변경하지 않습니다.
                if (material.getMaxStackSize() != 64) {
                    continue;
                }

                // Bukkit Material에 해당하는 NMS Item 객체를 가져옵니다.
                Object nmsItem = getCustomItem(material);

                // --- [수정된 부분] nmsItem이 null이 아닌지(즉, NMS Item 객체를 성공적으로 얻었는지)만 확인합니다. ---
                if (nmsItem != null) {
                    // NMS Item 객체의 maxStackSize 필드 값을 99로 변경
                    // maxStackField는 NMS Item 클래스의 필드이며, nmsItem은 해당 클래스의 인스턴스입니다.
                    maxStackField.set(nmsItem, NEW_MAX_STACK);
                    changeCount++;
                }
            }

            plugin.getLogger().info("Successfully changed max stack size of " + changeCount + " items to " + NEW_MAX_STACK + ".");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set custom max stack size! Server version compatibility issue.", e);
        }
    }

    // --- Reflection 헬퍼 메서드 ---

    // 서버 버전에 맞는 CraftBukkit 클래스를 로드합니다.
    private static Class<?> getCraftBukkitClass(String name) throws ClassNotFoundException {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("org.bukkit.craftbukkit." + version + "." + name);
    }

    // Material에서 NMS Item 객체를 가져오는 간접적인 방법
    private static Object getCustomItem(Material material) {
        try {
            // Material에 해당하는 ItemStack을 만들고
            org.bukkit.inventory.ItemStack bukkitStack = new org.bukkit.inventory.ItemStack(material);

            // CraftItemStack 클래스 로드
            Class<?> craftItemStackClass = getCraftBukkitClass("inventory.CraftItemStack");

            // CraftItemStack.asNMSCopy(bukkitStack)을 호출하여 NMS ItemStack으로 변환
            Object nmsItemStack = craftItemStackClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class).invoke(null, bukkitStack);

            // NMS ItemStack에서 Item을 추출
            return nmsItemStack.getClass().getMethod("getItem").invoke(nmsItemStack);

        } catch (Exception e) {
            // 특정 Material이 NMS Item으로 변환되지 못할 경우 (예: 일부 레거시 아이템)
            // Economy.getInstance().getLogger().log(Level.WARNING, "Failed to get NMS Item for: " + material.name(), e);
            return null;
        }
    }
}