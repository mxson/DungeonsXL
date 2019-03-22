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

import de.erethon.commons.chat.MessageUtil;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.config.DMessage;
import de.erethon.dungeonsxl.player.DPermission;
import java.util.ArrayList;
import java.util.List;

import de.erethon.dungeonsxl.util.StringUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * @author Daniel Saukel
 */
public class PermissionRequirement extends Requirement {

    private RequirementType type = RequirementTypeDefault.PERMISSION;

    private List<String> permissions = new ArrayList<>();

    public PermissionRequirement(DungeonsXL plugin) {
        super(plugin);
    }

    /* Getters and setters */
    /**
     * @return the permission the player must have to play the dungeon
     */
    public List<String> getPermissions() {
        return permissions;
    }

    /**
     * @param permissions the permissions to set
     */
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public RequirementType getType() {
        return type;
    }

    /* Actions */
    @Override
    public void setup(ConfigurationSection config) {
        permissions = config.getStringList("permission");
    }

    @Override
    public boolean check(Player player) {
        for (String permission : permissions) {
            if (!DPermission.hasPermission(player, permission)) {
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
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (!DPermission.hasPermission(player, permission)) {
                missing.add(permission);
            }
        }
        if(missing.size() > 0) {
            MessageUtil.sendMessage(player, DMessage.PERMISSION_REQUIREMENT.getMessage(StringUtil.concatList(missing)));
        }
    }

}
