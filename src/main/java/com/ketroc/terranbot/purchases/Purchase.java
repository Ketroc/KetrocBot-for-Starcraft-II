package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;

public interface Purchase {
    public PurchaseResult build();
    public Cost getCost();
    public void setCost();
    public String getType();

    public boolean canAfford();

    public static boolean isMorphQueued(Abilities morphType) { //TODO: relook at this.  it looks out of place here (but it is static)
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getMorphOrAddOn() == morphType) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgradeQueued(Upgrades upgrade) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getUpgrade() == upgrade) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgradeQueued(Tag structureTag) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getStructure().unit().getTag() == structureTag) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStructureQueued(Units unitType) {
        return isStructureQueued(unitType, null);
    }

    public static boolean isStructureQueued(Units unitType, Point2d pos) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructure &&
                    ((PurchaseStructure) p).getStructureType() == unitType &&
                    (pos == null || ((PurchaseStructure) p).getPosition().distance(pos) < 1)) {
                return true;
            }
        }
        return false;
    }

    public static Point2d getPositionOfQueuedStructure(Units unitType) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructure &&
                    ((PurchaseStructure) p).getStructureType() == unitType) {
                return ((PurchaseStructure) p).getPosition();
            }
        }
        return null;
    }
}
