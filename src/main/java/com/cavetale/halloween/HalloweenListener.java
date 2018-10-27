package com.cavetale.halloween;

import com.cavetale.dungeons.DungeonLootEvent;
import com.cavetale.itemmarker.ItemMarker;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@RequiredArgsConstructor
final class HalloweenListener implements Listener {
    private final HalloweenPlugin plugin;

    @EventHandler
    public void onDungeonLoot(DungeonLootEvent event) {
        Player player = event.getPlayer();
        int slot = ThreadLocalRandom.current().nextInt(event.getInventory().getSize());
        event.getInventory().setItem(slot, plugin.masks.spawnCandy());
        Persistence.PlayerData playerData = this.plugin.persistence.getPlayerData(player);
        String mask = playerData.getMaskDrop();
        if (mask != null) {
            slot = ThreadLocalRandom.current().nextInt(event.getInventory().getSize());
            event.getInventory().setItem(slot, plugin.masks.spawnMask(mask, player));
            playerData.setMaskDrop(null);
            this.plugin.persistence.save();
            this.plugin.playJingle(player);
            this.plugin.playEffect(player, event.getBlock().getLocation().add(0.5, 1.5, 0.5));
        } else {
            mask = null;
        }
        this.plugin.getLogger().info("Spawning Halloween dungeon loot for " + player.getName() + " mask=" + mask);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (ItemMarker.hasCustomId(item, "halloween_candy")) {
            givePotionEffect(event.getPlayer());
            return;
        }
        if (event.getItem().equals(item)) return;
        item = event.getPlayer().getInventory().getItemInOffHand();
        if (ItemMarker.hasCustomId(item, "halloween_candy")) {
            givePotionEffect(event.getPlayer());
            return;
        }
    }

    void givePotionEffect(Player player) {
        this.plugin.getLogger().info(player.getName() + " ate halloween candy");
        this.plugin.playCandyEffect(player);
        List<PotionEffectType> types = Arrays.asList(PotionEffectType.ABSORPTION,
                                                     PotionEffectType.DOLPHINS_GRACE,
                                                     PotionEffectType.FAST_DIGGING,
                                                     PotionEffectType.INCREASE_DAMAGE,
                                                     PotionEffectType.GLOWING,
                                                     PotionEffectType.FIRE_RESISTANCE,
                                                     PotionEffectType.REGENERATION,
                                                     PotionEffectType.SATURATION,
                                                     PotionEffectType.SPEED,
                                                     PotionEffectType.WATER_BREATHING,
                                                     PotionEffectType.LUCK);
        PotionEffectType pet = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        int amplifier = ThreadLocalRandom.current().nextInt(3);
        int duration = 20*30 + ThreadLocalRandom.current().nextInt(40*30);
        player.addPotionEffect(new PotionEffect(pet, duration, amplifier));
    }
}
