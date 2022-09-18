package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Ignored;
import com.ketroc.models.IgnoredUnit;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.Chat;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

import java.util.Optional;

public class Harassers {
    public static int NUM_BAD_HARASS = 2;

    public static BansheeHarasser clockwiseBanshee;
    public static BansheeHarasser counterClockwiseBanshee;
    //public static List<HellionHarasser> clockwiseHellions = new ArrayList<>();
    //public static List<HellionHarasser> counterClockwiseHellions = new ArrayList<>();
    public static int consecutiveBadHarass; //# of times in a row that banshee harass didn't get much done

    public static void onStep() {
        removeHarassers();
        if (Strategy.DO_BANSHEE_HARASS) {
            if (doEndBansheeHarass() && clockwiseBanshee == null && counterClockwiseBanshee == null) {
                Chat.tag("BANSHEE_HARASS_ENDED");
                Strategy.DO_BANSHEE_HARASS = false;
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
    }

    private static void removeHarassers() {
        if (Strategy.DO_BANSHEE_HARASS) {
            if (clockwiseBanshee != null) {
                if (doRemoveBanshee(clockwiseBanshee)) {
                    if (!clockwiseBanshee.banshee.isAlive() && clockwiseBanshee.isWithinPhoenixRange()) {
                        consecutiveBadHarass = 2;
                    }
                    if (!doEndBansheeHarass()) {
                        clockwiseBanshee.printKillReport();
                    }
                    Ignored.remove(clockwiseBanshee.banshee.getTag());
                    clockwiseBanshee = null;
                }
            }
            if (counterClockwiseBanshee != null) {
                if (doRemoveBanshee(counterClockwiseBanshee)) {
                    if (!doEndBansheeHarass()) {
                        counterClockwiseBanshee.printKillReport();
                    }
                    Ignored.remove(counterClockwiseBanshee.banshee.getTag());
                    counterClockwiseBanshee = null;
                }
            }
        }
    }

    private static boolean doRemoveBanshee(BansheeHarasser bansheeHarasser) {
        UnitInPool banshee = bansheeHarasser.banshee;
        return doEndBansheeHarass() || !banshee.isAlive() ||
                (bansheeHarasser.retreatForRepairs && UnitUtils.getDistance(banshee.unit(), PosConstants.REPAIR_BAY) < 10);
    }

    private static boolean doEndBansheeHarass() {
        return consecutiveBadHarass >= NUM_BAD_HARASS;
    }

    private static void getNewHarassers() {
        if (Strategy.DO_BANSHEE_HARASS) {
            if (doClockwise()) {
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
        if (Strategy.gamePlan == GamePlan.BC_RUSH) {
            if (!UnitUtils.myUnitsOfType(UnitUtils.HELLION_TYPE).isEmpty()) {
                addHellion();
            }
        }
        else if (ArmyManager.doOffense && Strategy.gamePlan != GamePlan.MECH_ALL_IN) {
            int numHellions = UnitUtils.myUnitsOfType(UnitUtils.HELLION_TYPE).size();
            if (numHellions == 0) {
                return;
            }
            switch (PosConstants.opponentRace) {
                case ZERG:
                    if (numHellions > UnitUtils.getEnemyUnitsOfType(Units.ZERG_ZERGLING).size() / 4 + 2) {
                        addHellion();
                    }
                    break;
                case PROTOSS:
                    if (numHellions > UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_ADEPT).size() + 2) {
                        addHellion();
                    }
                    break;
                case TERRAN:
                    addHellion();
                    break;
            }
        }
        else {
            releaseAllHellions();
        }
    }

    //do clockwise first if it's the longer path
    private static boolean doClockwise() {
        return clockwiseBanshee == null &&
                (counterClockwiseBanshee != null || PosConstants.clockBasePositions.size() >= PosConstants.counterClockBasePositions.size());
    }

    private static void releaseAllHellions() {
        UnitMicroList.getUnitSubList(HellionHarasser.class).forEach(hellionHarasser -> hellionHarasser.removeMe = true);
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
        Optional<Unit> availableHellion = UnitUtils.myUnitsOfType(Units.TERRAN_HELLION).stream().findAny();
        availableHellion.ifPresent(hellion -> {
            boolean isClockWiseNeeded = numHellions(true) < numHellions(false);
            UnitMicroList.add(new HellionHarasser(hellion, isClockWiseNeeded));
            ActionHelper.unitCommand(hellion, Abilities.STOP, false);
        });
    }

    public static int numHellions(boolean isClockwise) {
        return (int)UnitMicroList.getUnitSubList(HellionHarasser.class).stream()
                .filter(hellionHarasser -> hellionHarasser.isBaseTravelClockwise() == isClockwise)
                .count();
    }
}
