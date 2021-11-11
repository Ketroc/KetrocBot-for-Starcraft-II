package com.ketroc.purchases;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.KetrocBot;
import com.ketroc.models.Cost;

public interface Purchase {
    PurchaseResult build();
    Cost getCost();
    void setCost();
    String getType();

    boolean canAfford();

    static boolean isMorphQueued(Abilities morphType) { //TODO: relook at this.  it looks out of place here (but it is static)
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getMorphOrAddOn() == morphType) {
                return true;
            }
        }
        return false;
    }

    static boolean isMorphQueued(Tag structureTag, Abilities morphType) { //TODO: relook at this.  it looks out of place here (but it is static)
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseStructureMorph &&
                    ((PurchaseStructureMorph) p).getMorphOrAddOn() == morphType &&
                    ((PurchaseStructureMorph) p).getProductionStructure().getTag().equals(structureTag)) {
                return true;
            }
        }
        return false;
    }

    static boolean isUpgradeQueued(Upgrades upgrade) {
        return KetrocBot.purchaseQueue.stream()
                .anyMatch(p -> p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getUpgrade() == upgrade);
    }

    static boolean containsUpgrade() {
        return KetrocBot.purchaseQueue.stream()
                .anyMatch(purchase -> purchase instanceof PurchaseUpgrade);
    }



    static boolean isUpgradeQueued(Tag structureTag) {
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseUpgrade &&
                    ((PurchaseUpgrade) p).getProductionStructure() != null &&
                    ((PurchaseUpgrade) p).getProductionStructure().unit().getTag() == structureTag) {
                return true;
            }
        }
        return false;
    }

    static boolean isStructureQueued(Units unitType) {
        return isStructureQueued(unitType, null);
    }

    static boolean isAddOnQueued(Unit structureUnit) {
        return KetrocBot.purchaseQueue.stream()
                .filter(purchase -> purchase instanceof PurchaseStructureMorph)
                .map(purchase -> (PurchaseStructureMorph)purchase)
                .anyMatch(p -> p.getProductionStructure().getTag().equals(structureUnit.getTag()));
    }

    static boolean isStructureQueued(Units unitType, Point2d pos) {
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseStructure &&
                    ((PurchaseStructure) p).getStructureType() == unitType &&
                    (pos == null || ((PurchaseStructure) p).getPosition().distance(pos) < 1)) {
                return true;
            }
        }
        return false;
    }

    static int numStructuresQueuedOfType(Units unitType) {
        return (int)KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == unitType)
                .count();
    }

    static Point2d getPositionOfQueuedStructure(Units unitType) {
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseStructure &&
                    ((PurchaseStructure) p).getStructureType() == unitType) {
                return ((PurchaseStructure) p).getPosition();
            }
        }
        return null;
    }

    static void removeAll(Units unitType) {
        KetrocBot.purchaseQueue.removeIf(p -> p instanceof PurchaseStructure &&
                ((PurchaseStructure) p).getStructureType() == unitType);
    }

    static void removeFirst(Units unitType) {
        removeFirst(unitType, 0);
    }

    static void removeFirst(Units unitType, int numSkip) {
        KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == unitType)
                .skip(numSkip)
                .findFirst()
                .ifPresent(purchase -> KetrocBot.purchaseQueue.removeFirstOccurrence(purchase));
    }

}
