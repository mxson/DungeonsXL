package de.erethon.dungeonsxl.sign;

import de.erethon.caliburn.item.VanillaItem;
import de.erethon.dungeonsxl.DungeonsXL;
import de.erethon.dungeonsxl.world.DGameWorld;
import org.bukkit.block.Sign;

public class CompletionSign extends LocationSign {

    public CompletionSign(DungeonsXL plugin, Sign sign, String[] lines, DGameWorld gameWorld) {
        super(plugin, sign, lines, gameWorld);
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public void onInit() {
        getGameWorld().setCompletionLocation(getLocation());
        getSign().getBlock().setType(VanillaItem.AIR.getMaterial());
    }

    @Override
    public DSignType getType() {
        return DSignTypeDefault.COMPLETION;
    }

}