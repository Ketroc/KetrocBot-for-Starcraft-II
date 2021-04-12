package com.ketroc.terranbot.strategies.defenses;

import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.LocationConstants;

public class WorkerRushDefense2 {

    public static void onGameStart() {
        if (Strategy.WALL_OFF_IMMEDIATELY) {
            makeBuildAdjustment();
        }
    }

    //TODO: switch to depot/engbay/rax wall when production code sends out scv early to build the depot
    public static void makeBuildAdjustment() {
//        //cancel cc (& rax if not on the wall)
//        StructureScv.scvBuildingList.stream()
//                .filter(structureScv -> structureScv.structureType == Units.TERRAN_COMMAND_CENTER)
//                .findAny()
//                .ifPresent(structureScv -> {
//                    StructureScv.cancelProduction(Units.TERRAN_COMMAND_CENTER, GameCache.baseList.get(0).getCcPos());
//                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
//                });
//        StructureScv.scvBuildingList.stream()
//            .filter(structureScv -> structureScv.structureType == Units.TERRAN_BARRACKS && structureScv.structurePos.distance(LocationConstants.MID_WALL_3x3) > 1)
//            .findAny()
//            .ifPresent(structureScv -> {
//                    StructureScv.cancelProduction(Units.TERRAN_BARRACKS, GameCache.baseList.get(0).getCcPos());
//            });


        //build eng bay on wall
        KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == Units.TERRAN_ENGINEERING_BAY)
                .findFirst()
                .ifPresent(purchase -> KetrocBot.purchaseQueue.removeFirstOccurrence(purchase));
        KetrocBot.purchaseQueue.add(1, new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY, LocationConstants.WALL_3x3));

        //build 2nd depot on wall
        KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == Units.TERRAN_SUPPLY_DEPOT)
                .skip(1)
                .findFirst()
                .ifPresent(purchase -> KetrocBot.purchaseQueue.removeFirstOccurrence(purchase));
        KetrocBot.purchaseQueue.add(2, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.MID_WALL_2x2));

        //build barracks (not on wall)
        KetrocBot.purchaseQueue.stream()
                .filter(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == Units.TERRAN_BARRACKS)
                .findFirst()
                .ifPresent(purchase -> KetrocBot.purchaseQueue.removeFirstOccurrence(purchase));
        KetrocBot.purchaseQueue.add(3, new PurchaseStructure(Units.TERRAN_BARRACKS));

//        //set same scv for 1st depot and 1st barracks
//        Unit scv = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV).get(0).unit();
//        ((PurchaseStructure)KetrocBot.purchaseQueue.get(0)).setScv(scv);
//        ((PurchaseStructure)KetrocBot.purchaseQueue.get(2)).setScv(scv);

        //remove command center from build order so gas & factory is up quicker
        Purchase.removeAll(Units.TERRAN_COMMAND_CENTER);
    }
}
