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
import de.erethon.dungeonsxl.player.DGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * @author Daniel Saukel
 */
public class GroupSizeRequirement extends Requirement {

    private RequirementType type = RequirementTypeDefault.GROUP_SIZE;

    private int minimum;
    private int maximum;

    public GroupSizeRequirement(DungeonsXL plugin) {
        super(plugin);
    }

    /**
     * @return the group minimum
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * @param minimum the minimal group size to set
     */
    public void setMinimum(int minimum) {
        this.minimum = minimum;
    }

    /**
     * @return the group size maximum
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * @param maximum the maximal group size to set
     */
    public void setMaximum(int maximum) {
        this.maximum = maximum;
    }

    @Override
    public RequirementType getType() {
        return type;
    }

    /* Actions */
    @Override
    public void setup(ConfigurationSection config) {
        minimum = config.getInt("groupSize.minimum");
        maximum = config.getInt("groupSize.maximum");
    }

    @Override
    public boolean check(Player player) {
        DGroup dGroup = DGroup.getByPlayer(player);
        int size = dGroup.getPlayers().size();
        return size >= minimum && size <= maximum;
    }

    @Override
    public void demand(Player player) {
    }

    @Override
    public void showFailureMessage(Player player) {
        DGroup dGroup = DGroup.getByPlayer(player);
        int size = dGroup.getPlayers().size();
        String segment1 = null;
        String segment2 = null;
        //Your group                  players,
        //           dont have enough          minimum is 5
        //           have too many             maximum is 10
        if (size < minimum) {
            segment1 = "doesn't have enough";
            segment2 = "minimum is " + minimum;
        } else if(size > maximum) {
            segment1 = "has too many";
            segment2 = "maximum is " + maximum;
        }
        if(segment1 != null) MessageUtil.sendMessage(player, DMessage.GROUP_SIZE_REQUIREMENT.getMessage(segment1, segment2));
    }

}
