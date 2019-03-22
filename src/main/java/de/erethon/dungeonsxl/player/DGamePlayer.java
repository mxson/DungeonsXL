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
package de.erethon.dungeonsxl.player;

import de.erethon.caliburn.item.VanillaItem;
import de.erethon.commons.chat.MessageUtil;
import de.erethon.commons.player.PlayerUtil;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.config.DMessage;
import de.erethon.dungeonsxl.dungeon.Dungeon;
import de.erethon.dungeonsxl.event.dplayer.DPlayerKickEvent;
import de.erethon.dungeonsxl.event.dplayer.instance.DInstancePlayerUpdateEvent;
import de.erethon.dungeonsxl.event.dplayer.instance.game.DGamePlayerDeathEvent;
import de.erethon.dungeonsxl.event.dplayer.instance.game.DGamePlayerFinishEvent;
import de.erethon.dungeonsxl.event.dplayer.instance.game.DGamePlayerRewardEvent;
import de.erethon.dungeonsxl.event.requirement.RequirementCheckEvent;
import de.erethon.dungeonsxl.game.Game;
import de.erethon.dungeonsxl.game.GameGoal;
import de.erethon.dungeonsxl.game.GameRuleProvider;
import de.erethon.dungeonsxl.game.GameType;
import de.erethon.dungeonsxl.game.GameTypeDefault;
import de.erethon.dungeonsxl.mob.DMob;
import de.erethon.dungeonsxl.requirement.Requirement;
import de.erethon.dungeonsxl.reward.Reward;
import de.erethon.dungeonsxl.trigger.DistanceTrigger;
import de.erethon.dungeonsxl.util.StringUtil;
import de.erethon.dungeonsxl.world.DGameWorld;
import de.erethon.dungeonsxl.world.DResourceWorld;
import de.erethon.dungeonsxl.world.block.TeamFlag;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Represents a player in a DGameWorld.
 *
 * @author Frank Baumann, Tobias Schmitz, Milan Albrecht, Daniel Saukel
 */
public class DGamePlayer extends DInstancePlayer {

    // Variables
    private DGroup dGroup;

    private boolean ready = false;
    private boolean finished = false;

    private DClass dClass;
    private Location checkpoint;
    private Wolf wolf;
    private int wolfRespawnTime = 30;
    private long offlineTime;

    private int initialLives = -1;
    private int lives;

    private ItemStack oldHelmet;
    private DGroup stealing;

    private DGroupTag groupTag;

    public DGamePlayer(DungeonsXL plugin, Player player, DGameWorld world) {
        super(plugin, player, world.getWorld());

        Game game = Game.getByGameWorld(world);
        if (game == null) {
            game = new Game(plugin, DGroup.getByPlayer(player));
        }

        GameRuleProvider rules = game.getRules();
        player.setGameMode(GameMode.SURVIVAL);

        if (!rules.getKeepInventoryOnEnter()) {
            clearPlayerData();
        }
        player.setAllowFlight(rules.canFly());

        if (rules.isLobbyDisabled()) {
            ready();
        }

        initialLives = rules.getInitialLives();
        lives = initialLives;

        Location teleport = world.getLobbyLocation();
        if (teleport == null) {
            PlayerUtil.secureTeleport(player, world.getWorld().getSpawnLocation());
        } else {
            PlayerUtil.secureTeleport(player, teleport);
        }
    }

    public DGamePlayer(DungeonsXL plugin, Player player, DGameWorld world, GameType ready) {
        this(plugin, player, world);
        if (ready != null) {
            ready(ready);
        }
    }

    /* Getters and setters */
    @Override
    public String getName() {
        String name = player.getName();
        if (getDGroup() != null && dGroup.getDColor() != null) {
            name = getDGroup().getDColor().getChatColor() + name;
        }
        return name;
    }

    @Override
    public DGroup getDGroup() {
        if (dGroup == null) {
            dGroup = DGroup.getByPlayer(player);
        }
        return dGroup;
    }

    /**
     * @param player the player to set
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * @return if the player is in test mode
     */
    public boolean isInTestMode() {
        if (getDGroup() == null) {
            return false;
        }

        DGameWorld gameWorld = dGroup.getGameWorld();
        if (gameWorld == null) {
            return false;
        }

        Game game = gameWorld.getGame();
        if (game == null) {
            return false;
        }

        GameType gameType = game.getType();
        if (gameType == GameTypeDefault.TEST) {
            return true;
        }

        return false;
    }

    /**
     * @return the isReady
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * @param ready If the player is ready to play the dungeon
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }

    /**
     * @return the finished
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * @param finished the finished to set
     */
    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    /**
     * @return the dClass
     */
    public DClass getDClass() {
        return dClass;
    }

    /**
     * @param className the name of the class to set
     */
    public void setDClass(String className) {
        Game game = Game.getByWorld(getPlayer().getWorld());
        if (game == null) {
            return;
        }

        DClass dClass = plugin.getDClassCache().getByName(className);
        if (dClass == null || this.dClass == dClass) {
            return;
        }
        this.dClass = dClass;

        /* Set Dog */
        if (wolf != null) {
            wolf.remove();
            wolf = null;
        }

        if (dClass.hasDog()) {
            wolf = (Wolf) getWorld().spawnEntity(getPlayer().getLocation(), EntityType.WOLF);
            wolf.setTamed(true);
            wolf.setOwner(getPlayer());

            double maxHealth = ((Damageable) wolf).getMaxHealth();
            wolf.setHealth(maxHealth);
        }

        /* Delete Inventory */
        getPlayer().getInventory().clear();
        getPlayer().getInventory().setArmorContents(null);
        getPlayer().getInventory().setItemInHand(VanillaItem.AIR.toItemStack());

        // Remove Potion Effects
        for (PotionEffect effect : getPlayer().getActivePotionEffects()) {
            getPlayer().removePotionEffect(effect.getType());
        }

        // Reset lvl
        getPlayer().setTotalExperience(0);
        getPlayer().setLevel(0);

        /* Set Inventory */
        for (ItemStack istack : dClass.getItems()) {

            // Leggings
            if (VanillaItem.LEATHER_LEGGINGS.is(istack) || VanillaItem.CHAINMAIL_LEGGINGS.is(istack) || VanillaItem.IRON_LEGGINGS.is(istack)
                    || VanillaItem.DIAMOND_LEGGINGS.is(istack) || VanillaItem.GOLDEN_LEGGINGS.is(istack)) {
                getPlayer().getInventory().setLeggings(istack);
            } // Helmet
            else if (VanillaItem.LEATHER_HELMET.is(istack) || VanillaItem.CHAINMAIL_HELMET.is(istack) || VanillaItem.IRON_HELMET.is(istack)
                    || VanillaItem.DIAMOND_HELMET.is(istack) || VanillaItem.GOLDEN_HELMET.is(istack)) {
                getPlayer().getInventory().setHelmet(istack);
            } // Chestplate
            else if (VanillaItem.LEATHER_CHESTPLATE.is(istack) || VanillaItem.CHAINMAIL_CHESTPLATE.is(istack) || VanillaItem.IRON_CHESTPLATE.is(istack)
                    || VanillaItem.DIAMOND_CHESTPLATE.is(istack) || VanillaItem.GOLDEN_CHESTPLATE.is(istack)) {
                getPlayer().getInventory().setChestplate(istack);
            } // Boots
            else if (VanillaItem.LEATHER_BOOTS.is(istack) || VanillaItem.CHAINMAIL_BOOTS.is(istack) || VanillaItem.IRON_BOOTS.is(istack)
                    || VanillaItem.DIAMOND_BOOTS.is(istack) || VanillaItem.GOLDEN_BOOTS.is(istack)) {
                getPlayer().getInventory().setBoots(istack);
            } else {
                getPlayer().getInventory().addItem(istack);
            }
        }
    }

    /**
     * @return the checkpoint
     */
    public Location getCheckpoint() {
        return checkpoint;
    }

    /**
     * @param checkpoint the checkpoint to set
     */
    public void setCheckpoint(Location checkpoint) {
        this.checkpoint = checkpoint;
    }

    /**
     * @return the wolf
     */
    public Wolf getWolf() {
        return wolf;
    }

    /**
     * @param wolf the wolf to set
     */
    public void setWolf(Wolf wolf) {
        this.wolf = wolf;
    }

    /**
     * @return the wolfRespawnTime
     */
    public int getWolfRespawnTime() {
        return wolfRespawnTime;
    }

    /**
     * @param wolfRespawnTime the wolfRespawnTime to set
     */
    public void setWolfRespawnTime(int wolfRespawnTime) {
        this.wolfRespawnTime = wolfRespawnTime;
    }

    /**
     * @return the offlineTime
     */
    public long getOfflineTime() {
        return offlineTime;
    }

    /**
     * @param offlineTime the offlineTime to set
     */
    public void setOfflineTime(long offlineTime) {
        this.offlineTime = offlineTime;
    }

    /**
     * @return the initialLives
     */
    public int getInitialLives() {
        return initialLives;
    }

    /**
     * @param initialLives the initialLives to set
     */
    public void setInitialLives(int initialLives) {
        this.initialLives = initialLives;
    }

    /**
     * @return the lives
     */
    public int getLives() {
        return lives;
    }

    /**
     * @param lives the lives to set
     */
    public void setLives(int lives) {
        this.lives = lives;
    }

    /**
     * @return if the player is stealing a flag
     */
    public boolean isStealing() {
        return stealing != null;
    }

    /**
     * @return the group whose flag is stolen
     */
    public DGroup getRobbedGroup() {
        return stealing;
    }

    /**
     * @param dGroup the group whose flag is stolen
     */
    public void setRobbedGroup(DGroup dGroup) {
        if (dGroup != null) {
            oldHelmet = player.getInventory().getHelmet();
            player.getInventory().setHelmet(getDGroup().getDColor().getWoolMaterial().toItemStack());
        }

        stealing = dGroup;
    }

    /**
     * @return the player's group tag
     */
    public DGroupTag getDGroupTag() {
        return groupTag;
    }

    /**
     * Creates a new group tag for the player.
     */
    public void initDGroupTag() {
        groupTag = new DGroupTag(plugin, this);
    }

    /* Actions */
    public void captureFlag() {
        if (stealing == null) {
            return;
        }

        Game game = Game.getByWorld(getWorld());
        if (game == null) {
            return;
        }

        game.sendMessage(DMessage.GROUP_FLAG_CAPTURED.getMessage(getName(), stealing.getName()));

        GameRuleProvider rules = game.getRules();

        getDGroup().setScore(getDGroup().getScore() + 1);
        if (rules.getScoreGoal() == dGroup.getScore()) {
            dGroup.winGame();
        }

        stealing.setScore(stealing.getScore() - 1);
        if (stealing.getScore() == -1) {
            for (DGamePlayer member : stealing.getDGamePlayers()) {
                member.kill();
            }
            game.sendMessage(DMessage.GROUP_DEFEATED.getMessage(stealing.getName()));
        }

        stealing = null;
        player.getInventory().setHelmet(oldHelmet);

        if (game.getDGroups().size() == 1) {
            dGroup.winGame();
        }
    }

    @Override
    public void leave() {
        leave(true);
    }

    /**
     * @param message if messages should be sent
     */
    public void leave(boolean message) {
        Game game = Game.getByWorld(getWorld());
        if (game == null) {
            return;
        }
        DGameWorld gameWorld = game.getWorld();
        if (gameWorld == null) {
            return;
        }
        GameRuleProvider rules = game.getRules();
        delete();

        if (player.isOnline()) {
            if (finished) {
                reset(rules.getKeepInventoryOnFinish());
            } else {
                reset(rules.getKeepInventoryOnEscape());
            }
        }

        // Permission bridge
        if (plugin.getPermissionProvider() != null) {
            for (String permission : rules.getGamePermissions()) {
                plugin.getPermissionProvider().playerRemoveTransient(getWorld().getName(), player, permission);
            }
        }

        if (getDGroup() != null) {
            dGroup.removePlayer(getPlayer(), message);
        }

        if (game != null) {
            if (finished) {
                if (game.getType() == GameTypeDefault.CUSTOM || game.getType().hasRewards()) {
                    DGamePlayerRewardEvent dGroupRewardEvent = new DGamePlayerRewardEvent(this);
                    Bukkit.getPluginManager().callEvent(dGroupRewardEvent);
                    if (!dGroupRewardEvent.isCancelled()) {
                        giveLoot(rules, rules.getRewards(), dGroup.getRewards());
                    }

                    getData().logTimeLastFinished(getDGroup().getDungeonName());

                    // Tutorial Permissions
                    if (game.isTutorial()) {
                        getData().setFinishedTutorial(true);
                        if (plugin.getPermissionProvider() != null && plugin.getPermissionProvider().hasGroupSupport()) {
                            String endGroup = plugin.getMainConfig().getTutorialEndGroup();
                            if (plugin.isGroupEnabled(endGroup)) {
                                plugin.getPermissionProvider().playerAddGroup(getPlayer(), endGroup);
                            }

                            String startGroup = plugin.getMainConfig().getTutorialStartGroup();
                            if (plugin.isGroupEnabled(startGroup)) {
                                plugin.getPermissionProvider().playerRemoveGroup(getPlayer(), startGroup);
                            }
                        }
                    }
                }
            }
        }

        if (getDGroup() != null) {
            if (!dGroup.isEmpty()) {
                /*if (dGroup.finishIfMembersFinished()) {
                    return;
                }*/

                // Give secure objects to other players
                Player groupPlayer = null;
                for (Player player : dGroup.getPlayers().getOnlinePlayers()) {
                    if (player.isOnline()) {
                        groupPlayer = player;
                        break;
                    }
                }
                if (groupPlayer != null) {
                    for (ItemStack itemStack : getPlayer().getInventory()) {
                        if (itemStack != null) {
                            if (gameWorld.getSecureObjects().contains(itemStack)) {
                                groupPlayer.getInventory().addItem(itemStack);
                            }
                        }
                    }
                }
            }

            if (dGroup.getCaptain().equals(getPlayer()) && dGroup.getPlayers().size() > 0) {
                // Captain here!
                Player newCaptain = null;
                for (Player player : dGroup.getPlayers().getOnlinePlayers()) {
                    if (player.isOnline()) {
                        newCaptain = player;
                        break;
                    }
                }
                dGroup.setCaptain(newCaptain);
                if (message) {
                    MessageUtil.sendMessage(newCaptain, DMessage.PLAYER_NEW_CAPTAIN.getMessage());
                }
                // ...*flies away*
            }
        }
    }

    public void kill() {
        DPlayerKickEvent dPlayerKickEvent = new DPlayerKickEvent(this, DPlayerKickEvent.Cause.DEATH);
        Bukkit.getPluginManager().callEvent(dPlayerKickEvent);

        if (!dPlayerKickEvent.isCancelled()) {
            DGameWorld gameWorld = getDGroup().getGameWorld();
            if (lives != -1) {
                gameWorld.sendMessage(DMessage.PLAYER_DEATH_KICK.getMessage(getName()));
            } else if (getDGroup().getLives() != -1) {
                gameWorld.sendMessage(DMessage.GROUP_DEATH_KICK.getMessage(getName(), dGroup.getName()));
            }

            GameRuleProvider rules = Game.getByPlayer(player).getRules();
            leave();
            if (rules.getKeepInventoryOnEscape() && rules.getKeepInventoryOnDeath()) {
                applyRespawnInventory();
            }
        }
    }

    public boolean checkRequirements(Dungeon dungeon, GameRuleProvider rules) {
        if (DPermission.hasPermission(player, DPermission.IGNORE_REQUIREMENTS)) {
            return true;
        }

        if (!checkTimeAfterStart(dungeon, rules) && !checkTimeAfterFinish(dungeon, rules)) {
            final boolean check = rules.getTimeToNextPlayAfterStart() >= rules.getTimeToNextPlayAfterFinish();
            long endTime = check ? getTimeStartEnd(dungeon, rules) : getTimeFinishEnd(dungeon, rules); // greater than Systemn current ms
            final String timeLeft = StringUtil.humanReadableMillis(System.currentTimeMillis() - endTime);

            MessageUtil.sendMessage(player, DMessage.ERROR_COOLDOWN.getMessage(timeLeft));
            return false;

        } else if (!checkTimeAfterStart(dungeon, rules)) {
            final String message = StringUtil.humanReadableMillis(getTimeStartEnd(dungeon, rules) - System.currentTimeMillis());
            MessageUtil.sendMessage(player, DMessage.ERROR_COOLDOWN.getMessage(message));
            return false;

        } else if (!checkTimeAfterFinish(dungeon, rules)) {
            final String message = StringUtil.humanReadableMillis(getTimeFinishEnd(dungeon, rules) - System.currentTimeMillis());
            MessageUtil.sendMessage(player, DMessage.ERROR_COOLDOWN.getMessage(message));
            return false;
        }

        for (Requirement requirement : rules.getRequirements()) {
            RequirementCheckEvent event = new RequirementCheckEvent(requirement, player);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                continue;
            }

            if (!requirement.check(player)) {
                requirement.showFailureMessage(player);
                return false;
            }
        }

        if (rules.getFinished() != null && rules.getFinishedAll() != null) {
            if (!rules.getFinished().isEmpty()) {

                long bestTime = 0;
                int numOfNeeded = 0;
                boolean doneTheOne = false;

                if (rules.getFinished().size() == rules.getFinishedAll().size()) {
                    doneTheOne = true;
                }

                for (String played : rules.getFinished()) {
                    for (String dungeonName : DungeonsXL.MAPS.list()) {
                        if (new File(DungeonsXL.MAPS, dungeonName).isDirectory()) {
                            if (played.equalsIgnoreCase(dungeonName) || played.equalsIgnoreCase("any")) {

                                Long time = getData().getTimeLastFinished(dungeonName);
                                if (time != -1) {
                                    if (rules.getFinishedAll().contains(played)) {
                                        numOfNeeded++;
                                    } else {
                                        doneTheOne = true;
                                    }
                                    if (bestTime < time) {
                                        bestTime = time;
                                    }
                                }
                                break;

                            }
                        }
                    }
                }

                if (bestTime == 0) {
                    if(!doneTheOne) MessageUtil.sendMessage(player, DMessage.REQUIRE_FINISHED_ONCE.getMessage(StringUtil.concatList(rules.getFinishedOne())));
                    else MessageUtil.sendMessage(player, DMessage.REQUIRE_FINISHED_ALL.getMessage(StringUtil.concatList(rules.getFinishedAll())));
                    return false;

                } else if (rules.getTimeLastPlayed() != 0) {
                    if (System.currentTimeMillis() - bestTime > rules.getTimeLastPlayed() * (long) 3600000) {
                        final long timeToWaitSeconds = ((rules.getTimeLastPlayed() * (long) 3600000) - (System.currentTimeMillis() - bestTime)) / 1000;
                        MessageUtil.sendMessage(player, DMessage.REQUIRE_WAIT_BETWEEN.getMessage(timeToWaitSeconds + " seconds"));
                        return false;
                    }
                }

                if (numOfNeeded < rules.getFinishedAll().size() || !doneTheOne) {
                    MessageUtil.sendMessage(player, DMessage.REQUIRE_FINISHED_ALL.getMessage(StringUtil.concatList(rules.getFinishedAll())));
                    return false;
                }

            }
        }

        return true;
    }

    public boolean checkTimeAfterStart(Dungeon dungeon, GameRuleProvider rules) {
        return checkTime(rules.getTimeToNextPlayAfterStart(), getData().getTimeLastStarted(dungeon.getName()));
    }

    public long getTimeStartEnd(Dungeon dungeon, GameRuleProvider rules) {
        return getData().getTimeLastStarted(dungeon.getName()) + rules.getTimeToNextPlayAfterStart() * 1000 * 60 * 60;
    }

    public long getTimeFinishEnd(Dungeon dungeon, GameRuleProvider rules) {
        return getData().getTimeLastFinished(dungeon.getName()) + rules.getTimeToNextPlayAfterFinish() * 1000 * 60 * 60;
    }

    public long getTimeStartEnd(Game game) {
        return getData().getTimeLastStarted(game.getDungeon().getName()) + game.getRules().getTimeToNextPlayAfterStart() * 1000 * 60 * 60;
    }

    public long getTimeFinishEnd(Game game) {
        return getData().getTimeLastFinished(game.getDungeon().getName()) + game.getRules().getTimeToNextPlayAfterFinish() * 1000 * 60 * 60;
    }

    public boolean checkTimeAfterFinish(Dungeon dungeon, GameRuleProvider rules) {
        return checkTime(rules.getTimeToNextPlayAfterFinish(), getData().getTimeLastFinished(dungeon.getName()));
    }

    public boolean checkTime(int requirement, long dataTime) {
        if (DPermission.hasPermission(player, DPermission.IGNORE_TIME_LIMIT)) {
            return true;
        }

        return dataTime == -1 || dataTime + requirement * 1000 * 60 * 60 <= System.currentTimeMillis();
    }

    public void giveLoot(GameRuleProvider rules, List<Reward> ruleRewards, List<Reward> groupRewards) {
        if (!canLoot(rules)) {
            return;
        }
        ruleRewards.forEach(r -> r.giveTo(player.getPlayer()));
        groupRewards.forEach(r -> r.giveTo(player.getPlayer()));
        getData().logTimeLastLoot(dGroup.getDungeonName());
    }

    public boolean canLoot(GameRuleProvider rules) {
        return getTimeNextLoot(rules) <= getData().getTimeLastStarted(getDGroup().getDungeonName());
    }

    public long getTimeNextLoot(GameRuleProvider rules) {
        return rules.getTimeToNextLoot() * 60 * 60 * 1000 + getData().getTimeLastLoot(getDGroup().getDungeonName());
    }

    public void ready() {
        ready(GameTypeDefault.DEFAULT);
    }

    public boolean ready(GameType gameType) {
        if (getDGroup() == null) {
            return false;
        }

        Dungeon dungeon;
        GameRuleProvider rules;

        Game foundGame = Game.getByGameWorld(dGroup.getGameWorld());
        if (foundGame == null) {
            dungeon = dGroup.getDungeon();
            rules = Game.getGameRules(gameType, dGroup.getGameWorld(), dungeon);

        } else {
            dungeon = foundGame.getDungeon();
            rules = foundGame.getRules();
        }

        if (!checkRequirements(dungeon, rules)) { // check requirements should send message
            return false;
        }
        Game game = foundGame == null ? new Game(plugin, dGroup, gameType, dGroup.getGameWorld()) : foundGame;
        game.fetchRules();

        ready = true;

        boolean start = true;
        for (DGroup gameGroup : game.getDGroups()) {
            if (!gameGroup.isPlaying()) {
                if (!gameGroup.startGame(game)) {
                    start = false;
                }
            } else {
                respawn();
            }
        }

        game.setStarted(true);
        return start;
    }

    public void respawn() {
        Location respawn = checkpoint;

        if (respawn == null) {
            respawn = getDGroup().getGameWorld().getStartLocation(dGroup);
        }

        if (respawn == null) {
            respawn = getWorld().getSpawnLocation();
        }

        PlayerUtil.secureTeleport(getPlayer(), respawn);

        // Don't forget Doge!
        if (wolf != null) {
            wolf.teleport(getPlayer());
        }

        // Respawn Items
        Game game = Game.getByWorld(getWorld());

        if (game != null && game.getRules().getKeepInventoryOnDeath()) {
            applyRespawnInventory();
        }
    }

    /**
     * The DGamePlayer finishs the current floor.
     *
     * @param specifiedFloor the name of the next floor
     */
    public void finishFloor(DResourceWorld specifiedFloor) {
        if (!dGroup.getDungeon().isMultiFloor()) {
            finish();
            return;
        }

        MessageUtil.sendMessage(getPlayer(), DMessage.PLAYER_FINISHED_FLOOR.getMessage());
        finished = true;

        boolean hasToWait = false;
        if (getDGroup() == null) {
            return;
        }
        if (!dGroup.isPlaying()) {
            return;
        }
        dGroup.setNextFloor(specifiedFloor);
        if (dGroup.isFinished()) {
            dGroup.finishFloor(specifiedFloor);
        } else {
            MessageUtil.sendMessage(player, DMessage.PLAYER_WAIT_FOR_OTHER_PLAYERS.getMessage());
            hasToWait = true;
        }

        DGamePlayerFinishEvent dPlayerFinishEvent = new DGamePlayerFinishEvent(this, hasToWait);
        Bukkit.getPluginManager().callEvent(dPlayerFinishEvent);
        if (dPlayerFinishEvent.isCancelled()) {
            finished = false;
        }
    }

    /**
     * The DGamePlayer finishs the current game.
     */
    public void finish() {
        finish(true);
    }

    /**
     * @param message if messages should be sent
     */
    public void finish(boolean message) {
        if (message) {
            MessageUtil.sendMessage(getPlayer(), DMessage.PLAYER_FINISHED_DUNGEON.getMessage());
        }
        finished = true;

        boolean hasToWait = false;
        if (!getDGroup().isPlaying()) {
            return;
        }
        if (dGroup.isFinished()) {
            dGroup.finish();
        } else {
            if (message) {
                MessageUtil.sendMessage(this.getPlayer(), DMessage.PLAYER_WAIT_FOR_OTHER_PLAYERS.getMessage());
            }
            hasToWait = true;
        }

        DGamePlayerFinishEvent dPlayerFinishEvent = new DGamePlayerFinishEvent(this, hasToWait);
        Bukkit.getPluginManager().callEvent(dPlayerFinishEvent);
        if (dPlayerFinishEvent.isCancelled()) {
            finished = false;
        }
    }

    public void onDeath(PlayerDeathEvent event) {
        DGameWorld gameWorld = DGameWorld.getByWorld(player.getLocation().getWorld());
        if (gameWorld == null) {
            return;
        }

        Game game = Game.getByGameWorld(gameWorld);
        if (game == null) {
            return;
        }

        DGamePlayerDeathEvent dPlayerDeathEvent = new DGamePlayerDeathEvent(this, event, 1);
        Bukkit.getPluginManager().callEvent(dPlayerDeathEvent);

        if (dPlayerDeathEvent.isCancelled()) {
            return;
        }

        if (config.areGlobalDeathMessagesDisabled()) {
            event.setDeathMessage(null);
        }

        if (game.getRules().getKeepInventoryOnDeath()) {
            setRespawnInventory(event.getEntity().getInventory().getContents());
            setRespawnArmor(event.getEntity().getInventory().getArmorContents());
            // Delete all drops
            for (ItemStack item : event.getDrops()) {
                item.setType(VanillaItem.AIR.getMaterial());
            }
        }

        if (getDGroup() != null && dGroup.getLives() != -1) {
            int newLives = dGroup.getLives() - dPlayerDeathEvent.getLostLives();
            dGroup.setLives(newLives < 0 ? 0 : newLives);// If the group already has 0 lives, don't remove any
            gameWorld.sendMessage(DMessage.GROUP_DEATH.getMessage(getName(), dGroup.getName(), String.valueOf(dGroup.getLives())));

        } else {
            if (lives != -1) {
                lives = lives - dPlayerDeathEvent.getLostLives();
            }

            DGamePlayer killer = DGamePlayer.getByPlayer(player.getKiller());
            String newLives = lives == -1 ? DMessage.MISC_UNLIMITED.getMessage() : String.valueOf(this.lives);
            if (killer != null) {
                gameWorld.sendMessage(DMessage.PLAYER_KILLED.getMessage(getName(), killer.getName(), newLives));
            } else {
                gameWorld.sendMessage(DMessage.PLAYER_DEATH.getMessage(getName(), newLives));
            }
        }

        if (isStealing()) {
            for (TeamFlag teamFlag : gameWorld.getTeamFlags()) {
                if (teamFlag.getOwner().equals(stealing)) {
                    teamFlag.reset();
                    gameWorld.sendMessage(DMessage.GROUP_FLAG_LOST.getMessage(player.getName(), stealing.getName()));
                    stealing = null;
                }
            }
        }

        if ((dGroup.getLives() == 0 || lives == 0) && ready) {
            kill();
        }

        GameType gameType = game.getType();
        if (gameType != null && gameType != GameTypeDefault.CUSTOM) {
            if (gameType.getGameGoal() == GameGoal.LAST_MAN_STANDING) {
                if (game.getDGroups().size() == 1) {
                    game.getDGroups().get(0).winGame();
                }
            }
        }
    }

    @Override
    public void update(boolean updateSecond) {
        boolean locationValid = true;
        Location teleportLocation = player.getLocation();
        boolean teleportWolf = false;
        boolean respawnInventory = false;
        boolean offline = false;
        boolean kick = false;
        boolean triggerAllInDistance = false;

        DGameWorld gameWorld = DGameWorld.getByWorld(getWorld());

        if (!updateSecond) {
            if (!getPlayer().getWorld().equals(getWorld())) {
                locationValid = false;

                if (gameWorld != null) {
                    teleportLocation = getCheckpoint();

                    if (teleportLocation == null) {
                        teleportLocation = getDGroup().getGameWorld().getStartLocation(getDGroup());
                    }

                    // Don't forget Doge!
                    if (getWolf() != null) {
                        teleportWolf = true;
                    }

                    // Respawn Items
                    if (getRespawnInventory() != null || getRespawnArmor() != null) {
                        respawnInventory = true;
                    }
                }
            }

        } else if (gameWorld != null) {
            // Update Wolf
            if (getWolf() != null) {
                if (getWolf().isDead()) {
                    if (getWolfRespawnTime() <= 0) {
                        setWolf((Wolf) getWorld().spawnEntity(getPlayer().getLocation(), EntityType.WOLF));
                        getWolf().setTamed(true);
                        getWolf().setOwner(getPlayer());
                        setWolfRespawnTime(30);
                    }
                    wolfRespawnTime--;
                }

                DMob dMob = DMob.getByEntity(getWolf());
                if (dMob != null) {
                    gameWorld.removeDMob(dMob);
                }
            }

            // Kick offline players
            if (getOfflineTime() > 0) {
                offline = true;

                if (getOfflineTime() < System.currentTimeMillis()) {
                    kick = true;
                }
            }

            triggerAllInDistance = true;
        }

        DInstancePlayerUpdateEvent event = new DInstancePlayerUpdateEvent(this, locationValid, teleportWolf, respawnInventory, offline, kick, triggerAllInDistance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (!locationValid) {
            PlayerUtil.secureTeleport(getPlayer(), teleportLocation);
        }

        if (teleportWolf) {
            getWolf().teleport(teleportLocation);
        }

        if (respawnInventory) {
            applyRespawnInventory();
        }

        if (kick) {
            DPlayerKickEvent dPlayerKickEvent = new DPlayerKickEvent(this, DPlayerKickEvent.Cause.OFFLINE);
            Bukkit.getPluginManager().callEvent(dPlayerKickEvent);

            if (!dPlayerKickEvent.isCancelled()) {
                leave();
            }
        }

        if (triggerAllInDistance) {
            DistanceTrigger.triggerAllInDistance(getPlayer(), gameWorld);
        }
    }

    /* Statics */
    public static DGamePlayer getByPlayer(Player player) {
        for (DGamePlayer dPlayer : DungeonsXL.getInstance().getDPlayerCache().getDGamePlayers()) {
            if (dPlayer.getPlayer().equals(player)) {
                return dPlayer;
            }
        }
        return null;
    }

    public static DGamePlayer getByName(String name) {
        for (DGamePlayer dPlayer : DungeonsXL.getInstance().getDPlayerCache().getDGamePlayers()) {
            if (dPlayer.getPlayer().getName().equalsIgnoreCase(name) || dPlayer.getName().equalsIgnoreCase(name)) {
                return dPlayer;
            }
        }

        return null;
    }

    public static List<DGamePlayer> getByWorld(World world) {
        List<DGamePlayer> dPlayers = new ArrayList<>();

        for (DGamePlayer dPlayer : DungeonsXL.getInstance().getDPlayerCache().getDGamePlayers()) {
            if (dPlayer.getWorld() == world) {
                dPlayers.add(dPlayer);
            }
        }

        return dPlayers;
    }

}