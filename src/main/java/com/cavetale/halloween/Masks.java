package com.cavetale.halloween;

import com.cavetale.dirty.Dirty;
import com.cavetale.itemmarker.ItemMarker;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

@RequiredArgsConstructor
final class Masks {
    private final HalloweenPlugin plugin;
    MaskConfig maskConfig = new MaskConfig();
    public static final String MASK_ITEM_ID = "halloween_mask";
    public static final String MASK_ID = "halloween_mask_id";

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
        this.plugin.saveResource("masks.json", false);
        Gson gson = new Gson();
        try (FileReader fr = new FileReader(new File(this.plugin.getDataFolder(), "masks.json"))) {
            this.maskConfig = gson.fromJson(fr, MaskConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
            this.maskConfig = new MaskConfig();
        }
        for (Mask mask: this.maskConfig.masks) {
            Map map = (Map)mask.tag.get("SkullOwner");
            mask.name = (String)map.get("Name");
        }
        this.plugin.getLogger().info("" + this.maskConfig.masks.size() + " masks loaded");
    }

    int size() {
        return this.maskConfig.masks.size();
    }

    List<String> ids() {
        return this.maskConfig.masks.stream().map(m -> m.id).collect(Collectors.toList());
    }

    Mask getMask(String name) {
        for (Mask mask: this.maskConfig.masks) {
            if (name.equals(mask.id)) return mask;
        }
        return null;
    }

    ItemStack spawnMask(String name, Player owner) {
        Mask mask = getMask(name);
        if (mask == null) return null;
        ItemStack item = Dirty.newCraftItemStack(Material.PLAYER_HEAD);
        Dirty.setItemTag(item, mask.tag);
        ItemMarker.setCustomId(item, MASK_ITEM_ID);
        ItemMarker.setMarker(item, MASK_ID, mask.id);
        if (owner != null) ItemMarker.setOwner(item, owner.getUniqueId());
        List<String> lore = new ArrayList<>(this.maskConfig.lore);
        if (owner != null) lore.add("" + ChatColor.GRAY + "Property of " + owner.getName() + ".");
        HashMap<String, Object> displayMap = new HashMap<>();
        displayMap.put("Lore", lore);
        ItemMarker.setMarker(item, "display", displayMap);
        return item;
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
        item = ItemMarker.setCustomId(item, "halloween_candy");
        return item;
    }

    void spawnCandy(Location location) {
        ItemStack item = spawnCandy();
        location.getWorld().dropItem(location, item).setVelocity(new Vector(ThreadLocalRandom.current().nextDouble() * 0.5 - 0.25,
                                                                            ThreadLocalRandom.current().nextDouble() * 0.25,
                                                                            ThreadLocalRandom.current().nextDouble() * 0.5 - 0.25));
    }
}
