package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

public class Harassers {
    public static BansheeHarasser clockwiseBanshee;
    public static BansheeHarasser counterClockwiseBanshee;

    public static void onStep() {
        removeHarassers();
        getNewHarassers();
        giveBansheeCommands();
    }

    private static void giveBansheeCommands() {
        if (clockwiseBanshee != null) {
            clockwiseBanshee.bansheeMicro();
        }
        if (counterClockwiseBanshee != null) {
            counterClockwiseBanshee.bansheeMicro();
        }

    }

    private static void removeHarassers() {
        if (clockwiseBanshee != null) {
            if (doRemoveBanshee(clockwiseBanshee)) {
                IgnoredUnit.ignoredUnits.removeIf(ignoredUnit -> ignoredUnit.unitTag.equals(clockwiseBanshee.banshee.getTag()));
                clockwiseBanshee = null;
            }
        }
        if (counterClockwiseBanshee != null) {
            if (doRemoveBanshee(counterClockwiseBanshee)) {
                IgnoredUnit.ignoredUnits.removeIf(ignoredUnit -> ignoredUnit.unitTag.equals(counterClockwiseBanshee.banshee.getTag()));
                counterClockwiseBanshee = null;
            }
        }
    }

    private static boolean doRemoveBanshee(BansheeHarasser bansheeHarasser) {
        UnitInPool banshee = bansheeHarasser.banshee;
        return !banshee.isAlive(); // || banshee.unit().getHealth().orElse(0f) <= 40; commented out cuz low health banshees have no micro home
    }

    private static void getNewHarassers() {
        if (clockwiseBanshee == null) {
            Tag newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                clockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), true);
                IgnoredUnit.ignoredUnits.add(new IgnoredUnit(newBansheeTag));
            }
        }
        else if (counterClockwiseBanshee == null) {
            Tag newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                counterClockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), false);
                IgnoredUnit.ignoredUnits.add(new IgnoredUnit(newBansheeTag));
            }
        }
    }

    private static Tag getNewBanshee() { //TODO: complete stub
        return UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BANSHEE).stream()
                .filter(banshee -> UnitUtils.getHealthPercentage(banshee) >= 99)
                .filter(banshee -> banshee.getCloakState().orElse(CloakState.CLOAKED_ALLIED) == CloakState.NOT_CLOAKED)
                .filter(banshee -> banshee.getEnergy().orElse(0f) >= 50)
                .map(Unit::getTag)
                .findFirst()
                .orElse(null);
    }
}
