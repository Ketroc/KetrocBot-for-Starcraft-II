package com.ketroc.managers;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.micro.BansheeCreepClear;
import com.ketroc.micro.RavenCreepClear;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.models.Ignored;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.Chat;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

public class CreepClearManager {
    public static boolean doCreepClear;

    public static void onGameStart() {
        if (PosConstants.opponentRace == Race.ZERG && Strategy.gamePlan != GamePlan.BC_RUSH && Strategy.gamePlan != GamePlan.BC_MACRO) {
            UpgradeManager.armoryUpgradeList.remove(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1);
            UpgradeManager.armoryUpgradeList.add(0, Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1);
        }
    }

    public static void onStep() {
        if (PosConstants.opponentRace == Race.ZERG && Strategy.gamePlan != GamePlan.BC_RUSH && Strategy.gamePlan != GamePlan.BC_MACRO) {
            doCreepClear = true;
            Strategy.MIN_BANSHEES = Math.max(1, Strategy.MIN_BANSHEES);
        }

        if (!doCreepClear) {
            return;
        }

        //free up banshee/raven for defense
        if (UnitUtils.isEnemyKillingMe()) {
            UnitMicroList.getUnitSubList(BansheeCreepClear.class)
                    .forEach(banshee -> {
                        banshee.tumorReport();
                        banshee.removeMe = true;
                    });
            return;
        }

        //otherwise populate unit micro objects for banshee and raven
        boolean needBanshee = UnitMicroList.numOfUnitClass(BansheeCreepClear.class) == 0;
        boolean needRaven = UnitMicroList.numOfUnitClass(RavenCreepClear.class) == 0;

        Unit newBanshee = UnitUtils.myUnitsOfType(Units.TERRAN_BANSHEE).stream()
                .filter(banshee -> UnitUtils.getHealthPercentage(banshee) > Strategy.RETREAT_HEALTH)
                .filter(banshee -> !Ignored.contains(banshee.getTag()))
                .findFirst().orElse(null);
        Unit newRaven = UnitUtils.myUnitsOfType(Units.TERRAN_RAVEN).stream()
                .filter(raven -> !Ignored.contains(raven.getTag()))
                .filter(raven -> UnitUtils.getHealthPercentage(raven) > Strategy.RETREAT_HEALTH + 10)
                .findFirst().orElse(null);

        //missing both
        if (needBanshee && needRaven && newBanshee != null && newRaven != null) {
            Chat.chat("Creep Clearing Squad Added");
            UnitMicroList.add(new BansheeCreepClear(newBanshee));
            UnitMicroList.add(new RavenCreepClear(newRaven));
            return;
        }

        //missing raven only
        if (needRaven && newRaven != null) {
            UnitMicroList.add(new RavenCreepClear(newRaven));
        }
    }
}
