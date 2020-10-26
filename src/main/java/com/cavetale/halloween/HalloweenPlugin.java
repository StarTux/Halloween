package com.cavetale.halloween;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class HalloweenPlugin extends JavaPlugin {
    Masks masks;
    Persistence persistence;
    HalloweenListener halloweenListener;
    Collector collector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        persistence = new Persistence(this);
        persistence.load();
        masks = new Masks(this);
        masks.enable();
        halloweenListener = new HalloweenListener(this);
        getServer().getPluginManager().registerEvents(halloweenListener, this);
        collector = new Collector(this);
        getCommand("maskcollector").setExecutor(collector);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "mask": {
            if (args.length == 1) {
                sender.sendMessage(masks.size() + " masks");
                sender.sendMessage("" + masks.ids());
            } else if (args.length == 2) {
                if (args[1].equals("all")) {
                    for (String i: masks.ids()) {
                        player.getInventory().addItem(masks.spawnMask(i, player));
                    }
                } else {
                    player.getInventory().addItem(masks.spawnMask(args[1], player));
                }
            } else if (args.length == 3) {
                Player target = getServer().getPlayerExact(args[2]);
                player.getInventory().addItem(masks.spawnMask(args[1], target));
            }
            return true;
        }
        case "candy": {
            masks.spawnCandy(player.getEyeLocation());
            playJingle(player);
            return true;
        }
        case "spawn": {
            if (player == null) return false;
            if (args.length != 2) return false;
            String name = args[1];
            ConfigurationSection section = name.equals("Collector")
                ? getConfig().getConfigurationSection("Collector")
                : getConfig().getConfigurationSection("Treater." + name);
            if (section == null) {
                sender.sendMessage("Not found: " + name);
                return true;
            }
            String texture = section.getString("texture");
            String signature = section.getString("signature");
            ItemStack skull = makeSkull(name, texture, signature);
            ArmorStand armorStand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, e -> {
                    e.setPersistent(true);
                    e.setBasePlate(false);
                    e.setArms(true);
                    e.setHelmet(skull);
                    e.setChestplate(makeBlack(Material.LEATHER_CHESTPLATE));
                    e.setLeggings(makeBlack(Material.LEATHER_LEGGINGS));
                    e.setBoots(makeBlack(Material.LEATHER_BOOTS));
                });
            player.sendMessage("Spawned: " + name);
            return true;
        }
        case "reload": {
            reloadConfig();
            sender.sendMessage("config.yml reloaded");
            persistence.load();
            sender.sendMessage("player persistence reloaded");
            masks.enable();
            sender.sendMessage("masks reloaded");
            return true;
        }
        default: return false;
        }
    }

    void playCandyEffect(final Player player) {
        player.playSound(player.getEyeLocation(), Sound.ENTITY_WOLF_SHAKE, SoundCategory.MASTER, 0.5f, 2.0f);
    }

    void playJingle(final Player player) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isValid()) {
                    cancel();
                    return;
                }
                switch (ticks++) {
                case 0: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 0.8f); break;
                case 1: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 1.0f); break;
                case 2: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 1.2f); break;
                case 3: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 1.5f); break;
                default: cancel();
                }
            }
        }.runTaskTimer(this, 4L, 4L);
    }

    void playTalking(final Player player, Location location, int times) {
        new BukkitRunnable() {
            int ticks = times;
            @Override public void run() {
                if (!player.isValid()) {
                    cancel();
                    return;
                }
                player.playSound(location, org.bukkit.Sound.ENTITY_VILLAGER_TRADE, 1.0f, 2.0f);
                if (ticks-- <= 0) cancel();
            }
        }.runTaskTimer(this, 5L, 5L);
    }

    void playEffect(final Player player, final Location location) {
        player.spawnParticle(Particle.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0.0);
    }

    void playFailSound(final Player player, final Location location) {
        player.playSound(location, Sound.ENTITY_GHAST_SCREAM, SoundCategory.MASTER, 0.5f, 1.5f);
    }

    void playCompleteSound(final Player player) {
        player.playSound(player.getEyeLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.5f, 1.5f);
    }

    void showPortalParticle(final Player player, final Location location) {
        player.spawnParticle(Particle.END_ROD, location, 5, 0.25, 0.25, 0.25, 0.0);
    }

    public ItemStack makeSkull(String name, String texture, String signature) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.getServer().createProfile(UUID.randomUUID(), name);
        ProfileProperty prop = new ProfileProperty("textures", texture, signature);
        profile.setProperty(prop);
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeBlack(Material mat) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.BLACK);
        item.setItemMeta(meta);
        return item;
    }
}
