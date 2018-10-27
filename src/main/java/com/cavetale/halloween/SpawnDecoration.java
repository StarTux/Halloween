package com.cavetale.halloween;

import com.cavetale.itemmarker.ItemMarker;
import com.cavetale.npc.NPC;
import com.cavetale.npc.NPCPlugin;
import com.cavetale.npc.PlayerSkin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
final class SpawnDecoration {
    private final HalloweenPlugin plugin;
    private ArrayList<Floater> floaters = new ArrayList<>();
    List<Treater> treaters = new ArrayList<>();
    private final String worldName = "spawn";

    @RequiredArgsConstructor
    static class Floater {
        final NPC npc;
        final int x, y, z;
    }

    static class Treater {
        String name, id, texture, signature;
        List<Object> location;
        transient NPC npc;
    }

    void tick() {
        for (Iterator<Floater> iter = this.floaters.iterator(); iter.hasNext();) {
            if (!iter.next().npc.isValid()) iter.remove();
        }
        if (floaters.size() < 10) spawnFloater();
        for (Treater treater: this.treaters) {
            if (treater.npc == null || !treater.npc.isValid()) spawnTreater(treater);
        }
    }

    void spawnTreater(final Treater treater) {
        if (treater.location == null) return;
        double x = ((Number)treater.location.get(1)).doubleValue();
        double y = ((Number)treater.location.get(2)).doubleValue();
        double z = ((Number)treater.location.get(3)).doubleValue();
        World world = Bukkit.getWorld((String)treater.location.get(0));
        int ix = (int)Math.floor(x);
        int iz = (int)Math.floor(z);
        if (world != null && world.isChunkLoaded(ix >> 4, iz >> 4)) {
            Location location = new Location(world, x, y, z, (float)Math.random() * 360.0f, 0.0f);
            NPC npc = new NPC(NPCPlugin.getInstance(), NPC.Type.PLAYER, location, "%" + treater.name, new PlayerSkin(treater.id, treater.name, treater.texture, treater.signature));
            npc.setHeadYaw((double)location.getYaw());
            npc.setRemoveWhenUnwatched(false);
            NPCPlugin.getInstance().enableNPC(npc);
            treater.npc = npc;
            npc.setDelegate(new NPC.Delegate() {
                    UUID busyWith = null;
                    int busySince = 0;
                    int ticksLived = 0;
                    List<String> dialogueLines;
                    @Override public void onTick(NPC n) {
                        if (busyWith != null) {
                            Player player = Bukkit.getServer().getPlayer(busyWith);
                            if (player == null
                                || !player.getWorld().equals(n.getLocation().getWorld())
                                || player.getLocation().distanceSquared(n.getLocation()) > 256.0) {
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
                                            plugin.masks.spawnCandy(n.getHeadLocation());
                                        }
                                        plugin.playJingle(player);
                                        plugin.playEffect(player, n.getHeadLocation());
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
                            } else if (n.getSpeechBubble() == null || !n.getSpeechBubble().isValid()) {
                                if (dialogueLines == null || dialogueLines.isEmpty()) {
                                    busyWith = null;
                                    dialogueLines = null;
                                } else {
                                    String line = dialogueLines.remove(0);
                                    n.addSpeechBubble(line);
                                    n.lookAt(player.getEyeLocation());
                                    player.sendMessage("<" + ChatColor.GOLD + treater.name + ChatColor.WHITE + "> " + ChatColor.GRAY + line);
                                }
                            }
                            busySince += 1;
                        } else {
                            if ((ticksLived++ % 100) == 0) {
                                n.lookRandom();
                            }
                        }
                    }
                    @Override public boolean onInteract(NPC n, Player p, boolean r) {
                        if (busyWith != null) return true;
                        busyWith = p.getUniqueId();
                        this.busySince = 0;
                        return true;
                    }
                });
        }
    }

    void saveTreaters() {
        try (FileWriter writer = new FileWriter(new File(this.plugin.getDataFolder(), "treaters.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this.treaters, writer);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void enable() {
        this.plugin.saveResource("treaters.json", false);
        try (FileReader reader = new FileReader(new File(this.plugin.getDataFolder(), "treaters.json"))) {
            this.treaters = new Gson().fromJson(reader, new TypeToken<List<Treater>>() {}.getType());
            this.plugin.getLogger().info("" + this.treaters.size() + " treaters loaded.");
        } catch (IOException ioe) {
            ioe.printStackTrace();
            this.treaters = new ArrayList<>();
        }
    }

    void disable() {
        for (Floater floater: this.floaters) {
            floater.npc.setValid(false);
        }
        this.floaters.clear();
        for (Treater treater: this.treaters) {
            if (treater.npc != null) treater.npc.setValid(false);
        }
        this.treaters.clear();
    }

    void spawnFloater() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || world.getPlayers().isEmpty()) return;
        List<Chunk> chunks = Arrays.asList(world.getLoadedChunks());
        if (chunks.isEmpty()) return;
        Chunk chunk = chunks.get(ThreadLocalRandom.current().nextInt(chunks.size()));
        int x = (chunk.getX() << 4) + ThreadLocalRandom.current().nextInt(16);
        int z = (chunk.getZ() << 4) + ThreadLocalRandom.current().nextInt(16);
        for (Floater floater: this.floaters) {
            int dist = Math.max(Math.abs(x - floater.x), Math.abs(z - floater.z));
            if (dist < 16) return;
        }
        Block block = world.getHighestBlockAt(x, z).getRelative(0, 8, 0);
        Location location = block.getLocation().add(0.5, 0.0, 0.5);
        NPC npc = new NPC(NPCPlugin.getInstance(), NPC.Type.MOB, location, EntityType.GHAST);
        Floater floater = new Floater(npc, x, block.getY(), z);
        floaters.add(floater);
        NPCPlugin.getInstance().enableNPC(npc);
        npc.setDelegate((nnpc) -> tickFloater(nnpc, floater));
    }

    void tickFloater(NPC npc, Floater floater) {
        double time = (double)npc.getTicksLived();
        double headYaw = (time * 5.0) % 360.0;
        npc.setLocation(new Location(npc.getLocation().getWorld(), (double)floater.x + 0.5, (double)floater.y + (Math.sin(time * 0.03) * 0.5 + 0.5) * 16.0, (double)floater.z, (float)headYaw, 0.0f));
        npc.setHeadYaw(headYaw);
    }
}
