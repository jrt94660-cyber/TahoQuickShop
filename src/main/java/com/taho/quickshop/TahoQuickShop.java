package com.taho.quickshop;

import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.Map;
import java.util.HashMap;

public final class TahoQuickShop extends JavaPlugin implements Listener {
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
        getLogger().info("TahoQuickShop enabled. No commands are registered in this build.");
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();
        if (economy == null) getLogger().warning("Vault economy provider not found; purchases are disabled until one is available.");
    }

    /** Public entry point for a future command or another plugin to open the shop. */
    public void openMainShop(Player player) {
        if (!player.hasPermission("tahoquickshop.use")) {
            message(player, "no-permission");
            return;
        }
        ConfigurationSection categories = getConfig().getConfigurationSection("categories");
        if (categories == null) return;
        int rows = Math.max(1, Math.min(6, getConfig().getInt("settings.rows", 3)));
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
        int rows = Math.max(1, Math.min(6, category.getInt("rows", 3)));
        Inventory inventory = Bukkit.createInventory(new ShopHolder(id, page), rows * 9,
                color(category.getString("title", "&8Shop")));
        fill(inventory);
        ConfigurationSection items = category.getConfigurationSection("items");
        if (items != null) {
            List<String> keys = new ArrayList<>(items.getKeys(false));
            int contentStart = 0;
            int contentEnd = inventory.getSize() - 9;
            int capacity = Math.max(1, contentEnd - contentStart);
            int start = page * capacity;
            for (int i = start; i < Math.min(keys.size(), start + capacity); i++) {
                String key = keys.get(i);
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                int configuredSlot = item.getInt("slot", -1);
                int slot = configuredSlot >= contentStart && configuredSlot < contentEnd
                        ? configuredSlot : contentStart + (i - start);
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
        inventory.setItem(inventory.getSize() - 9, back);
        if (items != null && items.getKeys(false).size() > (page + 1) * Math.max(1, inventory.getSize() - 9)) {
            ItemStack next = new ItemStack(material(getConfig().getString("settings.navigation.next-material"), Material.ARROW));
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(getConfig().getString("settings.navigation.next-name", "&aNext page")));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next");
                next.setItemMeta(meta);
            }
            inventory.setItem(inventory.getSize() - 1, next);
        }
        if (page > 0) {
            ItemStack previous = new ItemStack(Material.ARROW);
            ItemMeta meta = previous.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(getConfig().getString("settings.navigation.previous-name", "&cPrevious page")));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "previous");
                previous.setItemMeta(meta);
            }
            inventory.setItem(inventory.getSize() - 2, previous);
        }
        player.openInventory(inventory);
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
        if (action != null) {
            if (action.equals("back")) openMainShop(player);
            else if (action.equals("next")) openCategory(player, holder.category, holder.page + 1);
            else if (action.equals("previous")) openCategory(player, holder.category, Math.max(0, holder.page - 1));
            return;
        }
        String itemRef = pdc.get(itemKey, PersistentDataType.STRING);
        if (itemRef != null) purchase(player, itemRef);
    }

    private void purchase(Player player, String itemRef) {
        String[] parts = itemRef.split(":", 2);
        if (parts.length != 2) return;
        ConfigurationSection item = getConfig().getConfigurationSection("categories." + parts[0] + ".items." + parts[1]);
        if (item == null) return;
        double price = item.getDouble("price", -1);
        int amount = Math.max(1, Math.min(64, item.getInt("amount", 1)));
        if (price < 0 || !Double.isFinite(price)) { message(player, "purchase-failed"); return; }
        String command = item.getString("command", "").trim();
        if (command.isEmpty()) {
            if (economy == null) { message(player, "economy-missing"); return; }
            if (economy.getBalance(player) + 1.0E-8 < price) { message(player, "not-enough"); return; }
            ItemStack reward = new ItemStack(material(item.getString("material"), Material.STONE), amount);
            ItemMeta rewardMeta = reward.getItemMeta();
            if (rewardMeta != null) {
                rewardMeta.setDisplayName(color(item.getString("name", parts[1])));
                reward.setItemMeta(rewardMeta);
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
            if (!leftover.isEmpty()) { message(player, "inventory-full"); return; }
            var response = economy.withdrawPlayer(player, price);
            if (!response.transactionSuccess()) {
                player.getInventory().removeItem(reward);
                message(player, "purchase-failed");
                return;
            }
        } else {
            // Configured reward commands are dispatched only after the economy transaction succeeds.
            if (economy == null) { message(player, "economy-missing"); return; }
            if (economy.getBalance(player) + 1.0E-8 < price) { message(player, "not-enough"); return; }
            var response = economy.withdrawPlayer(player, price);
            if (!response.transactionSuccess()) { message(player, "purchase-failed"); return; }
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, command.replace("%player%", player.getName()).replace("%amount%", String.valueOf(amount)));
        }
        String displayName = item.getString("name", parts[1]);
        String success = getConfig().getString("settings.messages.purchase-success", "&aPurchased %amount%x %item% for $%price%.");
        player.sendMessage(color(success.replace("%amount%", String.valueOf(amount))
                .replace("%item%", stripColor(displayName)).replace("%price%", format(price))));
        if (getConfig().getBoolean("settings.purchase.close-after-purchase", false)) player.closeInventory();
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
        private ShopHolder(String category, int page) { this.category = category; this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }
}
