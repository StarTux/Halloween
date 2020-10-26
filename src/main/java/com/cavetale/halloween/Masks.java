package com.cavetale.halloween;

import com.cavetale.dirty.Dirty;
import com.cavetale.worldmarker.ItemMarker;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.Data;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class Masks {
    private final HalloweenPlugin plugin;
    MaskConfig maskConfig = new MaskConfig();
    public static final String MASK_ITEM_ID = "halloween:mask";
    public final NamespacedKey maskId;
    public final NamespacedKey ownerId;

    public Masks(final HalloweenPlugin plugin) {
        this.plugin = plugin;
        maskId = new NamespacedKey(plugin, "mask_id");
        ownerId = new NamespacedKey(plugin, "owner_id");
    }

    class MaskConfig {
        List<String> lore = new ArrayList<>();
        List<Mask> masks = new ArrayList<>();
    }

    @Data
    static class Mask {
        final String id;
        final Map<String, Object> tag;
        transient String name;
    }

    void enable() {
        plugin.saveResource("masks.json", false);
        Gson gson = new Gson();
        try (FileReader fr = new FileReader(new File(plugin.getDataFolder(), "masks.json"))) {
            maskConfig = gson.fromJson(fr, MaskConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
            maskConfig = new MaskConfig();
        }
        for (Mask mask: maskConfig.masks) {
            Map map = (Map) mask.tag.get("SkullOwner");
            mask.name = (String) map.get("Name");
        }
        plugin.getLogger().info("" + maskConfig.masks.size() + " masks loaded");
    }

    int size() {
        return maskConfig.masks.size();
    }

    List<String> ids() {
        return maskConfig.masks.stream().map(m -> m.id).collect(Collectors.toList());
    }

    Mask getMask(String name) {
        for (Mask mask: maskConfig.masks) {
            if (name.equals(mask.id)) return mask;
        }
        return null;
    }

    ItemStack spawnMask(String name, Player owner) {
        Mask mask = getMask(name);
        if (mask == null) return null;
        ItemStack item = Dirty.newCraftItemStack(Material.PLAYER_HEAD);
        Dirty.setItemTag(item, mask.tag);
        ItemMarker.setId(item, MASK_ITEM_ID);
        List<String> lore = new ArrayList<>(maskConfig.lore);
        if (owner != null) lore.add("" + ChatColor.GRAY + "Property of " + owner.getName() + ".");
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer tag = meta.getPersistentDataContainer();
        tag.set(maskId, PersistentDataType.STRING, mask.id);
        if (owner != null) {
            tag.set(ownerId, PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isOwner(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer tag = meta.getPersistentDataContainer();
        if (!tag.has(ownerId, PersistentDataType.STRING)) return false;
        String id = tag.get(ownerId, PersistentDataType.STRING);
        if (id == null) return false;
        return id.equals(player.getUniqueId().toString());
    }

    public boolean isMask(Mask mask, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer tag = meta.getPersistentDataContainer();
        if (!tag.has(maskId, PersistentDataType.STRING)) return false;
        String id = tag.get(maskId, PersistentDataType.STRING);
        if (id == null) return false;
        return id.equals(mask.id);
    }

    public String getMask(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer tag = meta.getPersistentDataContainer();
        if (!tag.has(maskId, PersistentDataType.STRING)) return null;
        return tag.get(maskId, PersistentDataType.STRING);
    }

    ItemStack spawnCandy() {
        ItemStack item;
        if (ThreadLocalRandom.current().nextBoolean()) {
            item = Dirty.newCraftItemStack(Material.COOKIE);
        } else {
            item = Dirty.newCraftItemStack(Material.PUMPKIN_PIE);
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Halloween Candy");
        meta.setLore(Arrays.asList("Eat this delicious treat",
                                   "for a magical effect."));
        item.setItemMeta(meta);
        ItemMarker.setId(item, "halloween:candy");
        return item;
    }

    void spawnCandy(Location location) {
        ItemStack item = spawnCandy();
        location.getWorld().dropItem(location, item).setPickupDelay(0);
    }
}
