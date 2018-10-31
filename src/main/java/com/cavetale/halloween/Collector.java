package com.cavetale.halloween;

import com.cavetale.itemmarker.ItemMarker;
import com.cavetale.npc.NPC;
import com.winthier.generic_events.GenericEvents;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor @Getter
final class Collector implements CommandExecutor {
    private final HalloweenPlugin plugin;
    private String halloweenWorld;
    private Block halloweenPortal;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player)sender;
        if (args.length == 0) return true;
        NPC npc = this.plugin.spawnDecoration.getCollector();
        if (npc == null) return true;
        if (npc.getLocation().distanceSquared(player.getLocation()) > 64.0) return true;
        if (args.length == 1) {
            Masks.Mask mask = this.plugin.masks.getMask(args[0]);
            if (mask == null) return true;
            Persistence.PlayerData data = this.plugin.persistence.getPlayerData(player);
            if (!data.didShow(mask.id, "Collector")) {
                if (!takeMask(player, mask)) {
                    this.plugin.playFailSound(player, npc.getEyeLocation());
                    player.sendMessage(ChatColor.DARK_RED + "You don't have this mask in your inventory.");
                    showMaskList(player);
                    return true;
                } else {
                    data.setShown(mask.id, "Collector");
                    this.plugin.getLogger().info(player.getName() + " gave " + mask.id + " to Collector");
                    boolean complete = true;
                    for (String maskid: this.plugin.masks.ids()) {
                        if (!data.didShow(maskid, "Collector")) {
                            complete = false;
                            break;
                        }
                    }
                    if (complete) {
                        this.plugin.getLogger().info(player.getName() + " finished mask collection.");
                        data.collectedAll = complete;
                        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "title:titles unlockset " + player.getName() + " MaskCollector");
                    }
                    this.plugin.persistence.save();
                    if (complete) {
                        player.sendMessage(ChatColor.GOLD + "Finally, my collection is complete!!!");
                        this.plugin.playCompleteSound(player);
                        this.plugin.playTalking(player, npc.getEyeLocation(), 8);
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Thank you.");
                        GenericEvents.givePlayerMoney(player.getUniqueId(), 500.0, this.plugin, mask.name + " mask sold to mysterious Collector");
                        this.plugin.playJingle(player);
                        this.plugin.playTalking(player, npc.getEyeLocation(), 2);
                    }
                    this.plugin.playEffect(player, npc.getHeadLocation());
                    showMaskList(player);
                    return true;
                }
            }
        }
        return true;
    }

    boolean takeMask(Player player, Masks.Mask mask) {
        UUID owner = player.getUniqueId();
        for (ItemStack item: player.getInventory()) {
            if (item == null || item.getType() != Material.PLAYER_HEAD) continue;
            if (ItemMarker.hasCustomId(item, Masks.MASK_ITEM_ID)
                && mask.id.equals(ItemMarker.getMarker(item, Masks.MASK_ID))
                && ItemMarker.isOwner(item, owner)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    void importConfig() {
        this.halloweenWorld = this.plugin.getConfig().getString("HalloweenWorld");
        List<Object> list = (List<Object>)this.plugin.getConfig().getList("HalloweenPortal");
        this.halloweenPortal = Bukkit.getWorld((String)list.get(0)).getBlockAt((Integer)list.get(1), (Integer)list.get(2), (Integer)list.get(3));
    }

    void showMaskList(Player player) {
        List<ChatColor> colors = Arrays.asList(ChatColor.AQUA, ChatColor.BLUE, ChatColor.GOLD, ChatColor.GREEN, ChatColor.LIGHT_PURPLE, ChatColor.RED, ChatColor.YELLOW);
        Collections.shuffle(colors, new Random(0));
        ComponentBuilder cb = new ComponentBuilder("Masks").italic(true);
        int colorI = 0;
        for (Masks.Mask mask: this.plugin.masks.maskConfig.masks) {
            boolean collected = this.plugin.persistence.getPlayerData(player).didShow(mask.id, "Collector");
            cb.append(" ") .reset();
            ChatColor maskColor = colors.get(colorI++ % colors.size());
            if (collected) {
                cb
                    .append(mask.name)
                    .color(maskColor)
                    .bold(true)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("" + maskColor + ChatColor.BOLD + mask.name + "\n" + ChatColor.RESET + ChatColor.GRAY + ChatColor.ITALIC + "You gave me this map.\nThank you!")));
            } else {
                cb
                    .append("[" + mask.name + "]")
                    .color(ChatColor.GRAY)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/maskcollector " + mask.id))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(maskColor + mask.name + "\n" + ChatColor.GRAY + ChatColor.ITALIC + "Give this map to me.\nI will take good care of it, " + ChatColor.GRAY + ChatColor.ITALIC + "heehee!" + "\n" + ChatColor.DARK_RED + ChatColor.BOLD + "WARNING" + ChatColor.RESET + ChatColor.RED + " One such mask will beremoved\nfrom your inventory!")));
            }
        }
        player.spigot().sendMessage(cb.create());
    }
}
