package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.terranbot.Bot;
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
}
