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
import org.bukkit.potion.PotionType; // PotionType은 여전히 일부 상황에서 유용할 수 있습니다.

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.economyplugin.economy.Menu.createItem;

public class SellMenu {
    public static final String BASE_TITLE = "판매 메뉴 | 페이지";

    private final Economy plugin;
    private final Money money;

    private final List<Inventory> pages = new ArrayList<>();
    private int totalPages;

    // Potions data loaded from config for fast lookup (Key: PotionEffectType Name String)
    private final Map<String, PotionSellData> sellablePotions = new HashMap<>();
    private final Map<String, Double> multipliers = new HashMap<>();

    // 설정 파일 키의 대소문자 불일치를 해결하기 위해 Material 이름(UPPERCASE)과 실제 config 키를 매핑합니다.
    private final Map<String, String> materialKeyToConfigKey = new HashMap<>();

    private final NamespacedKey shopKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey kindKey;

    public SellMenu(Economy plugin, Money money) {
        this.plugin = plugin;
        this.money = money;

        this.shopKey = new NamespacedKey(plugin, "shop_key");
        this.priceKey = new NamespacedKey(plugin, "shop_price");
        this.kindKey = new NamespacedKey(plugin, "shop_kind");

        loadItems();
    }

    // Internal class to hold Potion selling data
    private static class PotionSellData {
        // PotionType 대신 포션 효과 타입의 문자열 이름을 사용합니다.
        final String effectTypeName;
        final String name;
        final int price;

        PotionSellData(String effectTypeName, String name, int price) {
            this.effectTypeName = effectTypeName;
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
        int currentPage = getPageFromTitle(player.getOpenInventory().getTitle());

        // Footer button handling
        if (slot >= 45) {
            if (slot == 47) { // Go to Buy Menu
                if (plugin.getBuyMenu() != null) {
                    plugin.getBuyMenu().openBuyMenu(player, 0);
                }
            } else if (slot == 49) { // Page navigation
                if (currentPage + 1 < totalPages) {
                    nextPage(player, currentPage);
                } else if (currentPage > 0) {
                    openSellMenu(player, 0);
                }
            } else if (slot == 52) { // Back
                if (plugin.getMenu() != null) {
                    plugin.getMenu().open(player);
                }
            } else if (slot == 51) { // Sell All Button
                handleSellAllClick(player);
            }
            return;
        }

        // Item Sell handling (Clicking the display icon in the GUI)
        handleSellClick(player, clicked);

        // Update balance icon
        ItemStack balItem = createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원");
        player.getOpenInventory().setItem(45, balItem);
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

        // 1. Load 'sell' section from items.yml (General Items to be displayed)
        if (itemsConfig.isConfigurationSection("sell")) {
            for (String key : itemsConfig.getConfigurationSection("sell").getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("[SellMenu] Unknown material in items.yml 'sell' section: " + key);
                    continue;
                }

                String name = itemsConfig.getString("sell." + key + ".name", key);
                int price = itemsConfig.getInt("sell." + key + ".price", 0);
                if (price <= 0) continue;

                // config key의 대소문자에 관계없이 Material.name()으로 맵핑하여 가격 조회에 사용
                materialKeyToConfigKey.put(mat.name(), key);

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§b" + name + " §7(1개 당 " + price + "원)");
                    meta.setLore(Collections.singletonList("§7클릭하여 1개 판매"));

                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    // PDC에는 Material.name() (UPPERCASE)을 저장하여 일관성 유지
                    pdc.set(shopKey, PersistentDataType.STRING, mat.name());
                    pdc.set(priceKey, PersistentDataType.INTEGER, price);
                    pdc.set(kindKey, PersistentDataType.STRING, "sell_item");

                    item.setItemMeta(meta);
                }
                items.add(item);
            }
        }

        // 2. Load 'sell_potions' and 'multipliers' from potions.yml (For Sell All logic)
        ConfigurationSection sellPotionsSection = potionConfig.getConfigurationSection("sell_potions");
        if (sellPotionsSection != null) {
            for (String key : sellPotionsSection.getKeys(false)) {
                ConfigurationSection config = sellPotionsSection.getConfigurationSection(key);
                if (config == null) continue;

                // 이제 type 필드는 PotionEffectType의 이름(문자열)을 받습니다.
                String effectTypeStr = config.getString("type", "");
                String name = config.getString("name", key);
                int price = config.getInt("price", 0);

                PotionEffectType pEffectType = null;
                try {
                    if (!effectTypeStr.isEmpty()) {
                        // PotionEffectType.getByName() 대신 PotionEffectType.getByKey()를 사용합니다.
                        // 하지만 호환성을 위해 문자열로 받아 UPPERCASE로 변환하여 맵 키로 사용합니다.
                        pEffectType = PotionEffectType.getByName(effectTypeStr.toUpperCase(Locale.ROOT));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[SellMenu] Invalid PotionEffectType in potions.yml: " + effectTypeStr + ". Error: " + e.getMessage());
                    continue;
                }

                // 이 경우 Water, Mundane, Thick, Awkward와 같이 효과가 없는 포션은
                // 별도로 처리하거나, config에서 'type'을 정의하지 않도록 해야 합니다.
                // 일단 효과 타입 문자열을 맵의 키로 저장합니다.
                if (!effectTypeStr.isEmpty() && price > 0) {
                    sellablePotions.put(effectTypeStr.toUpperCase(Locale.ROOT), new PotionSellData(effectTypeStr.toUpperCase(Locale.ROOT), name, price));
                } else {
                    plugin.getLogger().warning("[SellMenu] Invalid Price or Type in potions.yml: " + key);
                }
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

        // Sell All Button
        inv.setItem(51, createItem(Material.HOPPER, "§e모든 아이템 판매", Collections.singletonList("§7인벤토리의 판매 가능한 모든 아이템(포션 포함)을 판매합니다.")));

        String pageText;
        Material pageIcon = Material.ARROW;
        if (totalPages <= 1) {
            pageText = "§7페이지 없음";
        } else if (currentPage < totalPages - 1) {
            pageText = "§a다음 페이지 §7(" + (currentPage + 2) + "/" + totalPages + ")";
            pageIcon = Material.LIME_STAINED_GLASS_PANE;
        } else {
            pageText = "§c처음으로 돌아가기 §7(1/" + totalPages + ")";
            pageIcon = Material.RED_STAINED_GLASS_PANE;
        }
        inv.setItem(49, createItem(pageIcon, pageText));

        inv.setItem(52, createItem(Material.BARRIER, "§c뒤로"));

        // 경계선 채우기
        for (int slot : new int[]{46, 48, 50, 53}) {
            inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    public void openSellMenu(Player player, int page) {
        if (pages.isEmpty()) loadItems();
        if (pages.isEmpty()) {
            Inventory empty = Bukkit.createInventory(null, 54, BASE_TITLE + " (1/1)");
            addFooterTemplates(empty, 0);
            pages.add(empty);
            totalPages = 1;
        }

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory base = pages.get(page);

        // Create a new inventory view for the player
        String title = BASE_TITLE + " (" + (page + 1) + "/" + totalPages + ")";
        Inventory view = Bukkit.createInventory(player, base.getSize(), title);

        view.setContents(base.getContents());

        // Update balance display
        view.setItem(45, createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원"));

        player.openInventory(view);
    }

    // Sell 1 item by clicking the GUI icon (Only handles general items)
    public void handleSellClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 클릭된 아이템이 판매 메뉴 아이콘인지 확인
        if (!"sell_item".equals(pdc.get(kindKey, PersistentDataType.STRING))) return;


        int price = -1;
        String pKey = null; // Material.name() (UPPERCASE)

        Integer pdcPrice = pdc.get(priceKey, PersistentDataType.INTEGER);
        String pdcKey = pdc.get(shopKey, PersistentDataType.STRING);
        if (pdcPrice != null) price = pdcPrice;
        if (pdcKey != null) pKey = pdcKey;

        if (price <= 0 || pKey == null) {
            player.sendMessage("§c판매할 수 없는 아이템입니다. (가격 또는 키 누락)");
            return;
        }

        // pKey는 Material.name()으로 저장되어 있음
        Material targetMaterial = Material.getMaterial(pKey);
        if (targetMaterial == null) {
            player.sendMessage("§c판매 아이템을 찾을 수 없습니다.");
            return;
        }

        // 판매 로직: 플레이어 인벤토리에서 해당 아이템을 찾아서 1개 제거 및 판매
        ItemStack[] contents = player.getInventory().getContents();
        boolean itemSold = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];

            // ItemMeta가 없는 순수 아이템만 판매 가능하다고 가정
            if (item != null && item.getType() == targetMaterial && !item.hasItemMeta()) {
                // Deduct 1 item
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }

                money.addMoney(player, price);

                // getDisplayName()이 null일 경우를 대비하여 null 체크 및 안전한 문자열 처리
                String displayName = meta.hasDisplayName() ? meta.getDisplayName().replaceAll(" §7\\(.*\\)", "") : targetMaterial.name();

                player.sendMessage("§a판매 완료: " + displayName + " §7(+" + price + "원)");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                openSellMenu(player, getPageFromTitle(player.getOpenInventory().getTitle()));
                itemSold = true;
                break; // 1개만 판매하고 루프 종료
            }
        }

        if (!itemSold) {
            player.sendMessage("§c판매할 아이템이 인벤토리에 없습니다. (순수 아이템만 판매 가능)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        }
    }

    // Sell ALL items including potions
    public void handleSellAllClick(Player player) {
        if (sellablePotions.isEmpty() || multipliers.isEmpty()) {
            loadItems(); // Ensure items and potions data are loaded
            if (sellablePotions.isEmpty()) {
                player.sendMessage("§c판매 설정이 로드되지 않았습니다.");
                return;
            }
        }

        Map<Integer, Integer> amountsToSell = new HashMap<>();

        long totalProfit = 0;
        ItemStack[] contents = player.getInventory().getContents();

        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        // 1. Calculate profit and mark items for removal
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            // --- 1.1 Handle General Items (from items.yml) ---
            String materialName = item.getType().name(); // UPPERCASE
            String configKey = materialKeyToConfigKey.get(materialName); // config에 정의된 원래 키 (소문자일 수 있음)

            // 판매 가능 아이템이면서 순수 아이템인지 확인 (NBT나 커스텀 메타가 없는 기본 아이템)
            if (configKey != null && item.getType().getMaxStackSize() > 1 && !item.hasItemMeta()) {
                int pricePerItem = itemsConfig.getInt("sell." + configKey + ".price", 0);

                if (pricePerItem > 0) {
                    totalProfit += (long) item.getAmount() * pricePerItem;
                    amountsToSell.put(i, item.getAmount());
                }
                continue;
            }

            // --- 1.2 Handle Potions (from potions.yml) ---
            if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                if (item.getItemMeta() instanceof PotionMeta) {
                    PotionMeta meta = (PotionMeta) item.getItemMeta();

                    // 포션 효과 목록을 가져옵니다.
                    List<PotionEffect> effects = meta.getCustomEffects();

                    // 물병, 애매한 포션 등 효과가 없는 포션을 처리합니다.
                    if (effects.isEmpty()) {
                        // Water Bottle, Awkward, Mundane, Thick Potion 등 기본 포션을 처리
                        // 이들은 일반적으로 'type' 필드 없이 판매 항목에 정의되어야 합니다.
                        // 하지만 현재 config 로딩 로직은 'type' 필드를 기반으로 맵을 생성하므로,
                        // 임시로 "WATER" (가장 기본적인 PotionType)을 사용하거나 건너뜁니다.

                        // 경고가 뜨는 PotionData를 사용하지 않기 위해 기본 포션을 처리하는 별도의 로직이 필요합니다.
                        // 지금은 효과 목록이 비어 있으면 판매하지 않도록 처리하고, 유저에게 config 변경을 안내해야 합니다.
                        plugin.getLogger().warning("Effect-less potion found. Current config logic requires PotionEffectType.");
                        continue;
                    }

                    // 포션에 적용된 가장 주된 효과를 가져옵니다.
                    // 복수 효과가 있더라도 첫 번째 효과만을 기준으로 판매 가격을 결정합니다.
                    PotionEffect primaryEffect = effects.get(0);
                    String effectName = primaryEffect.getType().getName().toUpperCase(Locale.ROOT);

                    if (sellablePotions.containsKey(effectName)) {
                        PotionSellData baseData = sellablePotions.get(effectName);
                        double price = baseData.price;

                        // 레벨이 1을 초과하면 강화로 간주 (Amplifier는 0부터 시작하므로 Level 2가 되려면 Amplifier가 1이어야 함)
                        boolean isUpgraded = primaryEffect.getAmplifier() > 0;

                        // 지속 시간이 기본 포션의 지속 시간보다 길면 확장으로 간주
                        // (PotionMeta에 기본 지속 시간을 가져오는 쉬운 메서드가 없으므로,
                        //  이전 코드처럼 1200틱을 기준으로 판단합니다. 이는 정확하지 않을 수 있습니다.)
                        boolean isExtended = primaryEffect.getDuration() > 1200;

                        if (isUpgraded && multipliers.containsKey("UPGRADED")) {
                            price *= multipliers.get("UPGRADED");
                        }

                        if (isExtended && multipliers.containsKey("EXTENDED")) {
                            price *= multipliers.get("EXTENDED");
                        }

                        // Apply Item Type multipliers (Splash/Lingering)
                        if (item.getType() == Material.SPLASH_POTION && multipliers.containsKey("SPLASH")) {
                            price *= multipliers.get("SPLASH");
                        } else if (item.getType() == Material.LINGERING_POTION && multipliers.containsKey("LINGERING")) {
                            price *= multipliers.get("LINGERING");
                        }

                        long itemProfit = (long) Math.round(price * item.getAmount());

                        totalProfit += itemProfit;
                        amountsToSell.put(i, item.getAmount()); // Mark for removal
                    }
                }
            }
        }

        if (totalProfit == 0) {
            player.sendMessage("§c판매할 수 있는 기본 아이템 또는 포션이 인벤토리에 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        // 2. Process removal and add money
        for (Map.Entry<Integer, Integer> entry : amountsToSell.entrySet()) {
            player.getInventory().setItem(entry.getKey(), null);
        }

        money.addMoney(player, (int) totalProfit);

        player.sendMessage("§a[일괄 판매] 인벤토리의 모든 판매 가능 아이템을 판매했습니다.");
        player.sendMessage("§a총 수익: §6" + totalProfit + "원");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Update balance icon after selling
        openSellMenu(player, getPageFromTitle(player.getOpenInventory().getTitle()));
    }

    // --- Utility Methods ---
    public void nextPage(Player player, int currentPage) {
        if (currentPage + 1 < totalPages) openSellMenu(player, currentPage + 1);
    }

    public void previousPage(Player player, int currentPage) {
        if (currentPage - 1 >= 0) openSellMenu(player, currentPage - 1);
    }

    public int getPageFromTitle(String title) {
        try {
            // "판매 메뉴 | 페이지 (X/Y)" 형식에서 X 추출
            String inside = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
            return Integer.parseInt(inside) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}