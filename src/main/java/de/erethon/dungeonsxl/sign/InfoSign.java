package de.erethon.dungeonsxl.sign;

import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.world.DGameWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.scheduler.BukkitTask;

public class InfoSign extends DSign {

    private BukkitTask ticker;

    public InfoSign(DungeonsXL plugin, Sign sign, String[] lines, DGameWorld gameWorld) {
        super(plugin, sign, lines, gameWorld);
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public void onInit() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                getSign().setLine(0, ChatColor.BOLD + "" + getGameWorld().getGame().getDungeon().getName());
                getSign().setLine(2, ChatColor.DARK_RED + "" + getGameWorld().getGame().getPlayers().size() + " players");
                getSign().setLine(3, ChatColor.DARK_RED + "" + getGameWorld().getGame().getUnplayedFloors().size() + " floors");
                getSign().update();
            } catch (Exception e) {
                ticker.cancel(); // If an error occurs getting some instance then just stop updating
            }
        }, 0, 20 * 3);
    }

    @Override
    public void onDisable() {
        ticker.cancel();
    }

    @Override
    public DSignType getType() {
        return DSignTypeDefault.INFO;
    }

}