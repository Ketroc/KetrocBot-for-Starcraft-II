package com.ketroc.purchases;

import com.ketroc.bots.KetrocBot;
import com.ketroc.models.Cost;

//dummy purchase to indicate the end of the build order
public class BuildOrderComplete implements Purchase {
    @Override
    public PurchaseResult build() {
        return KetrocBot.purchaseQueue.stream().findFirst().stream().anyMatch(purchase -> purchase instanceof BuildOrderComplete) ?
                PurchaseResult.SUCCESS :
                PurchaseResult.WAITING;
    }

    @Override
    public Cost getCost() {
        return null;
    }

    @Override
    public void setCost() {

    }

    @Override
    public String getType() {
        return "End of Build Order";
    }

    @Override
    public boolean canAfford() {
        return false;
    }
}
