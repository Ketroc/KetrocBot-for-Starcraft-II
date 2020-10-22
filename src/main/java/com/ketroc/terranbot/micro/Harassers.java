package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;
import com.ketroc.terranbot.strategies.Strategy;

public class Harassers {
    public static BansheeHarasser clockwiseBanshee;
    public static BansheeHarasser counterClockwiseBanshee;

    public static void onStep() {
        if (Strategy.DO_BANSHEE_HARASS) {
            removeHarassers();
            getNewHarassers();
            giveBansheeCommands();
        }
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
                Ignored.remove(clockwiseBanshee.banshee.getTag());
                clockwiseBanshee = null;
            }
        }
        if (counterClockwiseBanshee != null) {
            if (doRemoveBanshee(counterClockwiseBanshee)) {
                Ignored.remove(counterClockwiseBanshee.banshee.getTag());
                counterClockwiseBanshee = null;
            }
        }
    }

    private static boolean doRemoveBanshee(BansheeHarasser bansheeHarasser) {
        UnitInPool banshee = bansheeHarasser.banshee;
        return !banshee.isAlive() ||
                (bansheeHarasser.retreatForRepairs && UnitUtils.getDistance(banshee.unit(), LocationConstants.REPAIR_BAY) < 10);
    }

    private static void getNewHarassers() {
        if (clockwiseBanshee == null) {
            Tag newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                clockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), true);
                Ignored.add(new IgnoredUnit(newBansheeTag));
            }
        }
        else if (counterClockwiseBanshee == null) {
            Tag newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                counterClockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), false);
                Ignored.add(new IgnoredUnit(newBansheeTag));
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
