package com.cavetale.halloween;

import java.util.Arrays;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class HalloweenPlugin extends JavaPlugin {
    SpawnDecoration spawnDecoration;
    Masks masks;
    Persistence persistence;
    HalloweenListener halloweenListener;

    @Override
    public void onEnable() {
        this.persistence = new Persistence(this);
        this.persistence.load();
        this.spawnDecoration = new SpawnDecoration(this);
        this.spawnDecoration.enable();
        this.masks = new Masks(this);
        this.masks.enable();
        this.halloweenListener = new HalloweenListener(this);
        getServer().getPluginManager().registerEvents(this.halloweenListener, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        spawnDecoration.disable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "mask": {
            if (args.length == 1) {
                sender.sendMessage(this.masks.size() + " masks");
                sender.sendMessage("" + this.masks.ids());
            } else if (args.length == 2) {
                if (args[1].equals("all")) {
                    for (String i: masks.ids()) {
                        player.getInventory().addItem(this.masks.spawnMask(i, player));
                    }
                } else {
                    player.getInventory().addItem(this.masks.spawnMask(args[1], player));
                }
            }
            return true;
        }
        case "treater": {
            if (args.length != 2) return false;
            SpawnDecoration.Treater treater = new SpawnDecoration.Treater();
            treater.name = args[1];
            treater.id = UUID.randomUUID().toString().replace("-", "");
            treater.location = Arrays.asList(player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
            spawnDecoration.treaters.add(treater);
            spawnDecoration.saveTreaters();
            player.sendMessage("Treater added: " + treater.name);
            return true;
        }
        case "candy": {
            this.masks.spawnCandy(player.getEyeLocation());
            playJingle(player);
            return true;
        }
        default: return false;
        }
    }

    void tick() {
        spawnDecoration.tick();
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

    void playEffect(final Player player, final Location location) {
        player.spawnParticle(Particle.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0.0);
    }
}
