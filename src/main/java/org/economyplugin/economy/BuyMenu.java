package org.economyplugin.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays; // 배열을 리스트로 변환하기 위해 추가
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 아이템 구매 메뉴 클래스 (BuyMenu)
 * 페이지를 지원하며 Enchanted Book 파싱 로직을 개선했습니다.
 */
public class BuyMenu {
    public static final String BASE_TITLE = "구매 메뉴 | 페이지";

    private final Economy plugin;
    private final Money money;

    private final List<Inventory> pages = new ArrayList<>();
    private int totalPages;

    private final NamespacedKey shopKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey kindKey;

    public BuyMenu(Economy plugin, Money money) {
        this.plugin = plugin;
        this.money = money;

        // PDC 키 초기화
        this.shopKey = new NamespacedKey(plugin, "shop_key");
        this.priceKey = new NamespacedKey(plugin, "shop_price");
        this.kindKey = new NamespacedKey(plugin, "shop_kind");

        loadItems();
    }

    // 임시 Menu.createItem 구현 (Menu 클래스가 없으므로 여기에 포함)
    // 실제 프로젝트에서는 Menu 클래스로 분리해야 합니다.
    public static ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }


    // --- GUI 메뉴 필수 메서드 (Menu 역할을 대신함) ---
    public boolean isBaseTitle(String title) {
        // "구매 메뉴 | 페이지"로 시작하는지 확인 (뒤에 페이지 번호가 붙을 수 있음)
        return title != null && title.startsWith(BASE_TITLE.split("\\|")[0].trim());
    }

    public void open(Player player) {
        openBuyMenu(player, 0);
    }

    public void onGuiClose(Player player) {
        // 필요한 경우 구현
    }

    public void onGuiClick(Player player, int slot, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = getPageFromTitle(player.getOpenInventory().getTitle());

        // 풋터 버튼 처리 (45번 슬롯부터)
        if (slot >= 45) {
            // 45번 슬롯은 잔액 표시 아이템이므로 건드리지 않습니다.

            if (slot == 47) { // 판매 메뉴로 이동
                if (plugin.getSellMenu() != null) {
                    plugin.getSellMenu().openSellMenu(player, 0);
                }
            } else if (slot == 49) { // 페이지 이동
                // 현재 페이지가 마지막 페이지보다 작으면 '다음 페이지' 기능
                if (currentPage < totalPages - 1 && clicked.getItemMeta().getDisplayName().contains("다음 페이지")) {
                    nextPage(player, currentPage);
                }
                // 현재 페이지가 0보다 크면 '이전 페이지' 기능
                else if (currentPage > 0 && clicked.getItemMeta().getDisplayName().contains("이전 페이지")) {
                    previousPage(player, currentPage);
                }
            } else if (slot == 52) { // 뒤로 가기
                if (plugin.getMenu() != null) {
                    plugin.getMenu().open(player);
                }
            }
            return;
        }

        // 아이템 구매 처리
        handleBuyClick(player, clicked);

        // 구매 후 잔액 업데이트를 위해 아이콘만 갱신 (화면 깜빡임 방지)
        // Menu.createItem 대신 BuyMenu.createItem 사용
        ItemStack balItem = createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원");
        player.getOpenInventory().setItem(45, balItem);
    }
    // -----------------------

    // 인챈트 파싱 결과를 담는 헬퍼 클래스
    private static class EnchantmentParseResult {
        public final Enchantment enchantment;
        public final int level;
        public final String enchantmentName; // Enchantment KEY (e.g., SHARPNESS)

        public EnchantmentParseResult(Enchantment enchantment, int level, String enchantmentName) {
            this.enchantment = enchantment;
            this.level = level;
            this.enchantmentName = enchantmentName;
        }
    }

    /**
     * 설정 파일 키와 레벨을 사용하여 Enchantment 객체를 안전하게 파싱합니다.
     * @param key 설정 파일의 키 (예: FIRE_ASPECT_2, SHARPNESS)
     * @param configLevel 설정 파일에 명시된 레벨 (필수 사용)
     * @return 파싱 결과
     */
    private EnchantmentParseResult parseEnchantmentKey(String key, int configLevel) {
        String baseKey = key.toUpperCase(Locale.ROOT);

        // 1. 키 전체를 인챈트 이름으로 시도 (e.g., MENDING, SHARPNESS)
        Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(baseKey.toLowerCase(Locale.ROOT)));
        String nameUsed = baseKey;

        if (enchant == null) {
            // 2. 키에 레벨 접미사가 붙은 경우를 시도 (e.g., PROTECTION_4, FIRE_ASPECT_2)
            int lastUnderscore = baseKey.lastIndexOf('_');
            if (lastUnderscore > 0) {
                String baseNameCandidate = baseKey.substring(0, lastUnderscore);
                String suffix = baseKey.substring(lastUnderscore + 1);

                try {
                    Integer.parseInt(suffix); // 접미사가 숫자인지 확인

                    // 접미사를 제거한 이름으로 다시 시도
                    enchant = Enchantment.getByKey(NamespacedKey.minecraft(baseNameCandidate.toLowerCase(Locale.ROOT)));
                    if (enchant != null) {
                        nameUsed = baseNameCandidate;
                    }
                } catch (NumberFormatException ignored) {
                    // 접미사가 숫자가 아니므로 무시하고 원본 키를 계속 사용
                }
            }
        }

        if (enchant != null) {
            // 성공적으로 인챈트를 찾은 경우
            return new EnchantmentParseResult(enchant, configLevel, nameUsed);
        }

        // 찾지 못한 경우
        return null;
    }


    private void loadItems() {
        pages.clear();
        totalPages = 0;

        // items.yml 파일 로드
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) plugin.saveResource("items.yml", false);
        FileConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

        // enchanted_books.yml 파일 로드
        File enchFile = new File(plugin.getDataFolder(), "enchanted_books.yml");
        if (!enchFile.exists()) plugin.saveResource("enchanted_books.yml", false);
        FileConfiguration enchConfig = YamlConfiguration.loadConfiguration(enchFile);

        List<ItemStack> items = new ArrayList<>();

        // items.yml (일반 아이템) 로드
        if (itemsConfig.isConfigurationSection("buy")) {
            for (String key : itemsConfig.getConfigurationSection("buy").getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("[BuyMenu] Unknown material in items.yml: " + key);
                    continue;
                }

                String name = itemsConfig.getString("buy." + key + ".name", key);
                int price = itemsConfig.getInt("buy." + key + ".price", 0);
                if (price <= 0) continue;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§a" + name + " §7(" + price + "원)");
                    meta.setLore(Collections.singletonList("§7클릭하여 구매"));

                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(shopKey, PersistentDataType.STRING, key);
                    pdc.set(priceKey, PersistentDataType.INTEGER, price);
                    pdc.set(kindKey, PersistentDataType.STRING, "item");

                    item.setItemMeta(meta);
                }
                items.add(item);
            }
        }

        // enchanted_books.yml (마법 부여된 책) 로드
        if (enchConfig.isConfigurationSection("enchanted_books")) {
            ConfigurationSection enchSection = enchConfig.getConfigurationSection("enchanted_books");
            for (String key : enchSection.getKeys(false)) {
                String name = enchSection.getString(key + ".name", key);
                int price = enchSection.getInt(key + ".price", -1);
                int level = enchSection.getInt(key + ".level", 1); // 레벨 필드를 사용

                if (price <= 0) continue;

                // --- [수정된 로직 적용] ---
                EnchantmentParseResult result = parseEnchantmentKey(key, level);

                if (result != null && result.enchantment != null) {
                    ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
                    EnchantmentStorageMeta esm = (EnchantmentStorageMeta) icon.getItemMeta();

                    esm.addStoredEnchant(result.enchantment, result.level, true);

                    esm.setDisplayName("§b인챈트북: " + name + " §7(" + price + "원)");
                    esm.setLore(Collections.singletonList("§7클릭하여 해당 인챈트북 구매"));

                    PersistentDataContainer pdc = esm.getPersistentDataContainer();
                    pdc.set(shopKey, PersistentDataType.STRING, key); // 원본 키 전체 저장
                    pdc.set(priceKey, PersistentDataType.INTEGER, price);
                    pdc.set(kindKey, PersistentDataType.STRING, "enchanted_book");

                    icon.setItemMeta(esm);
                    items.add(icon);
                } else {
                    plugin.getLogger().warning("[BuyMenu] Enchantment not found for key: " + key + " (level: " + level + ")");
                }
            }
        }

        // --- 특별 액션 아이템 추가: 네더의 별 (잡담) ---
        ItemStack actionItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta actionMeta = actionItem.getItemMeta();
        if (actionMeta != null) {
            int actionPrice = 1000000; // 100만원
            actionMeta.setDisplayName("§d잡담");
            actionMeta.setLore(Arrays.asList(
                    "§7가격: §a" + actionPrice + "원",
                    "§7클릭 시 아이템을 얻는 대신,",
                    "§7특별한 메시지를 받습니다."
            ));
            PersistentDataContainer pdc = actionMeta.getPersistentDataContainer();
            pdc.set(shopKey, PersistentDataType.STRING, "GOSSIP_STAR"); // 식별자
            pdc.set(priceKey, PersistentDataType.INTEGER, actionPrice);
            pdc.set(kindKey, PersistentDataType.STRING, "action_item"); // 새로운 종류
            actionItem.setItemMeta(actionMeta);
        }
        items.add(actionItem);
        // --- 끝 ---

        // 페이지 분할 및 GUI 생성 로직
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
            for (int j = start; j < end; j++) inv.setItem(j - start, items.get(j)); // i * perPage 만큼 오프셋 적용
            addFooterTemplates(inv, i);
            pages.add(inv);
        }
    }

    private void addFooterTemplates(Inventory inv, int currentPage) {
        if (inv == null) return;
        // 45번(잔액)은 open 시 플레이어 별로 설정하므로 여기선 비워둠

        // 47번: 판매 메뉴로 이동
        inv.setItem(47, createItem(Material.DIAMOND, "§b판매 메뉴로 이동"));

        // 49번: 페이지 이동 (동적 버튼)
        String pageText;
        Material pageIcon;
        if (currentPage < totalPages - 1) {
            pageText = "§a다음 페이지 §7(" + (currentPage + 2) + "/" + totalPages + ")";
            pageIcon = Material.ARROW;
        } else if (currentPage > 0) {
            pageText = "§c이전 페이지 §7(" + (currentPage) + "/" + totalPages + ")";
            pageIcon = Material.ARROW;
        } else {
            pageText = "§7페이지 없음";
            pageIcon = Material.RED_DYE;
        }
        inv.setItem(49, createItem(pageIcon, pageText));

        // 52번: 뒤로 가기
        inv.setItem(52, createItem(Material.BARRIER, "§c뒤로"));

        // 경계선 채우기
        for (int slot : new int[]{46, 48, 50, 51, 53}) {
            inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    public void openBuyMenu(Player player, int page) {
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

        String title = BASE_TITLE + " (" + (page + 1) + "/" + totalPages + ")";
        Inventory view = Bukkit.createInventory(player, base.getSize(), title);

        view.setContents(base.getContents());

        // 잔액 표시 업데이트
        view.setItem(45, createItem(Material.GOLD_INGOT, "§e잔액: §6" + money.getBalance(player) + "원"));

        player.openInventory(view);
    }

    public void nextPage(Player player, int currentPage) {
        if (currentPage + 1 < totalPages) openBuyMenu(player, currentPage + 1);
    }

    public void previousPage(Player player, int currentPage) {
        if (currentPage - 1 >= 0) openBuyMenu(player, currentPage - 1);
    }

    public int getPageFromTitle(String title) {
        try {
            // 제목 형식: "구매 메뉴 | 페이지 (X/Y)" -> X를 추출
            String inside = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
            return Integer.parseInt(inside) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    public void handleBuyClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        int price = -1;
        String pKey = null;
        String kind = null;

        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Integer pdcPrice = pdc.get(priceKey, PersistentDataType.INTEGER);
            String pdcKey = pdc.get(shopKey, PersistentDataType.STRING);
            String pdcKind = pdc.get(kindKey, PersistentDataType.STRING);
            if (pdcPrice != null) price = pdcPrice;
            if (pdcKey != null) pKey = pdcKey;
            if (pdcKind != null) kind = pdcKind;
        }

        // 아이템 구매 처리 전 필수 검증
        if (price <= 0 || pKey == null) {
            player.sendMessage("§c[상점] 구매할 수 없는 아이템입니다. (가격 또는 키 누락)");
            return;
        }

        // 1. 잔액 확인 (모든 아이템 공통)
        if (money.getBalance(player) < price) {
            player.sendMessage("§c[상점] 잔액이 부족합니다. 현재 잔액: §a" + money.getBalance(player) + "원");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        // --- A. 특별 액션 아이템 처리 (공간 불필요) ---
        if ("action_item".equals(kind) && "GOSSIP_STAR".equals(pKey)) {
            money.takeMoney(player, price); // 돈 차감

            // 메시지 전송 (메시지 내용은 여기서 수정 가능)
            player.sendMessage("§d[잡담] 내가 하고 싶은 이야기\n힘들었을텐데 여기까지 와줘서 고마워!\n난 당분간은 코딩 안하려고..\n너무 많이해서 지쳤거든..\n그리고 시간이 갈수록 재미도 없고 억지로 하는 기분만 들더라\n그래서 나 당분간은 쉬려고\n유튜브도 보고 게임도 하고 책도 읽으면서\n물론 원래도 했어!\n근데 요즘 제대로 하는 느낌이 안들더라..\n아 그리고 타자 연습도 해야겠다!\n암튼 이렇게 긴글 읽어줘서 고맙고 또 재밌게 즐겨줘서 고마워:)\n(추신:아 나도 롤 해볼까?)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return;
        }
        // ------------------------------------


        // 2. 인벤토리 공간 확인 (물리적 아이템만 해당)
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c[상점] 인벤토리가 가득 찼습니다. 공간을 확보해주세요.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        // 3. 돈 차감 (물리적 아이템: 잔액/공간 확인 후 차감)
        money.takeMoney(player, price);

        // 인챈트북 처리
        if ("enchanted_book".equals(kind)) {
            giveEnchantedBook(player, pKey, price, meta.getDisplayName());
            return;
        }

        // 일반 아이템 처리
        Material mat = Material.getMaterial(pKey); // pKey(shopKey)를 사용하여 Material 찾기
        if (mat == null) mat = clicked.getType();

        ItemStack itemToGive = new ItemStack(mat);

        // 원본 아이템의 이름/로어 정보를 복사하여 부여 (GUI 표시용 이름 제거)
        if (meta != null && meta.hasDisplayName()) {
            ItemMeta newItemMeta = itemToGive.getItemMeta();
            if (newItemMeta != null) {
                String originalName = meta.getDisplayName();
                int priceIndex = originalName.lastIndexOf(" §7(");
                if (priceIndex != -1) {
                    originalName = originalName.substring(0, priceIndex).trim();
                }
                // 이름에서 §a를 제거하고 순수 이름만 남김
                newItemMeta.setDisplayName(originalName.replaceAll("§a", "").trim());
                itemToGive.setItemMeta(newItemMeta);
            }
        }

        // 4. 아이템 지급
        player.getInventory().addItem(itemToGive);

        String display = (itemToGive.getItemMeta() != null && itemToGive.getItemMeta().hasDisplayName()) ? itemToGive.getItemMeta().getDisplayName() : pKey;
        player.sendMessage("§a[상점] 구매 완료: " + display + " §7(-" + price + "원)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void giveEnchantedBook(Player player, String pKey, int price, String guiDisplayName) {
        // enchanted_books.yml 파일 로드 (재로드하여 정확한 레벨을 가져옴)
        File enchFile = new File(plugin.getDataFolder(), "enchanted_books.yml");
        FileConfiguration enchConfig = YamlConfiguration.loadConfiguration(enchFile);

        ConfigurationSection itemSection = enchConfig.getConfigurationSection("enchanted_books." + pKey);
        if (itemSection == null) {
            money.addMoney(player, price);
            player.sendMessage("§c[상점] 인챈트북 정보를 찾을 수 없습니다. (관리자 문의)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        int level = itemSection.getInt("level", 1);

        // --- [수정된 로직 적용] ---
        EnchantmentParseResult result = parseEnchantmentKey(pKey, level);

        if (result == null || result.enchantment == null) {
            money.addMoney(player, price);
            player.sendMessage("§c[상점] 해당 인챈트를 찾을 수 없습니다. (관리자에게 문의)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            return;
        }

        // 인챈트북 생성
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta esm = (EnchantmentStorageMeta) book.getItemMeta();
        esm.addStoredEnchant(result.enchantment, result.level, true);
        book.setItemMeta(esm);

        // 이름 정리
        String messageName = guiDisplayName.replace("§b인챈트북: ", "").replaceAll("§7\\(.*원\\)", "").trim();
        ItemMeta finalMeta = book.getItemMeta();
        if (finalMeta != null) {
            // 인챈트북은 인챈트 정보 로어를 위해 이름 설정을 생략하는 것이 더 자연스러울 수 있지만,
            // GUI에 표시된 이름과 일치시키기 위해 여기서 다시 설정합니다.
            finalMeta.setDisplayName(messageName);
            book.setItemMeta(finalMeta);
        }

        // 아이템 지급 (inventory space check already passed in handleBuyClick)
        player.getInventory().addItem(book);

        player.sendMessage("§a[상점] 구매 완료: 인챈트북 " + messageName + " §7(-" + price + "원)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }
}