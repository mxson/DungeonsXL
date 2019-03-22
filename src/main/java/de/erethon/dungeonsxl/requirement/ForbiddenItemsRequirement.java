/*
 * Copyright (C) 2012-2019 Frank Baumann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.erethon.dungeonsxl.requirement;

import de.erethon.caliburn.CaliburnAPI;
import de.erethon.caliburn.item.ExItem;
import de.erethon.commons.chat.MessageUtil;
import de.erethon.dungeonsxl.DungeonsXL;

import java.util.ArrayList;
import java.util.List;

import de.erethon.dungeonsxl.config.DMessage;
import de.erethon.dungeonsxl.util.StringUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * @author Daniel Saukel
 */
public class ForbiddenItemsRequirement extends Requirement {

    private CaliburnAPI caliburn;

    private RequirementType type = RequirementTypeDefault.FORBIDDEN_ITEMS;

    private List<ExItem> forbiddenItems;

    public ForbiddenItemsRequirement(DungeonsXL plugin) {
        super(plugin);
        caliburn = plugin.getCaliburn();
    }

    /* Getters and setters */
    /**
     * @return the forbidden items
     */
    public List<ExItem> getForbiddenItems() {
        return forbiddenItems;
    }

    @Override
    public RequirementType getType() {
        return type;
    }

    /* Actions */
    @Override
    public void setup(ConfigurationSection config) {
        forbiddenItems = caliburn.deserializeExItemList(config, "forbiddenItems");
    }

    @Override
    public boolean check(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            ExItem exItem = caliburn.getExItem(item);
            if (forbiddenItems.contains(exItem)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void demand(Player player) {
    }

    @Override
    public void showFailureMessage(Player player) {
        final List<String> forbiddenMessages = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            ExItem exItem = caliburn.getExItem(item);
            if (forbiddenItems.contains(exItem)) {
                forbiddenMessages.add(exItem.getName().toLowerCase());
            }
        }
        if(forbiddenMessages.size() > 0) {
            MessageUtil.sendMessage(player, DMessage.FORBIDDEN_ITEM_REQUIREMENT.getMessage(StringUtil.concatList(forbiddenMessages)));
        }
    }

}
