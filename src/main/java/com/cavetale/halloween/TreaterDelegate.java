package com.cavetale.halloween;

import com.cavetale.itemmarker.ItemMarker;
import com.cavetale.npc.NPC;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
final class TreaterDelegate implements NPC.Delegate {
    final HalloweenPlugin plugin;
    final SpawnDecoration.Treater treater;
    UUID busyWith = null;
    int busySince = 0;
    int ticksLived = 0;
    List<List<String>> dialogueLines;

    @Override
    public void onTick(NPC npc) {
        ticksLived += 1;
        if ((ticksLived % 5 == 0) && treater.name.equals("Collector")) {
            for (NPC.Watcher watcher: npc.getWatchers().values()) {
                Persistence.PlayerData playerData = plugin.persistence.getPlayerData(watcher.player);
                if (playerData.collectedAll) {
                    Block portalBlock = this.plugin.collector.getHalloweenPortal();
                    if (watcher.player.getLocation().getBlock().equals(portalBlock)) {
                        watcher.player.teleport(Bukkit.getWorld(this.plugin.collector.getHalloweenWorld()).getSpawnLocation());
                    } else {
                        Location loc = portalBlock.getLocation().add(0.25 + Math.random() * 0.5, 0.5 + Math.random() * 1.5, 0.25 + Math.random() * 0.5);
                        this.plugin.showPortalParticle(watcher.player, loc);
                    }
                }
            }
        }
        if (this.busyWith != null) {
            Player player = Bukkit.getServer().getPlayer(busyWith);
            if (player == null
                || !player.getWorld().equals(npc.getLocation().getWorld())
                || player.getLocation().distanceSquared(npc.getLocation()) > 256.0) {
                this.busyWith = null;
                return;
            }
            Persistence.PlayerData playerData = plugin.persistence.getPlayerData(player);
            if (this.busySince == 0 && this.treater.name.equals("Collector")) {
                if (playerData.collectedAll) {
                    this.dialogueLines = new ArrayList<>();
                    this.dialogueLines.add(Arrays.asList("Enter through the portal, mortal!",
                                                         "" + ChatColor.GRAY + ChatColor.ITALIC + "heehee!"));
                } else {
                    this.dialogueLines = new ArrayList<>();
                    this.dialogueLines.add(Arrays.asList("Greetings!",
                                                         "Have you started your own",
                                                         " mask collection yet?"));
                    this.dialogueLines.add(Arrays.asList("I will take one of",
                                                         "each off your hand."));
                    this.dialogueLines.add(Arrays.asList("You won't regret it, " + ChatColor.ITALIC + "heehee!"));
                }
            } else if (this.busySince == 0) {
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && ItemMarker.hasCustomId(helmet, Masks.MASK_ITEM_ID) && ItemMarker.isOwner(helmet, busyWith)) {
                    String maskName = (String)ItemMarker.getMarker(helmet, Masks.MASK_ID);
                    if (maskName == null) maskName = "INVALID";
                    if (playerData.didShow(maskName, treater.name)) {
                        dialogueLines = new ArrayList<>();
                        dialogueLines.add(Arrays.asList("I hear there are more",
                                                        "masks hidden in dungeons",
                                                        "of the Mining World."));
                    } else {
                        playerData.setShown(maskName, treater.name);
                        plugin.persistence.save();
                        dialogueLines = new ArrayList<>();
                        dialogueLines.add(Arrays.asList("Whoa, what a cool mask!",
                                                        "Where did you find that?",
                                                        "Here, have some candy."));
                        for (int i = 0; i < 5; i += 1) {
                            plugin.masks.spawnCandy(npc.getHeadLocation());
                        }
                        plugin.playJingle(player);
                        plugin.playEffect(player, npc.getHeadLocation());
                    }
                } else {
                    dialogueLines = new ArrayList<>();
                    dialogueLines.add(Arrays.asList("Rumor has it one can find",
                                                    "the most awesome Halloween",
                                                    "masks in dungeons of the",
                                                    "Mining World."));
                    dialogueLines.add(Arrays.asList("You can find special",
                                                    "dungeons by using your",
                                                    "compass."));
                }
            } else if (npc.getSpeechBubble() == null || !npc.getSpeechBubble().isValid()) {
                if (dialogueLines == null || dialogueLines.isEmpty()) {
                    busyWith = null;
                    busySince = 0;
                    dialogueLines = null;
                    if (this.treater.name.equals("Collector") && !playerData.collectedAll) {
                        this.plugin.collector.showMaskList(player);
                    }
                } else {
                    List<String> lines = dialogueLines.remove(0);
                    StringBuilder sb = new StringBuilder();
                    for (String line: lines) {
                        NPC bubble = npc.addSpeechBubble(line);
                        bubble.getExclusive().add(player.getUniqueId());
                        bubble.setLifespan(lines.size() * 30);
                        sb.append(" ").append(line);
                    }
                    npc.lookAt(player.getEyeLocation());
                    ChatColor nameColor = !this.treater.name.equals("Collector") ? ChatColor.GOLD : ChatColor.DARK_RED;
                    ChatColor chatColor = !this.treater.name.equals("Collector") ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY;
                    player.sendMessage("<" + nameColor + treater.name + ChatColor.WHITE + ">" + chatColor + sb.toString());
                    final Location soundLoc = npc.getEyeLocation();
                    new BukkitRunnable() {
                        int count = lines.size() * 3;
                        @Override public void run() {
                            if (count <= 0 || !player.isValid()) return;
                            player.playSound(npc.getEyeLocation(), org.bukkit.Sound.ENTITY_VILLAGER_TRADE, 1.0f, 2.0f);
                            count -= 1;
                        }
                    }.runTaskTimer(this.plugin, 5L, 5L);
                }
            }
            busySince += 1;
        } else {
            // IDLE
            if ((ticksLived % 20) == 0) {
                if (ThreadLocalRandom.current().nextInt(2) == 0) {
                    npc.lookRandom();
                }
                if (ThreadLocalRandom.current().nextInt(5) == 0) {
                    npc.swingArm(ThreadLocalRandom.current().nextBoolean());
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        npc.setHeadYaw(npc.getHeadYaw() + 25.0);
                    } else {
                        npc.setHeadYaw(npc.getHeadYaw() - 25.0);
                    }
                    npc.getLocation().setYaw((float)npc.getHeadYaw());
                }
            }
        }
    }

    @Override
    public boolean onInteract(NPC npc, Player player, boolean right) {
        if (this.busyWith != null) return true;
        this.busyWith = player.getUniqueId();
        this.busySince = 0;
        return true;
    }
}
