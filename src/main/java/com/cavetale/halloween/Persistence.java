package com.cavetale.halloween;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class Persistence {
    private final HalloweenPlugin plugin;
    private Content content = new Content();

    @Data
    static class Content {
        Map<UUID, PlayerData> players = new HashMap<>();
    }

    @Data
    static class PlayerData {
        String name;
        int lastX = Integer.MAX_VALUE, lastZ = Integer.MAX_VALUE;
        boolean collectedAll;
        Set<List<String>> shown = new HashSet<>();

        boolean didShow(String maskId, String npcName) {
            return shown.contains(Arrays.asList(maskId, npcName));
        }

        void setShown(String maskId, String npcName) {
            shown.add(Arrays.asList(maskId, npcName));
        }

        String rollMaskDrop(HalloweenPlugin pl) {
            String mask = null;
            long count = 0;
            List<String> ids = pl.masks.ids();
            Collections.shuffle(ids);
            if (this.collectedAll) {
                return ids.get(0);
            } else {
                for (String maskId: ids) {
                    long maskIdCount = shown.stream().filter(s -> s.get(0).equals(maskId)).count();
                    if (mask == null || count > maskIdCount) {
                        mask = maskId;
                        count = maskIdCount;
                    }
                }
                return mask;
            }
        }
    }

    void load() {
        try (FileReader reader = new FileReader(new File(this.plugin.getDataFolder(), "persistence.json"))) {
            this.content = new Gson().fromJson(reader, Content.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            this.content = new Content();
        }
    }

    void save() {
        try (FileWriter writer = new FileWriter(new File(this.plugin.getDataFolder(), "persistence.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this.content, writer);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    PlayerData getPlayerData(Player player) {
        PlayerData result = this.content.players.get(player.getUniqueId());
        if (result == null) {
            result = new PlayerData();
            result.name = player.getName();
            this.content.players.put(player.getUniqueId(), result);
        }
        return result;
    }
}
