/*
 * This file is a part of project QuickShop, the name is SubCommand_Transfer.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.api.command.CommandHandler;
import org.maxgamer.quickshop.api.shop.Shop;
import org.maxgamer.quickshop.util.PlayerFinder;
import org.maxgamer.quickshop.util.Util;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SubCommand_Transfer implements CommandHandler<Player> {

    private final QuickShop plugin;

    public SubCommand_Transfer(QuickShop plugin) {
        this.plugin = plugin;
    }


    @Override
    public void onCommand(@NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        if (cmdArg.length == 1) {
            final PlayerFinder.PlayerProfile targetPlayer = PlayerFinder.findPlayerProfileByName(cmdArg[0], false, plugin.isIncludeOfflinePlayer());
            if (targetPlayer == null) {
                plugin.text().of(sender, "unknown-player").send();
                return;
            }
            String targetPlayerName = targetPlayer.getName();
            if (targetPlayerName == null) {
                targetPlayerName = "null";
            }
            final UUID targetPlayerUUID = targetPlayer.getUuid();
            List<Shop> shopList = plugin.getShopManager().getPlayerAllShops(sender.getUniqueId());
            if (plugin.isLimit()) {
                final Player player = plugin.getServer().getPlayer(targetPlayerUUID);
                if (player == null) {
                    plugin.text().of(sender, "unknown-player").send();
                    return;
                }
                if (!checkAndSendLimitMessage(player, sender, shopList.size())) {
                    return;
                }
            }
            for (Shop shop : shopList) {
                if (!shop.isBuying()) {
                    shop.setOwner(targetPlayerUUID);
                }
            }
            plugin.text().of(sender, "command.transfer-success", Integer.toString(shopList.size()), targetPlayerName).send();
        } else if (cmdArg.length == 2) {
            if (!QuickShop.getPermissionManager().hasPermission(sender, "quickshop.transfer.other")) {
                plugin.text().of(sender, "no-permission").send();
                return;
            }

            final PlayerFinder.PlayerProfile fromPlayer = PlayerFinder.findPlayerProfileByName(cmdArg[0], false, plugin.isIncludeOfflinePlayer());
            if (fromPlayer == null) {
                plugin.text().of(sender, "unknown-player").send();
                return;
            }
            String fromPlayerName = fromPlayer.getName();
            if (fromPlayerName == null) {
                fromPlayerName = "null";
            }
            //FIXME: Update this when drop 1.15 supports
            final PlayerFinder.PlayerProfile targetPlayer = PlayerFinder.findPlayerProfileByName(cmdArg[1], false, plugin.isIncludeOfflinePlayer());
            if (targetPlayer == null) {
                plugin.text().of(sender, "unknown-player").send();
                return;
            }
            String targetPlayerName = targetPlayer.getName();
            if (targetPlayerName == null) {
                targetPlayerName = "null";
            }
            final UUID targetPlayerUUID = targetPlayer.getUuid();
            List<Shop> shopList = plugin.getShopManager().getPlayerAllShops(fromPlayer.getUuid());
            if (plugin.isLimit()) {
                final Player player = plugin.getServer().getPlayer(targetPlayerUUID);
                if (player == null) {
                    plugin.text().of(sender, "unknown-player").send();
                    return;
                }
                if (!checkAndSendLimitMessage(player, sender, shopList.size())) {
                    return;
                }
            }
            for (Shop shop : shopList) {
                shop.setOwner(targetPlayerUUID);
            }
            plugin.text().of(sender, "command.transfer-success-other", Integer.toString(shopList.size()), fromPlayerName, targetPlayerName).send();

        } else {
            plugin.text().of(sender, "command.wrong-args").send();
        }
    }

    private boolean checkAndSendLimitMessage(Player checkingPlayer, CommandSender commandSender, int increment) {
        if (plugin.isLimit()) {
            int owned = 0;
            if (plugin.getConfig().getBoolean("limits.old-algorithm")) {
                owned = plugin.getShopManager().getPlayerAllShops(checkingPlayer.getUniqueId()).size();
            } else {
                for (final Shop shop : plugin.getShopManager().getPlayerAllShops(checkingPlayer.getUniqueId())) {
                    if (!shop.isUnlimited()) {
                        owned++;
                    }
                }
            }
            int max = plugin.getShopLimit(checkingPlayer);
            if (owned + increment <= max) {
                plugin.text().of(commandSender, "reached-maximum-other-can-hold", String.valueOf(owned + increment), String.valueOf(max)).send();
                return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        return cmdArg.length <= 2 ? Util.getPlayerList() : Collections.emptyList();
    }
}
