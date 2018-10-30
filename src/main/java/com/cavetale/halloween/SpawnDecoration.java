package com.cavetale.halloween;

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
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
final class SpawnDecoration {
    private final HalloweenPlugin plugin;
    private ArrayList<Floater> floaters = new ArrayList<>();
    List<Treater> treaters = new ArrayList<>();
    private final String worldName = "spawn";
    int treaterIndex = 0;

    @RequiredArgsConstructor
    static class Floater {
        final NPC npc;
        final int x, y, z;
    }

    static class Treater {
        String name, id;
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
            String texture, signature;
            int index = this.treaterIndex++;
            if (treater.name.equals("Collector")) {
                texture = this.plugin.getConfig().getDefaultSection().getString("Collector.texture");
                signature = this.plugin.getConfig().getDefaultSection().getString("Collector.signature");
            } else {
                List<String> keys = new ArrayList<>(this.plugin.getConfig().getDefaultSection().getConfigurationSection("Treater").getKeys(false));
                String key = keys.get(index % keys.size());
                texture = this.plugin.getConfig().getDefaultSection().getString("Treater." + key + ".texture");
                signature = this.plugin.getConfig().getDefaultSection().getString("Treater." + key + ".signature");
            }
            NPC npc = new NPC(NPCPlugin.getInstance(), NPC.Type.PLAYER, location, "%" + treater.name, new PlayerSkin(treater.id, treater.name, texture, signature));
            npc.setHeadYaw((double)location.getYaw());
            List<String> maskIds = this.plugin.masks.ids();
            ItemStack mask = this.plugin.masks.spawnMask(maskIds.get(index % maskIds.size()), null);
            npc.setEquipment(EquipmentSlot.HEAD, mask);
            npc.setRemoveWhenUnwatched(false);
            NPCPlugin.getInstance().enableNPC(npc);
            treater.npc = npc;
            npc.setDelegate(new TreaterDelegate(plugin, treater));
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
            this.treaters = new Gson().fromJson(reader, new TypeToken<List<Treater>>() { }.getType());
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
