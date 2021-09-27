package com.cavetale.halloween;

import com.cavetale.money.Money;
import com.cavetale.mytems.Mytems;
import com.cavetale.worldmarker.item.ItemMarker;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        case "shop":
            if (args.length == 1) return onShop(player);
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
            player.sendMessage(Component.text("You don't have this mask in your inventory.",
                                              NamedTextColor.DARK_RED));
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
                player.sendMessage(Component.text("Finally, my collection is complete!!!",
                                                  NamedTextColor.GOLD));
                plugin.playCompleteSound(player);
                plugin.playTalking(player, player.getLocation(), 8);
            } else {
                player.sendMessage(Component.text("Thank you.", NamedTextColor.GOLD));
                plugin.playJingle(player);
                plugin.playTalking(player, player.getLocation(), 2);
            }
            Money.give(player.getUniqueId(), 500.0, plugin, mask.name + " mask sold to The Collector");
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
                player.sendMessage(Component.text("I hear there are more masks hidden in dungeons of the Mining World.",
                                                  NamedTextColor.LIGHT_PURPLE));
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
            player.sendMessage(Component.text("Rumor has it one can find the most awesome"
                                              + " Halloween masks in dungeons of the Mining World.",
                                              NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("You can find special dungeons by using your compass.",
                                              NamedTextColor.LIGHT_PURPLE));
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
        List<NamedTextColor> colors = Arrays.asList(new NamedTextColor[] {
                NamedTextColor.AQUA, NamedTextColor.BLUE,
                NamedTextColor.GOLD, NamedTextColor.GREEN,
                NamedTextColor.LIGHT_PURPLE, NamedTextColor.RED,
                NamedTextColor.YELLOW,
            });
        Collections.shuffle(colors, new Random(0));
        TextComponent.Builder cb = Component.text().content("Masks").decorate(TextDecoration.ITALIC);
        int colorI = 0;
        for (Masks.Mask mask: plugin.masks.maskConfig.masks) {
            boolean collected = plugin.persistence.getPlayerData(player).didShow(mask.id, "Collector");
            cb.append(Component.space());
            NamedTextColor maskColor = colors.get(colorI++ % colors.size());
            if (collected) {
                cb.append(Component.text(mask.name, maskColor, TextDecoration.BOLD));
                Component tooltip = Component.join(JoinConfiguration.separator(Component.newline()), new ComponentLike[] {
                        Component.text(mask.name, maskColor, TextDecoration.BOLD),
                        Component.text("You gave me this mask.", NamedTextColor.GRAY, TextDecoration.ITALIC),
                        Component.text("Thank you!", NamedTextColor.GRAY, TextDecoration.ITALIC),
                    });
                cb.hoverEvent(HoverEvent.showText(tooltip));
            } else {
                cb.append(Component.text("[" + mask.name + "]", NamedTextColor.GRAY));
                cb.clickEvent(ClickEvent.runCommand("/maskcollector collector " + mask.id));
                Component tooltip = Component.join(JoinConfiguration.separator(Component.newline()), new Component[] {
                        Component.text(mask.name, maskColor),
                        Component.text("Give this mask to me. I will take good care of it, heehee!",
                                       NamedTextColor.GRAY, TextDecoration.ITALIC),
                        Component.text("WARNING", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(Component.text(" One such mask will be removed from your inventory!", NamedTextColor.RED)),
                    });
                cb.hoverEvent(HoverEvent.showText(tooltip));
            }
        }
        player.sendMessage(cb);
        cb = Component.text();
        cb.append(Component.text("Once completed: ", NamedTextColor.WHITE, TextDecoration.ITALIC));
        if (plugin.persistence.getPlayerData(player).collectedAll) {
            cb.append(Component.text("[Open Reward Shop]", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            cb.append(Component.text("[Open Reward Shop]", NamedTextColor.GRAY));
        }
        cb.clickEvent(ClickEvent.runCommand("/maskcollector shop"));
        cb.hoverEvent(HoverEvent.showText(Component.text("Reward Shop", NamedTextColor.GRAY)));
        player.sendMessage(cb);
    }

    boolean onShop(Player player) {
        Persistence.PlayerData data = plugin.persistence.getPlayerData(player);
        if (!data.collectedAll) {
            player.sendMessage(NamedTextColor.RED + "You haven't handed in all the masks yet!");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 0.5f);
            return true;
        }
        openShop(player);
        return true;
    }

    void openShop(Player player) {
        Gui gui = new Gui(plugin);
        gui.size(9);
        gui.title(Component.text("Choose an Item"));
        List<Mytems> itemList = Arrays.asList(Mytems.DR_ACULA_STAFF,
                                              Mytems.FLAME_SHIELD,
                                              Mytems.STOMPERS,
                                              Mytems.GHAST_BOW,
                                              Mytems.BAT_MASK);
        int slot = 0;
        for (int i = 0; i < itemList.size(); i += 1) {
            Mytems mytems = itemList.get(i);
            ItemStack item = mytems.createItemStack(player);
            gui.setItem(slot, item, click -> {
                    if (click.isShiftClick()) {
                        player.closeInventory();
                        return unlockItem(player, mytems, click);
                    } else {
                        player.sendMessage(Component.text("Shift click to choose!", NamedTextColor.RED));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 0.5f);
                        return false;
                    }
                });
            slot += 2;
        }
        gui.open(player);
    }

    private boolean unlockItem(Player player, Mytems mytems, InventoryClickEvent event) {
        Persistence.PlayerData data = plugin.persistence.getPlayerData(player);
        if (!data.collectedAll) return false;
        data.collectedAll = false;
        data.resetShown("Collector");
        plugin.persistence.save();
        ItemStack item = mytems.createItemStack(player);
        for (ItemStack drop : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getEyeLocation(), drop).setPickupDelay(0);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 2.0f);
        return true;
    }
}
