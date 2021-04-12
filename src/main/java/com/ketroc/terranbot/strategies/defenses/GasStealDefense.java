package com.ketroc.terranbot.strategies.defenses;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.function.Predicate;

public class GasStealDefense {

    public static boolean isDoubleGasStealThreatOver;

    //identify gas steal and immediately take 2nd gas to compensate
    public static void onStep() {
        //threat is over so ignore
        if (isDoubleGasStealThreatOver) {
            return;
        }

        //set threat to over, when my first gas is taken
        if (!Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.REFINERY_TYPE.contains(u.unit().getType())).isEmpty()) {
            isDoubleGasStealThreatOver = true;
            return;
        }

        Predicate<UnitInPool> isEnemyGasInMyMain = enemyGasStructure ->
                GameCache.baseList.get(0).getGases().stream()
                        .anyMatch(gas -> UnitUtils.getDistance(enemyGasStructure.unit(), gas.getNodePos()) < 1);
        //if gas is stolen
        if (UnitUtils.getEnemyUnitsOfTypes(UnitUtils.GAS_STRUCTURE_TYPES).stream().anyMatch(isEnemyGasInMyMain)) {
            isDoubleGasStealThreatOver = true;
            adjustBuildOrder();
        }
    }

    private static void adjustBuildOrder() {
        //move first refinery to top of the queue
        Purchase.removeFirst(Units.TERRAN_REFINERY);
        KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_REFINERY));

        //remove queued low-ground cc
        Purchase.removeAll(Units.TERRAN_COMMAND_CENTER);
    }
}
