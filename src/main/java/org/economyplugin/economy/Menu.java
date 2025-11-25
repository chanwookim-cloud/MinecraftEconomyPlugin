package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

public class Menu {
    public static final String BASE_TITLE = "메인 메뉴";

    final Economy plugin;
    private final Money money;
    private final BuyMenu buyMenu;
    private final SellMenu sellMenu;

    /**
     * 메인 메뉴는 BuyMenu와 SellMenu 인스턴스를 외부에서 주입받아 사용합니다.
     */
    public Menu(Economy plugin, Money money, BuyMenu buyMenu, SellMenu sellMenu) {
        this.plugin = plugin;
        this.money = money;
        this.buyMenu = buyMenu;
        this.sellMenu = sellMenu;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, BASE_TITLE);

        // 잔액 표시 아이템은 lore가 없으므로 사용자 제공 메서드 사용
        inv.setItem(0, createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원"));

        // 나머지 아이템은 사용자 제공 메서드 사용
        inv.setItem(3, createItem(Material.EMERALD, "§a구매 상점"));
        inv.setItem(4, createItem(Material.BARRIER, "§c닫기"));
        inv.setItem(5, createItem(Material.DIAMOND, "§b판매 상점"));
        inv.setItem(7, createItem(Material.BOOK, "§f설명"));

        player.openInventory(inv);
    }

    // --- 리스너 연동을 위한 메서드 ---
    public boolean isBaseTitle(String title) {
        return title != null && title.equals(BASE_TITLE);
    }

    public void onGuiClick(Player player, int slot, ItemStack clicked) {
        // 클릭 로직
        switch (slot) {
            case 3: // 구매 상점 이동
                if (buyMenu != null) buyMenu.open(player); // openBuyMenu 대신 BuyMenu.open() 사용
                break;
            case 4: // 닫기
                player.closeInventory();
                break;
            case 5: // 판매 상점 이동
                if (sellMenu != null) sellMenu.openSellMenu(player,0); // openSellMenu 대신 SellMenu.open() 사용
                break;
            case 7: // 설명
                player.sendMessage("§e경제 플러그인 v1.0");
                break;
            default:
                break;
        }
    }
    // ---------------------------------

    /**
     * 기본 아이템 생성 (Material, DisplayName) - 사용자 제공 메서드
     */
    public static ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, Collections.emptyList());
    }

    /**
     * 로어(Lore)를 포함한 아이템 생성 (Material, DisplayName, Lore) - Sell/BuyMenu 컴파일을 위한 오버로드
     */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}