package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import static org.economyplugin.economy.Menu.createItem;

public class SellMenu {
    public static final String BASE_TITLE = "판매 메뉴 | 페이지";

    private final Economy plugin;
    private final Money money;
    private final Logger logger;

    private final List<Inventory> pages = new ArrayList<>();
    private int totalPages;

    // 판매 가능한 포션 데이터
    private final Map<String, PotionSellData> sellablePotions = new HashMap<>();
    private final Map<String, Double> multipliers = new HashMap<>();

    // 일반 아이템 매핑
    private final Map<String, String> materialKeyToConfigKey = new HashMap<>();

    private final NamespacedKey shopKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey kindKey;

    public SellMenu(Economy plugin, Money money) {
        this.plugin = plugin;
        this.money = money;
        this.logger = plugin.getLogger();

        this.shopKey = new NamespacedKey(plugin, "shop_key");
        this.priceKey = new NamespacedKey(plugin, "shop_price");
        this.kindKey = new NamespacedKey(plugin, "shop_kind");

        loadItems();
    }

    private static class PotionSellData {
        final String typeKey;
        final String name;
        final int price;

        PotionSellData(String typeKey, String name, int price) {
            this.typeKey = typeKey;
            this.name = name;
            this.price = price;
        }
    }

    // --- Listener Integration Methods ---
    public boolean isBaseTitle(String title) {
        return title != null && title.startsWith("판매 메뉴");
    }

    public void onGuiOpen(Player player, int page) {
        openSellMenu(player, page);
    }

    public void onGuiClick(Player player, int slot, ItemStack clicked) {
        if (!player.isOnline() || player.getOpenInventory().getTitle() == null) return;

        int currentPage = getPageFromTitle(player.getOpenInventory().getTitle());
        Inventory openInventory = player.getOpenInventory().getTopInventory();

        // Footer button handling (slots 45-53)
        if (slot >= 45) {
            if (slot == 47) { // Go to Buy Menu
                if (plugin.getBuyMenu() != null) {
                    plugin.getBuyMenu().openBuyMenu(player, 0);
                }
            } else if (slot == 49) { // Page navigation
                int nextPage = currentPage + 1;
                if (nextPage < totalPages) {
                    openSellMenu(player, nextPage); // 다음 페이지로 이동
                } else if (totalPages > 1) {
                    openSellMenu(player, 0); // 처음으로 돌아가기
                }
            } else if (slot == 52) { // Back
                if (plugin.getMenu() != null) {
                    plugin.getMenu().open(player);
                }
            } else if (slot == 51) { // Sell All Button
                handleSellAllClick(player);
            }

            // Update balance icon
            ItemStack balItem = createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원");
            openInventory.setItem(45, balItem);
            return;
        }

        // Item Sell handling (GUI 아이콘 클릭)
        handleSellClick(player, clicked);

        // Update balance icon
        ItemStack balItem = createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원");
        openInventory.setItem(45, balItem);
    }

    // ------------------------------------

    private void loadItems() {
        pages.clear();
        totalPages = 0;
        sellablePotions.clear();
        multipliers.clear();
        materialKeyToConfigKey.clear();

        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();
        FileConfiguration potionConfig = plugin.getConfigManager().getPotionsConfig();

        List<ItemStack> items = new ArrayList<>();

        // 1. Load 'sell' section from items.yml (General Items)
        if (itemsConfig.isConfigurationSection("sell")) {
            for (String key : itemsConfig.getConfigurationSection("sell").getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) continue;

                String name = itemsConfig.getString("sell." + key + ".name", key);
                int price = itemsConfig.getInt("sell." + key + ".price", 0);
                if (price <= 0) continue;

                materialKeyToConfigKey.put(mat.name(), key);

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§b" + name + " §7(1개 당 " + price + "원)");
                    meta.setLore(Collections.singletonList("§7클릭하여 1개 판매 (순수 아이템만)"));

                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(shopKey, PersistentDataType.STRING, mat.name());
                    pdc.set(priceKey, PersistentDataType.INTEGER, price);
                    pdc.set(kindKey, PersistentDataType.STRING, "sell_item");
                    item.setItemMeta(meta);
                }
                items.add(item);
            }
        }

        // 2. Load 'sell_potions' and 'multipliers' from potions.yml
        ConfigurationSection sellPotionsSection = potionConfig.getConfigurationSection("sell_potions");
        if (sellPotionsSection != null) {
            for (String key : sellPotionsSection.getKeys(false)) {
                ConfigurationSection config = sellPotionsSection.getConfigurationSection(key);
                if (config == null) continue;

                String effectTypeStr = config.getString("type", "");
                String name = config.getString("name", key);
                int price = config.getInt("price", 0);

                if (price <= 0) continue;

                PotionEffectType pEffectType = null;
                if (!effectTypeStr.isEmpty()) {
                    pEffectType = PotionEffectType.getByName(effectTypeStr.toUpperCase(Locale.ROOT));
                }

                String upperTypeKey;
                ItemStack potionItem = new ItemStack(Material.POTION);
                PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();

                // --- 포션 유형 처리 ---
                if (pEffectType != null) {
                    // 1. 효과 포션 (SPEED, HEAL 등)
                    upperTypeKey = pEffectType.getName().toUpperCase(Locale.ROOT);

                    try {
                        // Base PotionType은 효과가 있는 포션에만 설정 시도
                        PotionType baseType = PotionType.valueOf(upperTypeKey);
                        potionMeta.setBasePotionType(baseType);
                    } catch (Exception ignored) {
                        // 일치하는 PotionType이 없으면 무시 (예: HEAL 타입이 HEALING으로 다름)
                        // 대신 PotionEffectType을 추가하여 아이콘을 만듭니다.
                        potionMeta.addCustomEffect(new PotionEffect(pEffectType, 20 * 60, 0), true);
                    }

                    potionMeta.addCustomEffect(new PotionEffect(pEffectType, 20 * 60, 0), true);
                    potionMeta.setDisplayName("§b" + name + " 포션 §7(기본 1개당 " + price + "원)");
                } else {
                    // 2. 기본 포션 (WATER, MUNDANE 등)
                    upperTypeKey = key.toUpperCase(Locale.ROOT);
                    try {
                        PotionType baseType = PotionType.valueOf(upperTypeKey);
                        potionMeta.setBasePotionType(baseType);
                    } catch (IllegalArgumentException e) {
                        logger.warning("[SellMenu] Unknown PotionType for basic potion: " + key);
                    }
                    potionMeta.setDisplayName("§b" + name + " §7(1개당 " + price + "원)");
                }

                List<String> lore = new ArrayList<>();
                lore.add("§7클릭하여 1개 판매 (인벤토리에서 매칭되는 포션)");
                lore.add("§7강화/확장/투척 포션은 추가 이익이 있습니다.");
                potionMeta.setLore(lore);

                sellablePotions.put(upperTypeKey, new PotionSellData(upperTypeKey, name, price));

                PersistentDataContainer pdc = potionMeta.getPersistentDataContainer();
                pdc.set(shopKey, PersistentDataType.STRING, upperTypeKey); // 판매 기준 키 (SPEED, WATER 등)
                pdc.set(priceKey, PersistentDataType.INTEGER, price);
                pdc.set(kindKey, PersistentDataType.STRING, "sell_potion");

                potionItem.setItemMeta(potionMeta);
                items.add(potionItem);
            }
        }

        ConfigurationSection multiplierSection = potionConfig.getConfigurationSection("multipliers");
        if (multiplierSection != null) {
            for (String key : multiplierSection.getKeys(false)) {
                double multiplier = multiplierSection.getDouble(key + ".multiplier", 1.0);
                multipliers.put(key.toUpperCase(Locale.ROOT), multiplier);
            }
        }

        // Inventory Paging Logic
        final int perPage = 45;
        totalPages = (int) Math.ceil((double) items.size() / perPage);
        if (totalPages == 0) {
            Inventory inv = Bukkit.createInventory(null, 54, BASE_TITLE + " (1/1)");
            addFooterTemplates(inv, 0);
            pages.add(inv);
            totalPages = 1;
            return;
        }

        for (int i = 0; i < totalPages; i++) {
            Inventory inv = Bukkit.createInventory(null, 54, BASE_TITLE + " (" + (i + 1) + "/" + totalPages + ")");
            int start = i * perPage;
            int end = Math.min(start + perPage, items.size());
            for (int j = start; j < end; j++) inv.addItem(items.get(j));
            addFooterTemplates(inv, i);
            pages.add(inv);
        }
    }

    private void addFooterTemplates(Inventory inv, int currentPage) {
        if (inv == null) return;

        inv.setItem(47, createItem(Material.EMERALD, "§a구매 메뉴로 이동"));
        inv.setItem(51, createItem(Material.HOPPER, "§e모든 아이템 판매", Collections.singletonList("§7인벤토리의 판매 가능한 모든 아이템(포션 포함)을 판매합니다.")));
        inv.setItem(52, createItem(Material.BARRIER, "§c뒤로"));
        inv.setItem(50, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(53, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        // 페이지 이동 버튼 텍스트 수정
        String pageText;
        Material pageIcon;
        if (totalPages <= 1) {
            pageText = "§7페이지 없음";
            pageIcon = Material.BARRIER;
        } else if (currentPage < totalPages - 1) {
            pageText = "§a다음 페이지 §7(" + (currentPage + 2) + "/" + totalPages + ")";
            pageIcon = Material.ARROW;
        } else {
            pageText = "§c이전 페이지 §7(1/" + totalPages + ")";
            pageIcon = Material.ARROW;
        }
        inv.setItem(49, createItem(pageIcon, pageText));

        for (int slot : new int[]{46, 48}) {
            inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    public void openSellMenu(Player player, int page) {
        if (pages.isEmpty()) loadItems();
        if (pages.isEmpty()) return;

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory base = pages.get(page);
        String title = BASE_TITLE + " (" + (page + 1) + "/" + totalPages + ")";
        Inventory view = Bukkit.createInventory(player, base.getSize(), title);

        view.setContents(base.getContents());
        view.setItem(45, createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원"));

        player.openInventory(view);
    }

    // --- Potion Utility Methods ---

    /**
     * 포션 아이템의 완전한 식별자(예: LONG_SPEED, MUNDANE, WATER)를 반환합니다.
     * PotionType이 우선하며, 없으면 PotionEffectType을 사용합니다.
     */
    private String getPotionIdentifier(PotionMeta meta) {
        // 1. Base Potion Type (가장 정확한 포션 타입을 반환)
        try {
            if (meta.hasBasePotionType()) {
                return meta.getBasePotionType().name().toUpperCase(Locale.ROOT);
            }
        } catch (Exception e) {
            // 버전 호환성 문제시 무시
        }

        // 2. Custom Effect (효과 포션인 경우 효과 타입 반환)
        if (!meta.getCustomEffects().isEmpty()) {
            return meta.getCustomEffects().get(0).getType().getName().toUpperCase(Locale.ROOT);
        }

        // 3. Fallback (물병 등)
        return "WATER";
    }

    /**
     * 식별자에서 LONG_, STRONG_ 접두사를 제거하고 기본 효과 키를 반환합니다.
     */
    private String getBasePotionKey(String fullKey) {
        if (fullKey.startsWith("LONG_")) {
            return fullKey.substring(5);
        }
        if (fullKey.startsWith("STRONG_")) {
            return fullKey.substring(7);
        }
        return fullKey;
    }

    /**
     * 해당 포션 식별자가 효과가 없는 기본 포션인지 확인합니다.
     */
    private boolean isBasicPotion(String identifier) {
        return identifier.equals("WATER") || identifier.equals("AWKWARD") ||
                identifier.equals("MUNDANE") || identifier.equals("THICK");
    }


    private long calculatePotionProfit(PotionMeta meta, Material type) {
        String fullIdentifier = getPotionIdentifier(meta);

        // 1. 가격을 찾을 기준 키 (baseKey) 결정
        String baseKey;
        if (isBasicPotion(fullIdentifier)) {
            // 모든 기본 포션은 'WATER' 가격으로 통일 (설정 파일에 'WATER' 항목이 있어야 함)
            baseKey = "WATER";
        } else {
            // 효과 포션은 기본 효과명(예: LONG_SPEED -> SPEED)으로 결정
            baseKey = getBasePotionKey(fullIdentifier);
        }

        if (!sellablePotions.containsKey(baseKey)) {
            return 0;
        }

        PotionSellData baseData = sellablePotions.get(baseKey);
        double price = baseData.price;

        // --- 멀티플라이어 적용 로직 ---

        // 기본 포션이 아닌 경우 (효과가 있는 포션)에만 강화/확장 확인
        if (!isBasicPotion(fullIdentifier)) {
            boolean isUpgraded = false;
            boolean isExtended = false;

            // BasePotionType에서 확인 (가장 확실함)
            if (meta.hasBasePotionType()) {
                String typeName = meta.getBasePotionType().name();
                if (typeName.contains("STRONG_")) isUpgraded = true;
                if (typeName.contains("LONG_")) isExtended = true;
            } else if (!meta.getCustomEffects().isEmpty()) {
                // BasePotionType이 없으면 Custom Effect에서 확인
                PotionEffect primaryEffect = meta.getCustomEffects().get(0);
                if (primaryEffect.getAmplifier() > 0) isUpgraded = true;
                if (primaryEffect.getDuration() > 3600) isExtended = true; // 3분 초과 시 확장
            }

            if (isUpgraded && multipliers.containsKey("UPGRADED")) price *= multipliers.get("UPGRADED");
            if (isExtended && multipliers.containsKey("EXTENDED")) price *= multipliers.get("EXTENDED");
        }


        // 투척/잔류형 확인
        if (type == Material.SPLASH_POTION && multipliers.containsKey("SPLASH")) price *= multipliers.get("SPLASH");
        else if (type == Material.LINGERING_POTION && multipliers.containsKey("LINGERING")) price *= multipliers.get("LINGERING");

        return (long) Math.round(price);
    }

    /**
     * 아이템을 인벤토리에서 제거하고 돈을 지급하는 헬퍼 함수
     */
    private void performSell(Player player, ItemStack item, int slot, long profit, String displayName) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItem(slot, null);
        }

        money.addMoney(player, (int) profit);
        player.sendMessage("§a판매 완료: " + displayName + " §7(+" + profit + "원)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // --- Selling Handlers ---

    public void handleSellClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String kind = pdc.get(kindKey, PersistentDataType.STRING);
        if (kind == null) return;

        String pKey = pdc.get(shopKey, PersistentDataType.STRING);
        if (pKey == null) return;

        if ("sell_item".equals(kind)) {
            handleSingleGeneralItemSell(player, pKey, meta);
        } else if ("sell_potion".equals(kind)) {
            handleSinglePotionSell(player, pKey, meta);
        }
    }

    private void handleSinglePotionSell(Player player, String targetKey, ItemMeta displayMeta) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean itemSold = false;
        String displayName = displayMeta.hasDisplayName() ? displayMeta.getDisplayName().replaceAll(" §7\\(.*\\)", "") : targetKey;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !(item.getItemMeta() instanceof PotionMeta)) continue;

            PotionMeta itemMeta = (PotionMeta) item.getItemMeta();
            // 인벤토리 포션의 전체 식별자 (예: LONG_SPEED, MUNDANE)
            String itemIdentifier = getPotionIdentifier(itemMeta);

            // 1. 인벤토리 포션의 '기본 키'를 추출합니다. (LONG_SPEED -> SPEED, MUNDANE/AWKWARD/THICK -> 자기 자신)
            String inventoryBaseKey;

            if (isBasicPotion(itemIdentifier)) {
                // 기본 포션은 'WATER'와 매칭시키기 위해 별도 처리
                inventoryBaseKey = itemIdentifier;
            } else {
                // 효과 포션은 접두사 제거 (LONG_SPEED -> SPEED)
                inventoryBaseKey = getBasePotionKey(itemIdentifier);
            }

            // 2. GUI 항목의 targetKey(SPEED 또는 WATER)와 비교하여 매칭 여부를 확인합니다.
            boolean matches = false;

            // A. 효과 포션 매칭: 인벤토리의 기본 효과(SPEED)와 GUI의 키(SPEED)가 일치하는 경우
            if (!isBasicPotion(itemIdentifier) && inventoryBaseKey.equals(targetKey)) {
                matches = true;
            }
            // B. 기본 포션 매칭: GUI 키가 WATER이고, 인벤토리 포션이 기본 포션인 경우 (WATER, MUNDANE, AWKWARD, THICK)
            else if ("WATER".equals(targetKey) && isBasicPotion(itemIdentifier)) {
                matches = true;
            }

            if (matches) {
                long profit = calculatePotionProfit(itemMeta, item.getType());
                if (profit > 0) {
                    performSell(player, item, i, profit, displayName);

                    openSellMenu(player, getPageFromTitle(player.getOpenInventory().getTitle()));
                    itemSold = true;
                    break;
                }
            }
        }

        if (!itemSold) {
            player.sendMessage("§c인벤토리에서 일치하는 포션을 찾을 수 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        }
    }

    private void handleSingleGeneralItemSell(Player player, String targetKey, ItemMeta displayMeta) {
        int price = displayMeta.getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
        Material targetMaterial = Material.getMaterial(targetKey);
        if (targetMaterial == null || price <= 0) return;

        ItemStack[] contents = player.getInventory().getContents();
        boolean itemSold = false;
        String displayName = displayMeta.hasDisplayName() ? displayMeta.getDisplayName().replaceAll(" §7\\(.*\\)", "") : targetMaterial.name();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == targetMaterial && !item.hasItemMeta()) {
                performSell(player, item, i, price, displayName);

                openSellMenu(player, getPageFromTitle(player.getOpenInventory().getTitle()));
                itemSold = true;
                break;
            }
        }
        if (!itemSold) {
            player.sendMessage("§c판매할 순수 아이템(" + displayName + ")이 인벤토리에 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        }
    }

    // Sell ALL items including potions
    public void handleSellAllClick(Player player) {
        if (sellablePotions.isEmpty() && materialKeyToConfigKey.isEmpty()) {
            loadItems();
            if (sellablePotions.isEmpty() && materialKeyToConfigKey.isEmpty()) {
                player.sendMessage("§c판매 설정이 로드되지 않았습니다.");
                return;
            }
        }

        Map<Integer, Integer> amountsToSell = new HashMap<>();
        long totalProfit = 0;
        ItemStack[] contents = player.getInventory().getContents();
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            long pricePerItem = 0;
            if (item.getItemMeta() instanceof PotionMeta) {
                pricePerItem = calculatePotionProfit((PotionMeta) item.getItemMeta(), item.getType());
            } else {
                String materialName = item.getType().name();
                String configKey = materialKeyToConfigKey.get(materialName);
                if (configKey != null && !item.hasItemMeta()) {
                    pricePerItem = itemsConfig.getInt("sell." + configKey + ".price", 0);
                }
            }

            if (pricePerItem > 0) {
                totalProfit += pricePerItem * item.getAmount();
                amountsToSell.put(i, item.getAmount());
            }
        }

        if (totalProfit == 0) {
            player.sendMessage("§c판매할 수 있는 아이템이 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        for (Map.Entry<Integer, Integer> entry : amountsToSell.entrySet()) {
            player.getInventory().setItem(entry.getKey(), null);
        }

        money.addMoney(player, (int) totalProfit);
        player.sendMessage("§a[일괄 판매] 총 수익: §6" + totalProfit + "원");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        openSellMenu(player, getPageFromTitle(player.getOpenInventory().getTitle()));
    }

    public int getPageFromTitle(String title) {
        try {
            String inside = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
            return Integer.parseInt(inside) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}