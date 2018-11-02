package com.cavetale.halloween;

import com.cavetale.dungeons.DungeonLootEvent;
import com.cavetale.itemmarker.ItemMarker;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@RequiredArgsConstructor
final class HalloweenListener implements Listener {
    private final HalloweenPlugin plugin;

    @EventHandler
    public void onDungeonLoot(DungeonLootEvent event) {
        if (event.getDungeon().isRaided()) return;
        Player player = event.getPlayer();
        int slot = ThreadLocalRandom.current().nextInt(event.getInventory().getSize());
        event.getInventory().setItem(slot, plugin.masks.spawnCandy());
        Persistence.PlayerData playerData = this.plugin.persistence.getPlayerData(player);
        String mask = playerData.rollMaskDrop(this.plugin);
        slot = ThreadLocalRandom.current().nextInt(event.getInventory().getSize());
        event.getInventory().setItem(slot, plugin.masks.spawnMask(mask, player));
        this.plugin.playJingle(player);
        this.plugin.playEffect(player, event.getBlock().getLocation().add(0.5, 1.5, 0.5));
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
        int duration = 20 * 30 + ThreadLocalRandom.current().nextInt(40 * 30);
        player.addPotionEffect(new PotionEffect(pet, duration, amplifier));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (ItemMarker.hasCustomId(item, Masks.MASK_ITEM_ID)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        for (Boss boss: this.plugin.bosses) {
            if (boss.isEntity(event.getEntity())) boss.onEntityDamage(event);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        for (Boss boss: this.plugin.bosses) {
            if (boss.isEntity(event.getEntity())) boss.onEntityDamageByEntity(event);
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        for (Boss boss: this.plugin.bosses) {
            if (boss.isEntity(event.getEntity())) boss.onEntityCombust(event);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        for (Boss boss: this.plugin.bosses) {
            if (boss.isEntity(event.getEntity())) boss.onEntityDeath(event);
        }
    }

    // Frost walker
    @EventHandler(ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!event.getBlock().getWorld().getName().equals(this.plugin.collector.getHalloweenWorld())) return;
        event.setCancelled(true);
    }
}
