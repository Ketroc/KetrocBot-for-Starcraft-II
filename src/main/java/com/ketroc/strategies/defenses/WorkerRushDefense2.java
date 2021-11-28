package com.ketroc.strategies.defenses;

import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.bots.KetrocBot;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Time;

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

        //TODO: move first depot to wall (in case it's on reaper wall vs scv rush)
        //build 1st depot on wall
        Purchase.removeFirst(Units.TERRAN_SUPPLY_DEPOT);
        KetrocBot.purchaseQueue.add(0, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));

        //build eng bay on wall
        Purchase.removeFirst(Units.TERRAN_ENGINEERING_BAY);
        KetrocBot.purchaseQueue.add(1, new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY, LocationConstants.WALL_3x3));

        //build 2nd depot on wall
        Purchase.removeFirst(Units.TERRAN_SUPPLY_DEPOT, 1);
        KetrocBot.purchaseQueue.add(2, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.MID_WALL_2x2));

        //build barracks (not on wall)
        Purchase.removeFirst(Units.TERRAN_BARRACKS);
        KetrocBot.purchaseQueue.add(3, new PurchaseStructure(Units.TERRAN_BARRACKS));

//        //set same scv for 1st depot and 1st barracks
//        Unit scv = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV).get(0).unit();
//        ((PurchaseStructure)KetrocBot.purchaseQueue.get(0)).setScv(scv);
//        ((PurchaseStructure)KetrocBot.purchaseQueue.get(2)).setScv(scv);

        //remove gasless command center from build order so gas & factory is up quicker
        if (KetrocBot.purchaseQueue.stream()
                .skip(5)
                .anyMatch(p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == Units.TERRAN_COMMAND_CENTER)) {
            Purchase.removeAll(Units.TERRAN_COMMAND_CENTER);
        }
    }

    public static void onStep() {
        //turn off worker rush code
        if (Strategy.WALL_OFF_IMMEDIATELY && Time.nowSeconds() > 300) {
            Strategy.WALL_OFF_IMMEDIATELY = false;
        }
    }
}
