package org.economyplugin.economy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

// Listener는 Bukkit 이벤트를 수신하고, 이벤트 처리를 Menu 객체로 위임하는 역할을 합니다.
public class BuySellMenuListener implements Listener {

    private final Menu mainMenu;
    private final BuyMenu buyMenu;
    private final SellMenu sellMenu;

    public BuySellMenuListener(Menu mainMenu, BuyMenu buyMenu, SellMenu sellMenu) {
        this.mainMenu = mainMenu;
        this.buyMenu = buyMenu;
        this.sellMenu = sellMenu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Java 16+의 pattern matching for instanceof를 사용하여 코드를 간결하게 만듭니다.
        if (event.getView() == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        ItemStack currentItem = event.getCurrentItem();
        Inventory clickedInventory = event.getClickedInventory();

        // 1. 우리가 관리하는 GUI인지 식별
        boolean isMyGui = (mainMenu != null && mainMenu.isBaseTitle(title)) ||
                (buyMenu != null && buyMenu.isBaseTitle(title)) ||
                (sellMenu != null && sellMenu.isBaseTitle(title));

        if (!isMyGui) return;

        int rawSlot = event.getRawSlot();

        // 2. GUI 상단 (Raw Slot 0 ~ 53) 클릭 시에만 취소
        // 플레이어 인벤토리(Raw Slot 54 이상) 클릭은 막지 않아야 아이템 이동이 가능함
        if (rawSlot < 54 && clickedInventory != null && clickedInventory.equals(event.getView().getTopInventory())) {
            // [필수] 내 플러그인 메뉴의 상단 인벤토리라면 무조건 클릭 취소
            event.setCancelled(true);
        }

        // GUI 상단 인벤토리를 클릭하지 않았거나 (플레이어 인벤토리), 클릭된 아이템이 없다면 로직 처리 안 함
        // 상단 인벤토리를 클릭했는데 아이템이 없는 경우는 처리할 필요가 없으므로 early return
        if (currentItem == null && rawSlot < 54) return;

        // 3. GUI 종류에 따라 해당 Menu 클래스의 onGuiClick 메서드를 호출하여 로직 위임

        // 메인 메뉴 클릭 처리
        if (mainMenu != null && mainMenu.isBaseTitle(title)) {
            // 메인 메뉴는 항상 상단 GUI 클릭만 처리합니다.
            if (rawSlot < 54) {
                mainMenu.onGuiClick(player, rawSlot, currentItem);
            }
        }
        // 구매 메뉴 클릭 처리
        else if (buyMenu != null && buyMenu.isBaseTitle(title)) {
            // 구매 메뉴는 항상 상단 GUI 클릭만 처리합니다.
            if (rawSlot < 54) {
                buyMenu.onGuiClick(player, rawSlot, currentItem);
            }
        }
        // 판매 메뉴 클릭 처리
        else if (sellMenu != null && sellMenu.isBaseTitle(title)) {
            // SellMenu의 onGuiClick은 상단 인벤토리의 버튼 클릭 (페이지 이동, 전체 판매 등)을 처리합니다.
            if (clickedInventory != null && clickedInventory.equals(event.getView().getTopInventory())) {
                sellMenu.onGuiClick(player, rawSlot, currentItem);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView() == null) return;
        String title = event.getView().getTitle();

        boolean isMyGui = (mainMenu != null && mainMenu.isBaseTitle(title)) ||
                (buyMenu != null && buyMenu.isBaseTitle(title)) ||
                (sellMenu != null && sellMenu.isBaseTitle(title));

        if (isMyGui) {
            // 드래그된 슬롯 중 하나라도 상단 인벤토리 영역(Raw Slot 0 ~ 53)에 속하면 취소
            if (event.getRawSlots().stream().anyMatch(slot -> slot < 54)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Menu 객체에 onGuiClose() 메서드가 정의되어 있지 않으므로, 닫기 이벤트 시 아무 작업도 수행하지 않습니다.
        // 필요하다면 Menu 추상 클래스에 해당 메서드를 추가하여 닫기 로직을 위임할 수 있습니다.
    }
}