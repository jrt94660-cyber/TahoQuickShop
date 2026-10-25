package com.taho.quickshop;

import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.Map;

public final class TahoQuickShop extends JavaPlugin implements Listener, CommandExecutor {
    private Economy economy;
    private NamespacedKey categoryKey;
    private NamespacedKey itemKey;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        categoryKey = new NamespacedKey(this, "category");
        itemKey = new NamespacedKey(this, "shop_item");
        actionKey = new NamespacedKey(this, "shop_action");
        setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("shop") != null) getCommand("shop").setExecutor(this);
        if (getCommand("shardshop") != null) getCommand("shardshop").setExecutor(this);
        getLogger().info("TahoQuickShop enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shardshop")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }
            if (!player.hasPermission("tahoquickshop.use")) {
                message(player, "no-permission");
                return true;
            }
            openCategory(player, "shards", 0);
            return true;
        }
        if (!command.getName().equalsIgnoreCase("shop")) return false;

        if (args.length > 0) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("tahoquickshop.admin")) {
                    sender.sendMessage(color(getConfig().getString("settings.messages.no-permission", "&cYou don't have permission.")));
                    return true;
                }
                reloadConfig();
                setupEconomy();
                sender.sendMessage(color("&aTahoQuickShop configuration reloaded."));
                return true;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
                if (!sender.hasPermission("tahoquickshop.admin")) {
                    sender.sendMessage(color(getConfig().getString("settings.messages.no-permission", "&cYou don't have permission.")));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cOnly players can add an item from their hand."));
                    return true;
                }
                addHeldItem(player, args[1]);
                return true;
            }

            sender.sendMessage(color("&cUsage: /shop | /shop reload | /shop add <category>"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        openMainShop(player);
        return true;
    }

    private void addHeldItem(Player player, String categoryId) {
        ConfigurationSection category = getConfig().getConfigurationSection("categories." + categoryId);
        if (category == null) {
            player.sendMessage(color("&cCategory '" + categoryId + "' doesn't exist. Use a category ID from config.yml."));
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(color("&cHold the item you want to add in your main hand."));
            return;
        }

        String baseKey = held.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');
        ConfigurationSection items = category.getConfigurationSection("items");
        if (items == null) {
            category.createSection("items");
            items = category.getConfigurationSection("items");
        }
        String itemId = baseKey;
        int suffix = 2;
        while (items != null && items.contains(itemId)) {
            itemId = baseKey + "-" + suffix++;
        }
        if (items == null) {
            player.sendMessage(color("&cCouldn't access this category's items section."));
            return;
        }

        String path = "categories." + categoryId + ".items." + itemId;
        ItemMeta meta = held.getItemMeta();
        String displayName = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName() : prettyMaterialName(held.getType());
        getConfig().set(path + ".material", held.getType().name());
        getConfig().set(path + ".name", displayName);
        getConfig().set(path + ".price", 0.0);
        getConfig().set(path + ".amount", held.getAmount());
        getConfig().set(path + ".slot", -1);
        if (meta != null && meta.hasLore()) {
            getConfig().set(path + ".lore", meta.getLore());
        } else {
            getConfig().set(path + ".lore", List.of("&7Price: &a$%price%"));
        }
        saveConfig();

        player.sendMessage(color("&aAdded &f" + displayName + " &ato category &e" + categoryId
                + "&a as &e" + itemId + "&a. Its price is currently &e0&a; edit it in config.yml."));
    }

    private String prettyMaterialName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return "&f" + name;
    }

    private void setupEconomy() {
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();
        if (economy == null) getLogger().warning("Vault economy provider not found; purchases are disabled until one is available.");
    }

    public void openMainShop(Player player) {
        if (!player.hasPermission("tahoquickshop.use")) {
            message(player, "no-permission");
            return;
        }
        ConfigurationSection categories = getConfig().getConfigurationSection("categories");
        if (categories == null) return;
        int rows = 3;
        Inventory inventory = Bukkit.createInventory(new ShopHolder("main", 0), rows * 9,
                color(getConfig().getString("settings.title", "&8Shop")));
        fill(inventory);
        for (String id : categories.getKeys(false)) {
            ConfigurationSection category = categories.getConfigurationSection(id);
            if (category == null) continue;
            Material material = material(category.getString("material"), Material.CHEST);
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(category.getString("name", id)));
                meta.setLore(colorList(category.getStringList("lore")));
                meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, id);
                icon.setItemMeta(meta);
            }
            int slot = category.getInt("slot", 0);
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, icon);
        }
        player.openInventory(inventory);
    }

    private void openCategory(Player player, String id, int page) {
        ConfigurationSection category = getConfig().getConfigurationSection("categories." + id);
        if (category == null) { openMainShop(player); return; }
        int rows = 3;
        Inventory inventory = Bukkit.createInventory(new ShopHolder(id, page, null, 1), rows * 9,
                color(category.getString("title", "&8Shop")));
        fill(inventory);
        ConfigurationSection items = category.getConfigurationSection("items");
        if (items != null) {
            List<String> keys = new ArrayList<>(items.getKeys(false));
            int contentStart = 9;
            int contentEnd = 18;
            int capacity = 9;
            int start = page * capacity;
            for (int i = start; i < Math.min(keys.size(), start + capacity); i++) {
                String key = keys.get(i);
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                int slot = contentStart + (i - start);
                ItemStack stack = new ItemStack(material(item.getString("material"), Material.STONE),
                        Math.max(1, Math.min(64, item.getInt("amount", 1))));
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(color(item.getString("name", key)));
                    List<String> lore = new ArrayList<>();
                    for (String line : item.getStringList("lore")) {
                        lore.add(color(line.replace("%price%", format(item.getDouble("price")))));
                    }
                    if (lore.isEmpty()) lore.add(color("&7Price: &a$" + format(item.getDouble("price"))));
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id + ":" + key);
                    stack.setItemMeta(meta);
                }
                if (slot >= 0 && slot < contentEnd && slot < inventory.getSize()) inventory.setItem(slot, stack);
            }
        }
        ItemStack back = new ItemStack(material(getConfig().getString("settings.navigation.back-material"), Material.RED_STAINED_GLASS_PANE));
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(color(getConfig().getString("settings.navigation.back-name", "&cBack")));
            backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
            back.setItemMeta(backMeta);
        }
        inventory.setItem(18, back);
        if (items != null && items.getKeys(false).size() > (page + 1) * 9) {
            ItemStack next = new ItemStack(material(getConfig().getString("settings.navigation.next-material"), Material.ARROW));
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(getConfig().getString("settings.navigation.next-name", "&aNext page")));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next");
                next.setItemMeta(meta);
            }
            inventory.setItem(26, next);
        }
        if (page > 0) {
            ItemStack previous = new ItemStack(Material.ARROW);
            ItemMeta meta = previous.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(getConfig().getString("settings.navigation.previous-name", "&cPrevious page")));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "previous");
                previous.setItemMeta(meta);
            }
            inventory.setItem(25, previous);
        }
        player.openInventory(inventory);
    }

    private void openQuantitySelector(Player player, String itemRef, int amount) {
        String[] parts = itemRef.split(":", 2);
        if (parts.length != 2) return;
        ConfigurationSection item = getConfig().getConfigurationSection("categories." + parts[0] + ".items." + parts[1]);
        if (item == null) return;
        Inventory inventory = Bukkit.createInventory(new ShopHolder(parts[0], 0, itemRef, amount), 27, color("&8Select Amount"));
        fill(inventory);
        Material itemMaterial = material(item.getString("material"), Material.STONE);
        boolean noAmount = isNoAmountItem(itemMaterial);
        int largeStep = itemMaterial.getMaxStackSize() <= 16 ? 16 : 64;
        if (!noAmount) {
            addActionItem(inventory, 9, Material.RED_CONCRETE, "&c-" + largeStep, "qty:remove:" + largeStep);
            addActionItem(inventory, 10, Material.RED_CONCRETE, "&c-10", "qty:remove:10");
            addActionItem(inventory, 11, Material.RED_CONCRETE, "&c-1", "qty:remove:1");
        }
        ItemStack chosen = new ItemStack(itemMaterial, 1);
        ItemMeta chosenMeta = chosen.getItemMeta();
        if (chosenMeta != null) {
            chosenMeta.setDisplayName(color(item.getString("name", parts[1])));
            chosenMeta.setLore(noAmount
                    ? List.of(color("&7Amount: &e1"), color("&7Total: &a" + categoryPricePrefix(parts[0]) + format(item.getDouble("price"))))
                    : List.of(color("&7Amount: &e" + amount), color("&7Total: &a" + categoryPricePrefix(parts[0]) + format(item.getDouble("price") * amount)), color("&8Use the buttons to change amount")));
            chosen.setItemMeta(chosenMeta);
        }
        inventory.setItem(13, chosen);
        if (!noAmount) {
            addActionItem(inventory, 15, Material.LIME_CONCRETE, "&a+1", "qty:add:1");
            addActionItem(inventory, 16, Material.LIME_CONCRETE, "&a+10", "qty:add:10");
            addActionItem(inventory, 17, Material.LIME_CONCRETE, "&aSet to " + largeStep, "qty:set:" + largeStep);
        }
        addActionItem(inventory, 18, Material.ARROW, "&cBack", "qty:back");
        addActionItem(inventory, 22, Material.EMERALD, "&aConfirm Purchase", "qty:confirm");
        player.openInventory(inventory);
    }

    private boolean isNoAmountItem(Material material) {
        return java.util.Set.of(
                Material.TOTEM_OF_UNDYING, Material.POTION, Material.SPLASH_POTION,
                Material.ENCHANTED_BOOK, Material.SHULKER_BOX, Material.ELYTRA,
                Material.BOW, Material.CROSSBOW, Material.FISHING_ROD,
                Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.STONE_PICKAXE,
                Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.SHIELD
        ).contains(material);
    }

    private String categoryPricePrefix(String categoryId) {
        ConfigurationSection category = getConfig().getConfigurationSection("categories." + categoryId);
        if (category != null && category.getString("economy", "vault").equalsIgnoreCase("coinsengine")) {
            return category.getString("currency-display", "Shards") + " ";
        }
        return "$";
    }

    private void addActionItem(Inventory inventory, int slot, Material material, String name, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
        }
        inventory.setItem(slot, stack);
    }

    private void fill(Inventory inventory) {
        if (!getConfig().getBoolean("settings.filler.enabled", false)) return;
        ItemStack filler = new ItemStack(material(getConfig().getString("settings.filler.material"), Material.BLACK_STAINED_GLASS_PANE));
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("settings.filler.name", " ")));
            filler.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        String category = pdc.get(categoryKey, PersistentDataType.STRING);
        if (category != null && holder.category.equals("main")) {
            openCategory(player, category, 0);
            return;
        }
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action != null && action.startsWith("qty:") && holder.selectionRef != null) {
            int amount = holder.amount;
            if (action.startsWith("qty:add:")) amount += Integer.parseInt(action.substring("qty:add:".length()));
            else if (action.startsWith("qty:remove:")) amount -= Integer.parseInt(action.substring("qty:remove:".length()));
            else if (action.startsWith("qty:set:")) amount = Integer.parseInt(action.substring("qty:set:".length()));
            else if (action.equals("qty:confirm")) { purchase(player, holder.selectionRef, amount); return; }
            else if (action.equals("qty:back")) { openCategory(player, holder.category, holder.page); return; }
            amount = Math.max(1, Math.min(64, amount));
            openQuantitySelector(player, holder.selectionRef, amount);
            return;
        }
        if (action != null) {
            if (action.equals("back")) openMainShop(player);
            else if (action.equals("next")) openCategory(player, holder.category, holder.page + 1);
            else if (action.equals("previous")) openCategory(player, holder.category, Math.max(0, holder.page - 1));
            return;
        }
        String itemRef = pdc.get(itemKey, PersistentDataType.STRING);
        if (itemRef != null) openQuantitySelector(player, itemRef, 1);
    }

    private void purchase(Player player, String itemRef, int requestedAmount) {
        String[] parts = itemRef.split(":", 2);
        if (parts.length != 2) return;
        ConfigurationSection category = getConfig().getConfigurationSection("categories." + parts[0]);
        ConfigurationSection item = getConfig().getConfigurationSection("categories." + parts[0] + ".items." + parts[1]);
        if (category == null || item == null) return;

        int amount = Math.max(1, Math.min(64, requestedAmount));
        double price = item.getDouble("price", -1) * amount;
        if (price < 0 || !Double.isFinite(price)) { message(player, "purchase-failed"); return; }

        String provider = category.getString("economy", "vault").trim().toLowerCase(Locale.ROOT);
        String currency = category.getString("currency", "").trim();
        double balance = readBalance(player, provider, currency);
        if (Double.isNaN(balance)) { message(player, "economy-missing"); return; }
        if (balance + 1.0E-8 < price) { message(player, "not-enough"); return; }

        String command = item.getString("command", "").trim();
        ItemStack reward = null;
        if (command.isEmpty()) {
            reward = new ItemStack(material(item.getString("material"), Material.STONE), 1);
            ItemMeta rewardMeta = reward.getItemMeta();
            if (rewardMeta != null) {
                rewardMeta.setDisplayName(color(item.getString("name", parts[1])));
                reward.setItemMeta(rewardMeta);
            }
            if (player.getInventory().firstEmpty() == -1 && !canStack(player, reward)) {
                message(player, "inventory-full");
                return;
            }
        }

        if (!changeBalance(player, provider, currency, -price)) {
            message(player, "purchase-failed");
            return;
        }

        if (command.isEmpty()) {
            Map<Integer, ItemStack> leftover = new java.util.HashMap<>();
            int remaining = amount;
            while (remaining > 0) {
                ItemStack stack = reward.clone();
                int stackAmount = Math.min(remaining, stack.getMaxStackSize());
                stack.setAmount(stackAmount);
                Map<Integer, ItemStack> addResult = player.getInventory().addItem(stack);
                leftover.putAll(addResult);
                if (!addResult.isEmpty()) break;
                remaining -= stackAmount;
            }
            if (!leftover.isEmpty()) {
                changeBalance(player, provider, currency, price);
                message(player, "inventory-full");
                return;
            }
        } else {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            boolean dispatched = Bukkit.dispatchCommand(console, command
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount)));
            if (!dispatched) {
                changeBalance(player, provider, currency, price);
                message(player, "purchase-failed");
                return;
            }
        }

        String displayName = item.getString("name", parts[1]);
        String success = category.getString("purchase-success-message",
                getConfig().getString("settings.messages.purchase-success", "&aPurchased &e%amount%x %item% &afor &e$%price%&a."));
        player.sendMessage(color(success.replace("%amount%", String.valueOf(amount))
                .replace("%item%", stripColor(displayName))
                .replace("%price%", format(price))
                .replace("%currency%", category.getString("currency-display", "$"))));
        if (getConfig().getBoolean("settings.purchase.close-after-purchase", false)) player.closeInventory();
    }

    private boolean isCustomCurrencyProvider(String provider) {
        return provider.equals("coinsengine") || provider.equals("excellenteconomy") || provider.equals("excellent-economy");
    }

    private Object excellentEconomyApi() throws ReflectiveOperationException {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (plugin == null || !plugin.isEnabled()) return null;
        return plugin.getClass().getMethod("getAPI").invoke(plugin);
    }

    private double readBalance(Player player, String provider, String currency) {
        if (!isCustomCurrencyProvider(provider)) {
            return economy == null ? Double.NaN : economy.getBalance(player);
        }
        if (currency.isEmpty()) return Double.NaN;
        try {
            Object api = excellentEconomyApi();
            if (api != null) {
                Class<?> apiType = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                boolean exists = (boolean) apiType.getMethod("hasCurrency", String.class).invoke(api, currency);
                if (!exists) {
                    getLogger().warning("ExcellentEconomy currency '" + currency + "' is not registered. Check its exact ID in the ExcellentEconomy currency configuration.");
                    return Double.NaN;
                }
                return ((Number) apiType.getMethod("getBalance", Player.class, String.class).invoke(api, player, currency)).doubleValue();
            }
            if (provider.equals("coinsengine")) {
                Class<?> coinsApi = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
                boolean exists = (boolean) coinsApi.getMethod("hasCurrency", String.class).invoke(null, currency);
                if (!exists) {
                    getLogger().warning("CoinsEngine currency '" + currency + "' is not registered.");
                    return Double.NaN;
                }
                return ((Number) coinsApi.getMethod("getBalance", UUID.class, String.class).invoke(null, player.getUniqueId(), currency)).doubleValue();
            }
            return Double.NaN;
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("Could not access " + provider + " currency '" + currency + "': " + ex.getMessage());
            return Double.NaN;
        }
    }

    private boolean changeBalance(Player player, String provider, String currency, double amount) {
        if (!isCustomCurrencyProvider(provider)) {
            if (economy == null) return false;
            if (amount < 0) return economy.withdrawPlayer(player, -amount).transactionSuccess();
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        if (currency.isEmpty()) return false;
        try {
            Object api = excellentEconomyApi();
            if (api != null) {
                Class<?> apiType = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
                String methodName = amount < 0 ? "withdraw" : "deposit";
                Object result = apiType.getMethod(methodName, Player.class, String.class, double.class)
                        .invoke(api, player, currency, Math.abs(amount));
                return result instanceof Boolean && (Boolean) result;
            }
            if (provider.equals("coinsengine")) {
                Class<?> coinsApi = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
                String methodName = amount < 0 ? "removeBalance" : "addBalance";
                Object result = coinsApi.getMethod(methodName, UUID.class, String.class, double.class)
                        .invoke(null, player.getUniqueId(), currency, Math.abs(amount));
                return result instanceof Boolean && (Boolean) result;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("Could not change " + provider + " currency '" + currency + "': " + ex.getMessage());
            return false;
        }
    }

    private boolean canStack(Player player, ItemStack reward) {
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null) return true;
            if (content.isSimilar(reward) && content.getAmount() < content.getMaxStackSize()) return true;
        }
        return false;
    }

    private void message(Player player, String key) {
        player.sendMessage(color(getConfig().getString("settings.messages." + key, "&cShop error.")));
    }

    private String format(double amount) {
        if (Math.rint(amount) == amount) return String.format(Locale.US, "%.0f", amount);
        return String.format(Locale.US, "%.2f", amount);
    }

    private Material material(String input, Material fallback) {
        if (input == null) return fallback;
        Material found = Material.matchMaterial(input);
        return found == null ? fallback : found;
    }

    private String color(String input) {
        if (input == null) return "";
        String legacy = ChatColor.translateAlternateColorCodes('&', input);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(legacy);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(out, ChatColor.of("#" + matcher.group(1)).toString());
        matcher.appendTail(out);
        return out.toString();
    }

    private List<String> colorList(List<String> lines) {
        List<String> output = new ArrayList<>();
        for (String line : lines) output.add(color(line));
        return output;
    }

    private String stripColor(String input) {
        return ChatColor.stripColor(color(input));
    }

    private static final class ShopHolder implements InventoryHolder {
        private final String category;
        private final int page;
        private final String selectionRef;
        private final int amount;
        private ShopHolder(String category, int page) { this(category, page, null, 1); }
        private ShopHolder(String category, int page, String selectionRef, int amount) {
            this.category = category; this.page = page; this.selectionRef = selectionRef; this.amount = amount;
        }
        @Override public Inventory getInventory() { return null; }
    }
}
