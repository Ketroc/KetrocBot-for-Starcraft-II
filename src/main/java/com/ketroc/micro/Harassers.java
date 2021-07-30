package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Ignored;
import com.ketroc.models.IgnoredUnit;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.Chat;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Harassers {
    public static BansheeHarasser clockwiseBanshee;
    public static BansheeHarasser counterClockwiseBanshee;
    public static List<HellionHarasser> clockwiseHellions = new ArrayList<>();
    public static List<HellionHarasser> counterClockwiseHellions = new ArrayList<>();
    public static int consecutiveBadHarass; //# of times in a row that banshee harass didn't get much done

    public static void onStep() {
        removeHarassers();
        if (Strategy.DO_BANSHEE_HARASS) {
            if (doEndBansheeHarass() && clockwiseBanshee == null && counterClockwiseBanshee == null) {
                Chat.tag("BANSHEE_HARASS_ENDED");
                Strategy.DO_BANSHEE_HARASS = false;
                return;
            }
        }
        getNewHarassers();
        giveHarassersCommands();
    }

    private static void giveHarassersCommands() {
        if (Strategy.DO_BANSHEE_HARASS) {
            if (clockwiseBanshee != null) {
                clockwiseBanshee.bansheeMicro();
            }
            if (counterClockwiseBanshee != null) {
                counterClockwiseBanshee.bansheeMicro();
            }
        }
        clockwiseHellions.forEach(hellionHarasser -> hellionHarasser.onStep());
        counterClockwiseHellions.forEach(hellionHarasser -> hellionHarasser.onStep());
    }

    private static void removeHarassers() {
        if (Strategy.DO_BANSHEE_HARASS) {
            if (clockwiseBanshee != null) {
                if (doRemoveBanshee(clockwiseBanshee)) {
                    if (!clockwiseBanshee.banshee.isAlive() && clockwiseBanshee.isWithinPhoenixRange()) {
                        consecutiveBadHarass = 2;
                    }
                    clockwiseBanshee.printKillReport();
                    Ignored.remove(clockwiseBanshee.banshee.getTag());
                    clockwiseBanshee = null;
                }
            }
            if (counterClockwiseBanshee != null) {
                if (doRemoveBanshee(counterClockwiseBanshee)) {
                    counterClockwiseBanshee.printKillReport();
                    Ignored.remove(counterClockwiseBanshee.banshee.getTag());
                    counterClockwiseBanshee = null;
                }
            }
        }
        clockwiseHellions.removeIf(hellionHarasser -> hellionHarasser.removeMe);
        counterClockwiseHellions.removeIf(hellionHarasser -> hellionHarasser.removeMe);
    }

    private static boolean doRemoveBanshee(BansheeHarasser bansheeHarasser) {
        UnitInPool banshee = bansheeHarasser.banshee;
        return doEndBansheeHarass() || !banshee.isAlive() ||
                (bansheeHarasser.retreatForRepairs && UnitUtils.getDistance(banshee.unit(), LocationConstants.REPAIR_BAY) < 10);
    }

    private static boolean doEndBansheeHarass() {
        return consecutiveBadHarass >= 2;
    }

    private static void getNewHarassers() {
        if (Strategy.DO_BANSHEE_HARASS) {
            if (clockwiseBanshee == null) {
                Tag newBansheeTag = getNewBanshee();
                if (newBansheeTag != null) {
                    clockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), true);
                    Ignored.add(new IgnoredUnit(newBansheeTag));
                    GameCache.bansheeList.removeIf(banshee -> banshee.getTag().equals(newBansheeTag));
                }
            }
            else if (counterClockwiseBanshee == null) {
                Tag newBansheeTag = getNewBanshee();
                if (newBansheeTag != null) {
                    counterClockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), false);
                    Ignored.add(new IgnoredUnit(newBansheeTag));
                    GameCache.bansheeList.removeIf(banshee -> banshee.getTag().equals(newBansheeTag));
                }
            }
        }
        if (ArmyManager.doOffense) {
            int extraHellions = UnitUtils.getMyUnitsOfType(UnitUtils.HELLION_TYPE).size() -
                    UnitUtils.getEnemyUnitsOfType(Units.ZERG_ZERGLING).size() / 4;
            for (int i=0; i<extraHellions; i++) {
                addHellion();
            }
        }
        else {
            releaseAllHellions();
        }
    }

    private static void releaseAllHellions() {
        clockwiseHellions.forEach(hellionHarasser -> hellionHarasser.removeMe = true);
        counterClockwiseHellions.forEach(hellionHarasser -> hellionHarasser.removeMe = true);
    }

    private static Tag getNewBanshee() {
        Tag bansheeTag = null;
        if (!ArmyManager.isEnemyInMain() && !ArmyManager.isEnemyInNatural()) { //defend with banshees if required
            bansheeTag = GameCache.bansheeList.stream()
                    .filter(banshee -> UnitUtils.getHealthPercentage(banshee) >= 99)
                    .filter(banshee -> banshee.getCloakState().orElse(CloakState.CLOAKED_ALLIED) == CloakState.NOT_CLOAKED)
                    .filter(banshee -> banshee.getEnergy().orElse(0f) >= 50)
                    .map(Unit::getTag)
                    .findFirst()
                    .orElse(null);
        }
        return bansheeTag;
    }

    public static void onEnemyUnitDeath(Unit unit) {
        if (clockwiseBanshee != null) {
            clockwiseBanshee.onEnemyUnitDeath(unit);
        }
        if (counterClockwiseBanshee != null) {
            counterClockwiseBanshee.onEnemyUnitDeath(unit);
        }
    }

    public static void addHellion() {
        Optional<Unit> availableHellion = UnitUtils.getMyUnitsOfType(Units.TERRAN_HELLION).stream().findAny();
        availableHellion.ifPresent(hellion -> {
                if (clockwiseHellions.size() > counterClockwiseHellions.size()) {
                    counterClockwiseHellions.add(new HellionHarasser(hellion, false));
                }
                else {
                    clockwiseHellions.add(new HellionHarasser(hellion, true));
                }
                Ignored.add(new IgnoredUnit(hellion.getTag()));
        });
    }
}
