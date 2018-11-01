package com.cavetale.halloween;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@RequiredArgsConstructor
final class Boss {
    final HalloweenPlugin plugin;
    final Config config;
    Persistence persistence = new Persistence();
    LivingEntity entity = null;
    // non-persistence
    boolean shield;
    long shieldCooldown; // ticks
    BossBar bossBar;
    Random random = new Random(System.nanoTime());

    final class Config {
        String id;
        List<Double> spawnLocation;
        double healing = 10.0;
        transient double x, y = -1.0, z;
        transient int cx, cz;
        transient int index;
    }

    final class Persistence {
        long respawnCooldown = 0; // absolute time
        long ticksLived = 0;
        double health = 0.0;
        Set<UUID> damagers = new HashSet<>();
    }

    World getWorld() {
        return Bukkit.getWorld(this.plugin.collector.getHalloweenWorld());
    }

    Location getSpawnLocation() {
        try {
            return new Location(Bukkit.getWorld(this.plugin.collector.getHalloweenWorld()), this.config.x, this.config.y, this.config.z);
        } catch (Exception e) {
            return null;
        }
    }

    static List<Boss> loadBosses(HalloweenPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "bosses.json");
        List<Boss> result = new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            List<Config> configs = new Gson().fromJson(reader, new TypeToken<List<Config>>() { }.getType());
            int index = 0;
            for (Config cfg: configs) {
                cfg.x = cfg.spawnLocation.get(0);
                cfg.y = cfg.spawnLocation.get(1);
                cfg.z = cfg.spawnLocation.get(2);
                cfg.cx = (int)Math.floor(cfg.x) >> 4;
                cfg.cz = (int)Math.floor(cfg.z) >> 4;
                cfg.index = index++;
                Boss boss = new Boss(plugin, cfg);
                boss.loadPersistence();
                result.add(boss);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return result;
    }

    void savePersistence() {
        File dir = new File(this.plugin.getDataFolder(), "bosses");
        dir.mkdirs();
        File file = new File(dir, this.config.id + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(this.persistence, writer);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void loadPersistence() {
        File dir = new File(this.plugin.getDataFolder(), "bosses");
        File file = new File(dir, this.config.id + ".json");
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            this.persistence = new Gson().fromJson(reader, Persistence.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    boolean isAlive() {
        return this.entity != null && this.entity.isValid();
    }

    void remove() {
        if (this.entity != null) this.entity.remove();
        if (this.bossBar != null) this.bossBar.removeAll();
        this.bossBar = null;
    }

    void kill() {
        if (this.entity != null && this.entity.isValid()) this.entity.setHealth(0.0);
        if (this.bossBar != null) this.bossBar.removeAll();
        this.bossBar = null;
    }

    boolean isEntity(Entity other) {
        return this.entity != null && this.entity.equals(other);
    }

    void onTick() {
        if (entity == null || !entity.isValid()) {
            if (this.bossBar != null) {
                this.bossBar.removeAll();
                this.bossBar = null;
            }
            for (Boss other: this.plugin.bosses) {
                if (other.isAlive()) return;
            }
            if (this.persistence.respawnCooldown < System.currentTimeMillis()
                && this.config.y >= 0.0) {
                World world = this.getWorld();
                if (world != null && world.isChunkLoaded(this.config.cx, this.config.cz)) {
                    spawnEntity();
                }
            }
        } else {
            if ((this.persistence.ticksLived % 20) == 0) {
                if (this.bossBar == null) {
                    this.bossBar = Bukkit.getServer().createBossBar(this.entity.getCustomName(), BarColor.PURPLE, BarStyle.SOLID, BarFlag.DARKEN_SKY, BarFlag.PLAY_BOSS_MUSIC);
                }
                World world = this.getWorld();
                bossBar.setProgress(this.entity.getHealth() / this.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                for (Player other: Bukkit.getServer().getOnlinePlayers()) {
                    if (other.getWorld().equals(world)) {
                        this.bossBar.addPlayer(other);
                    } else {
                        this.bossBar.removePlayer(other);
                    }
                }
            }
            tickLiving();
            this.persistence.ticksLived += 1;
            if ((this.persistence.ticksLived % 100) == 0) {
                savePersistence();
            }
        }
    }

    void spawnEntity() {
        Location location = getSpawnLocation();
        if (location == null) return;
        final double health = 1000.0;
        switch (this.config.index) {
        case 0: {
            this.entity = location.getWorld().spawn(location, Skeleton.class, (skeleton) -> {
                    skeleton.setCustomName("" + ChatColor.GRAY + ChatColor.BOLD + ChatColor.ITALIC + "Skellington");
                    skeleton.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                    skeleton.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                    skeleton.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                    skeleton.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
                });
            break;
        }
        case 1: {
            this.entity = location.getWorld().spawn(location, ElderGuardian.class, (elderGuardian) -> {
                    elderGuardian.setCustomName("" + ChatColor.DARK_AQUA + ChatColor.BOLD + ChatColor.ITALIC + "Deep Fear");
                });
            break;
        }
        case 2: {
            this.entity = location.getWorld().spawn(location, WitherSkeleton.class, (wither) -> {
                    wither.setCustomName("" + ChatColor.GOLD + ChatColor.BOLD + ChatColor.ITALIC + "Pumpkin King");
                    wither.getEquipment().setHelmet(new ItemStack(Material.JACK_O_LANTERN));
                    wither.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                });
            break;
        }
        default: this.entity = null;
        }
        this.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        if (this.persistence.health <= 0.0) this.persistence.health = health;
        this.entity.setHealth(this.persistence.health);
        this.entity.setPersistent(false);
        this.persistence.ticksLived = 0;
        savePersistence();
    }

    void tickLiving() {
        if ((this.persistence.ticksLived % 20) == 10) {
            double max = this.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            this.entity.setHealth(Math.min(this.entity.getHealth() + this.config.healing, max));
        }
        switch (this.config.index) {
        case 0: {
            Skeleton skeleton = (Skeleton)this.entity;
            if (skeleton.getVehicle() == null) {
                Spider spider = skeleton.getWorld().spawn(skeleton.getLocation(), Spider.class);
                spider.addPassenger(skeleton);
                for (int i = 0; i < 4; i += 1) {
                    skeleton.getWorld().spawn(skeleton.getLocation(), CaveSpider.class);
                }
            }
            break;
        }
        case 1: {
            ElderGuardian elderGuardian = (ElderGuardian)this.entity;
            if (this.shield) {
                for (int i = 0; i < 3; i += 1) {
                    Vector vec = new Vector(random.nextDouble() - 0.5,
                                            random.nextDouble() - 0.5,
                                            random.nextDouble() - 0.5)
                        .normalize()
                        .multiply(4.0);
                    Location loc = elderGuardian.getEyeLocation().add(vec);
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if ((this.persistence.ticksLived % 20L) == 0 && random.nextInt(3) == 0) {
                    List<Player> targets = new ArrayList<>();
                    for (Entity nearby: this.entity.getNearbyEntities(24.0, 24.0, 24.0)) {
                        if (nearby instanceof Player && this.entity.hasLineOfSight(nearby)) {
                            targets.add((Player)nearby);
                        }
                    }
                    if (!targets.isEmpty()) {
                        Collections.shuffle(targets);
                        Player target = targets.get(0);
                        Vector v = target.getLocation().subtract(this.entity.getLocation()).toVector().normalize().multiply(2.0);
                        Trident tri = this.entity.launchProjectile(Trident.class, v);
                        tri.setPickupStatus(Trident.PickupStatus.DISALLOWED);
                    }
                }
            } else if (this.shieldCooldown <= 0) {
                this.shield = true;
                this.entity.getWorld().playSound(this.entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 2.0f);
            } else {
                this.shieldCooldown -= 1;
                if ((this.persistence.ticksLived % 20) == 0 && random.nextInt(4) == 0) {
                    int adds = 0;
                    for (Entity nearby: this.entity.getNearbyEntities(32.0, 32.0, 32.0)) {
                        if (nearby instanceof Guardian) adds += 1;
                    }
                    for (int i = adds; i < 8; i += 1) {
                        this.entity.getWorld().spawn(this.entity.getLocation(), Guardian.class);
                    }
                }
            }
            break;
        }
        case 2: {
            WitherSkeleton wither = (WitherSkeleton)this.entity;
            if (wither.getVehicle() == null) {
                MagmaCube cube = wither.getWorld().spawn(wither.getLocation(), MagmaCube.class, (c) -> {
                        c.setSize(3);
                    });
                cube.addPassenger(wither);
            }
            if ((this.persistence.ticksLived % 20) == 0 && random.nextInt(3) == 0) {
                this.entity.getWorld().spawnParticle(Particle.LAVA, this.entity.getEyeLocation(), 16, 0.5, 0.5, 0.5, 0.0);
                for (Entity nearby: wither.getNearbyEntities(64.0, 64.0, 64.0)) {
                    if (nearby instanceof Player) {
                        Player nearbyPlayer = (Player)nearby;
                        int cursed = 0;
                        ItemStack[] equipment = {nearbyPlayer.getEquipment().getHelmet(),
                                                 nearbyPlayer.getEquipment().getChestplate(),
                                                 nearbyPlayer.getEquipment().getLeggings(),
                                                 nearbyPlayer.getEquipment().getBoots()};
                        for (ItemStack item: equipment) {
                            if (item != null && item.getType() != Material.AIR
                                && (item.getEnchantmentLevel(Enchantment.BINDING_CURSE) > 0
                                    || item.getEnchantmentLevel(Enchantment.VANISHING_CURSE) > 0)) {
                                cursed += 1;
                            }
                        }
                        if (cursed == 0 || random.nextInt(4) >= cursed) {
                            int level = 1;
                            PotionEffect effect = nearbyPlayer.getPotionEffect(PotionEffectType.WITHER);
                            if (effect != null) level = effect.getAmplifier() + 1;
                            nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20 * 30, level), true);
                            nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 30, 0), true);
                            nearbyPlayer.playSound(nearbyPlayer.getEyeLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 0.5f, 0.5f);
                        } else {
                            nearbyPlayer.playSound(nearbyPlayer.getEyeLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 0.1f, 2.0f);
                        }
                    }
                }
                if ((this.persistence.ticksLived % 20) == 0 && random.nextInt(4) == 0) {
                    int adds = 0;
                    for (Entity nearby: this.entity.getNearbyEntities(32.0, 32.0, 32.0)) {
                        if (nearby instanceof Phantom) adds += 1;
                    }
                    for (int i = adds; i < 8; i += 1) {
                        this.entity.getWorld().spawn(this.entity.getLocation(), Phantom.class);
                    }
                }
            }
            break;
        }
        default: break;
        }
    }

    void onEntityCombust(EntityCombustEvent event) {
        event.setCancelled(true);
    }

    void onEntityDamage(EntityDamageEvent event) {
        event.setCancelled(true);
        switch (event.getCause()) {
        case CONTACT:
        case CRAMMING:
        case DROWNING:
        case LAVA:
        case VOID:
        case SUFFOCATION:
            this.entity.teleport(getSpawnLocation());
        default: break;
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player)damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile)damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player)projectile.getShooter();
            }
        }
        return null;
    }

    void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = this.getPlayerDamager(event.getDamager());
        if (damager == null) {
            event.setCancelled(true);
            return;
        }
        boolean melee = damager.equals(event.getDamager());
        switch (this.config.index) {
        case 0: {
            if (melee) {
                ItemStack item = damager.getInventory().getItemInMainHand();
                int level = item.getEnchantmentLevel(Enchantment.DAMAGE_UNDEAD);
                if (level == 0) {
                    event.setCancelled(true);
                } else {
                    this.entity.setFireTicks(100);
                    this.entity.getWorld().playSound(this.entity.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.7f);
                }
            } else {
                event.setCancelled(true);
            }
            break;
        }
        case 1: {
            if (event.getDamager() instanceof Trident) {
                if (this.shield) {
                    this.shield = false;
                    this.shieldCooldown = 100;
                    this.entity.getWorld().spawnParticle(Particle.END_ROD, this.entity.getLocation(), 64, 1.0, 1.0, 1.0, 1.0);
                    this.entity.getWorld().playSound(this.entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_DEATH_LAND, 2.0f, 0.5f);
                } else {
                    event.setCancelled(true);
                }
            } else if (this.shield) {
                event.setCancelled(true);
            }
            break;
        }
        case 2: {
            if (damager.hasPotionEffect(PotionEffectType.WITHER)) {
                event.setCancelled(true);
            }
            break;
        }
        default: break;
        }
        if (!event.isCancelled()) {
            this.persistence.health = this.entity.getHealth();
            this.persistence.damagers.add(damager.getUniqueId());
            this.entity.setNoDamageTicks(0);
        }
    }

    void onEntityDeath(EntityDeathEvent event) {
        ItemStack reward = this.plugin.getConfig().getItemStack("reward" + (this.config.index + 1));
        if (reward != null) {
            this.entity.getWorld().dropItem(this.entity.getLocation(), reward).setInvulnerable(true);
            this.plugin.getLogger().info("Dropped reward: " + reward.getItemMeta().getDisplayName());
        }
        for (Player winner: getWorld().getPlayers()) {
            if (!this.persistence.damagers.contains(winner.getUniqueId())) continue;
            String name = winner.getName();
            this.plugin.getLogger().info(name + " killed " + ChatColor.stripColor(this.entity.getCustomName()));
            for (Player online: Bukkit.getServer().getOnlinePlayers()) {
                online.sendMessage(name + " killed " + this.entity.getCustomName());
            }
            if (this.config.index == 2) {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "title:titles unlockset " + name + " Spoopy");
            }
        }
        this.persistence.health = -1.0;
        this.persistence.respawnCooldown = System.currentTimeMillis() + 1000 * 60 * 60;
        this.persistence.damagers.clear();
        savePersistence();
        Firework firework = this.entity.getWorld().spawn(this.entity.getLocation(), Firework.class, (fw) -> {
                FireworkMeta meta = fw.getFireworkMeta();
                Vector cv = new Vector(Math.random(), Math.random(), Math.random()).normalize();
                meta.addEffect(FireworkEffect.builder()
                               .with(FireworkEffect.Type.BALL_LARGE)
                               .withColor(Color.fromRGB((int)(cv.getX() * 255.0),
                                                        (int)(cv.getY() * 255.0),
                                                        (int)(cv.getZ() * 255.0)))
                               .build());
                fw.setFireworkMeta(meta);
            });
        firework.detonate();
    }
}
