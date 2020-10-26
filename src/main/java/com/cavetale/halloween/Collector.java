package com.cavetale.halloween;

import com.cavetale.worldmarker.ItemMarker;
import com.winthier.generic_events.GenericEvents;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor @Getter
final class Collector implements CommandExecutor {
    private final HalloweenPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        if (args.length == 0) {
            showMaskList(player);
            return true;
        }
        switch (args[0]) {
        case "collector":
            if (args.length == 2) return onCollect(player, args[1]);
            return true;
        case "treater":
            if (args.length == 2) return onTreater(player, args[1]);
            return true;
        default: return true;
        }
    }

    public boolean onCollect(Player player, String maskName) {
        Masks.Mask mask = plugin.masks.getMask(maskName);
        if (mask == null) return true;
        Persistence.PlayerData data = plugin.persistence.getPlayerData(player);
        if (data.didShow(mask.id, "Collector")) return true;
        if (!takeMask(player, mask)) {
            plugin.playFailSound(player, player.getLocation());
            player.sendMessage(ChatColor.DARK_RED + "You don't have this mask in your inventory.");
            showMaskList(player);
            return true;
        } else {
            data.setShown(mask.id, "Collector");
            plugin.getLogger().info(player.getName() + " gave " + mask.id + " to Collector");
            boolean complete = true;
            for (String maskid: plugin.masks.ids()) {
                if (!data.didShow(maskid, "Collector")) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                plugin.getLogger().info(player.getName() + " finished mask collection.");
                data.collectedAll = complete;
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "title:titles unlockset " + player.getName() + " MaskCollector");
            }
            plugin.persistence.save();
            if (complete) {
                player.sendMessage(ChatColor.GOLD + "Finally, my collection is complete!!!");
                plugin.playCompleteSound(player);
                plugin.playTalking(player, player.getLocation(), 8);
            } else {
                player.sendMessage(ChatColor.GOLD + "Thank you.");
                plugin.playJingle(player);
                plugin.playTalking(player, player.getLocation(), 2);
            }
            GenericEvents.givePlayerMoney(player.getUniqueId(), 500.0, plugin, mask.name + " mask sold to The Collector");
            plugin.playEffect(player, player.getEyeLocation());
            showMaskList(player);
            return true;
        }
    }

    public boolean onTreater(Player player, String treaterName) {
        if (!plugin.getConfig().isConfigurationSection("Treater." + treaterName)) return true;
        Persistence.PlayerData playerData = plugin.persistence.getPlayerData(player);
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && ItemMarker.hasId(helmet, Masks.MASK_ITEM_ID) && plugin.masks.isOwner(player, helmet)) {
            String maskName = plugin.masks.getMask(helmet);
            if (maskName == null) return true;
            if (playerData.didShow(maskName, treaterName)) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "I hear there are more masks hidden in dungeons of the Mining World.");
            } else {
                playerData.setShown(maskName, treaterName);
                plugin.persistence.save();
                player.sendMessage("Whoa, what a cool mask! Where did you find that? Here, have some candy.");
                for (int i = 0; i < 5; i += 1) {
                    plugin.masks.spawnCandy(player.getEyeLocation());
                }
                plugin.playJingle(player);
                plugin.playEffect(player, player.getEyeLocation());
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Rumor has it one can find the most awesome Halloween masks in dungeons of the Mining World.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "You can find special dungeons by using your compass.");
        }
        return true;
    }

    boolean takeMask(Player player, Masks.Mask mask) {
        for (ItemStack item: player.getInventory()) {
            if (item == null || item.getType() != Material.PLAYER_HEAD) continue;
            if (ItemMarker.hasId(item, Masks.MASK_ITEM_ID) && plugin.masks.isMask(mask, item) && plugin.masks.isOwner(player, item)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    void showMaskList(Player player) {
        List<ChatColor> colors = Arrays.asList(ChatColor.AQUA, ChatColor.BLUE, ChatColor.GOLD, ChatColor.GREEN,
                                               ChatColor.LIGHT_PURPLE, ChatColor.RED, ChatColor.YELLOW);
        Collections.shuffle(colors, new Random(0));
        ComponentBuilder cb = new ComponentBuilder("Masks").italic(true);
        int colorI = 0;
        for (Masks.Mask mask: plugin.masks.maskConfig.masks) {
            boolean collected = plugin.persistence.getPlayerData(player).didShow(mask.id, "Collector");
            cb.append(" ") .reset();
            ChatColor maskColor = colors.get(colorI++ % colors.size());
            if (collected) {
                cb.append(mask.name).color(maskColor).bold(true);
                String tooltip = "" + maskColor + ChatColor.BOLD + mask.name
                    + "\n" + ChatColor.GRAY + ChatColor.ITALIC + "You gave me this mask."
                    + "\n" + ChatColor.GRAY + ChatColor.ITALIC + "Thank you!";
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip)));
            } else {
                cb.append("[" + mask.name + "]").color(ChatColor.GRAY);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/maskcollector collector " + mask.id));
                String tooltip = maskColor + mask.name
                    + "\n" + ChatColor.GRAY + ChatColor.ITALIC + "Give this mask to me. I will take good care of it, heehee!"
                    + "\n" + ChatColor.DARK_RED + ChatColor.BOLD + "WARNING" + ChatColor.RED + " One such mask will be removed from your inventory!";
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip)));
            }
        }
        player.spigot().sendMessage(cb.create());
    }
}
