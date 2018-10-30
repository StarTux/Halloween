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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
final class TreaterDelegate implements NPC.Delegate {
    final HalloweenPlugin plugin;
    final SpawnDecoration.Treater treater;
    UUID busyWith = null;
    int busySince = 0;
    int ticksLived = 0;
    List<String> dialogueLines;

    @Override
    public void onTick(NPC npc) {
        if (busyWith != null) {
            Player player = Bukkit.getServer().getPlayer(busyWith);
            if (player == null
                || !player.getWorld().equals(npc.getLocation().getWorld())
                || player.getLocation().distanceSquared(npc.getLocation()) > 256.0) {
                busyWith = null;
                return;
            }
            Persistence.PlayerData playerData = plugin.persistence.getPlayerData(player);
            if (busySince == 0) {
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && ItemMarker.hasCustomId(helmet, Masks.MASK_ITEM_ID) && ItemMarker.isOwner(helmet, busyWith)) {
                    String maskName = (String)ItemMarker.getMarker(helmet, Masks.MASK_ID);
                    if (maskName == null) maskName = "INVALID";
                    if (playerData.didShow(maskName, treater.name)) {
                        dialogueLines = new ArrayList<>(Arrays.asList("I hear there are more",
                                                                      "masks hidden in dungeons",
                                                                      "of the Mining World."));
                    } else {
                        playerData.setShown(maskName, treater.name);
                        dialogueLines = new ArrayList<>(Arrays.asList("Whoa, what a cool mask!",
                                                                      "Where did you find that?",
                                                                      "Here, have some candy."));
                        for (int i = 0; i < 5; i += 1) {
                            plugin.masks.spawnCandy(npc.getHeadLocation());
                        }
                        plugin.playJingle(player);
                        plugin.playEffect(player, npc.getHeadLocation());
                        if (playerData.getMaskDrop() == null) {
                            playerData.rollMaskDrop(plugin);
                        }
                        plugin.persistence.save();
                    }
                } else {
                    dialogueLines = new ArrayList<>(Arrays.asList("Rumor has it one can find",
                                                                  "the most awesome Halloween",
                                                                  "masks in dungeons of the",
                                                                  "Mining World.",
                                                                  "You can find special",
                                                                  "dungeons by using your",
                                                                  "compass."));
                }
            } else if (npc.getSpeechBubble() == null || !npc.getSpeechBubble().isValid()) {
                if (dialogueLines == null || dialogueLines.isEmpty()) {
                    busyWith = null;
                    dialogueLines = null;
                } else {
                    String line = dialogueLines.remove(0);
                    npc.addSpeechBubble(line);
                    npc.lookAt(player.getEyeLocation());
                    player.sendMessage("<" + ChatColor.GOLD + treater.name + ChatColor.WHITE + "> " + ChatColor.GRAY + line);
                }
            }
            busySince += 1;
        } else {
            if ((ticksLived++ % 20) == 0) {
                if (ThreadLocalRandom.current().nextInt(2) == 0) {
                    npc.lookRandom();
                }
                if (ThreadLocalRandom.current().nextInt(5) == 0) {
                    npc.swingArm(ThreadLocalRandom.current().nextBoolean());
                }
            }
        }
    }

    @Override
    public boolean onInteract(NPC npc, Player player, boolean right) {
        if (busyWith != null) return true;
        busyWith = player.getUniqueId();
        this.busySince = 0;
        return true;
    }
}
