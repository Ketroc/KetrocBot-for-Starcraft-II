package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

//TODO: cloak to set up snipe/emp??
public class GhostBasic extends Ghost {
    public static final Set<Units> EXCLUDE_UNITS = Set.of(
            Units.ZERG_ZERGLING, Units.ZERG_ZERGLING_BURROWED, Units.ZERG_DRONE,
            Units.ZERG_DRONE_BURROWED, Units.ZERG_CHANGELING_MARINE, Units.ZERG_CHANGELING_MARINE_SHIELD,
            Units.ZERG_BROODLING, Units.ZERG_LOCUS_TMP, Units.ZERG_LOCUS_TMP_FLYING, Units.ZERG_EGG,
            Units.ZERG_LARVA, Units.ZERG_OVERLORD_COCOON, Units.ZERG_TRANSPORT_OVERLORD_COCOON,
            Units.TERRAN_MARINE, Units.TERRAN_SCV
    );
    public static final Set<Units> TANKING_UNITS = Set.of(
            Units.TERRAN_HELLION_TANK, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_AUTO_TURRET,
            Units.TERRAN_BUNKER
    );
    public static final int EMP_RANGE = 10;
    public static long prevEmpFrame;
    public static long prevSnipeFrame;

    public GhostBasic(Unit unit, Point2d targetPos) {
        super(unit, targetPos);
    }

    public GhostBasic(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos);
    }

    @Override
    public void onStep() {
        if (!UnitUtils.canMove(unit.unit()) || isCasting()) {
            return;
        }

        if (canEmp()) {
            Point2d empPos = selectEmpPos();
            if (empPos != null) {
                emp(empPos);
                return;
            }
        }

        if (canSnipe()) {
            UnitInPool snipeTarget = selectSnipeTarget();
            if (snipeTarget != null) {
                snipe(snipeTarget.unit());
                System.out.println("snipeTarget.getTag() = " + snipeTarget.getTag());
                System.out.println("snipeTarget.unit().getDisplayType() = " + snipeTarget.unit().getDisplayType());
                System.out.println("UnitUtils.getCurHp(snipeTarget.unit()) = " + UnitUtils.getCurHp(snipeTarget.unit()));
                System.out.println("Ghost.prevSnipeFrame (before) = " + GhostBasic.prevSnipeFrame);
                System.out.println("numSnipesInProgress(snipeTarget.unit()) = " + numSnipesInProgress(snipeTarget.unit()));
                GhostBasic.prevSnipeFrame = Time.nowFrames();
                System.out.println("Time.nowFrames() = " + Time.nowFrames());
                System.out.println("Ghost.prevSnipeFrame (after) = " + GhostBasic.prevSnipeFrame);
                return;
            }
        }

        if (canCloak() && shouldCloak()) {
            cloak();
            return;
        }

        setTargetPos();
        setMicroPriority();
        super.onStep();
    }

    @Override
    //not safe in splash damage
    //not safe if within enemythreat && (cloaked && not detected)
    protected boolean isSafe(Point2d pos) {
        return !isInSplashDamage(pos) &&
                (!InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, pos) || (isCloaked() && !isDetected()));
    }

    @Override
    protected void setTargetPos() {
        //if heading to ground target, back up when a marauder/hellbat exists and is not in the lead
        if (ArmyManager.doOffense && ArmyManager.attackEitherPos != null &&
                ArmyManager.attackEitherPos.equals(ArmyManager.attackGroundPos)) {
            List<UnitInPool> tankingUnits = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MARAUDER ||
                    u.unit().getType() == Units.TERRAN_HELLION_TANK);
            if (!tankingUnits.isEmpty() &&
                    tankingUnits.stream()
                            .noneMatch(armyUnit -> UnitUtils.getDistance(armyUnit.unit(), ArmyManager.attackEitherPos) <
                                    UnitUtils.getDistance(unit.unit(), ArmyManager.attackEitherPos))) {
                targetPos = ArmyManager.retreatPos;
                return;
            }
        }
        super.setTargetPos();
    }

    //keep low health ghosts safe
    private void setMicroPriority() { //TODO: currently this will be bad vs tempests/tanks/lurkers/etc
        //stay back when low in health
        if (UnitUtils.getHealthPercentage(unit.unit()) < 20) {
            priority = MicroPriority.SURVIVAL;
            return;
        }

        //be aggressive when near a hellbat
        boolean isNearHellbat = !UnitUtils.getUnitsNearbyOfType(
                Alliance.SELF,
                Units.TERRAN_HELLION_TANK,
                unit.unit().getPosition().toPoint2d(),
                8).isEmpty();

        if (isNearHellbat || Bot.OBS.getFoodUsed() > 190) {
            priority = MicroPriority.DPS;
            return;
        }

        //be safe vs non-light units
        priority = MicroPriority.SURVIVAL;
    }

    //number of friendly ground units between this ghost and the enemy
    //hellbats count as 3 since they tank better than kiting units
    protected int numTankingUnits(Unit targetUnit) {
        float distGhostToTarget = UnitUtils.getDistance(unit.unit(), targetUnit);
        return Bot.OBS.getUnits(Alliance.SELF, myUnit -> !UnitUtils.isAir(myUnit.unit()) &&
                        UnitUtils.canAttack(myUnit.unit()) &&
                        UnitUtils.getDistance(myUnit.unit(), unit.unit()) < distGhostToTarget &&
                        UnitUtils.getDistance(myUnit.unit(), targetUnit) < distGhostToTarget)
                .stream()
                .mapToInt(myUnit -> TANKING_UNITS.contains(myUnit.unit().getType()) ? 3 : 1)
                .sum();
    }

    //if undetected and within 1-shot danger
    protected boolean shouldCloak() {
        return !isDetected() &&
                UnitUtils.getHealthPercentage(unit.unit()) < 99 &&
                (hasLockOnBuff() ||
                        InfluenceMaps.getValue(InfluenceMaps.pointDamageToGroundValue, unit.unit().getPosition().toPoint2d())
                                >= UnitUtils.getCurHp(unit.unit()));
    }

    //targets when target far away and tanking units in front of ghost
    protected boolean shouldSnipe(Unit target) {
        //ignore overlords unless only reachable by snipe
        if (target.getType() == Units.ZERG_OVERLORD) {
            return !Bot.OBS.isPathable(target.getPosition().toPoint2d()) &&
                    UnitUtils.getDistance(unit.unit(), target) > 7 &&
                    !Bot.OBS.isPathable(
                            Position.towards(target.getPosition().toPoint2d(), unit.unit().getPosition().toPoint2d(), 6)
                    );
        }

        //hallucination
        if (target.getHallucination().orElse(false)) {
            return false;
        }

        //not valid target
        Set<UnitAttribute> targetAttributes = Bot.OBS.getUnitTypeData(false).get(target.getType()).getAttributes();
        if (!targetAttributes.contains(UnitAttribute.BIOLOGICAL) ||
                targetAttributes.contains(UnitAttribute.STRUCTURE)) {
            return false;
        }

        //no additional snipes required
        int numSnipesToKill = (int)Math.ceil((UnitUtils.getCurHp(target)-10) / 170) - numSnipesInProgress(target); //note: -10hp to prevent sniping near dead targets. eg, double tapping queens (snipe: 170dmg, queen: 175hp)
        if (numSnipesToKill < 1) {
            return false;
        }

        //not detected, then snipe
        if (isCloaked() && !isDetected() && unit.unit().getEnergy().orElse(0f) > 53) {
            return true;
        }

        //out of enemy vision, then snipe
        if (!InfluenceMaps.getValue(InfluenceMaps.pointInEnemyVision, unit.unit().getPosition().toPoint2d())) {
            return true;
        }

        //target facing away (likely retreating), then snipe
        if (UnitUtils.isEnemyRetreating(target, unit.unit().getPosition().toPoint2d())) {
            return true;
        }

        //is there tanking units
        return numTankingUnits(target) >= 3;
    }

    public Point2d selectEmpPos() {
        return InfluenceMaps.getBestEmpPos(unit.unit());
    }

    public UnitInPool selectSnipeTarget() {
        return Bot.OBS.getUnits(Alliance.ENEMY, target -> UnitUtils.getDistance(unit.unit(), target.unit()) <= 10 &&
                        !UnitUtils.isSnapshot(target.unit()) &&
                        !EXCLUDE_UNITS.contains(target.unit().getType()))
                .stream()
                .filter(target -> shouldSnipe(target.unit()))
                .max(Comparator.comparing(target -> getTargetValue(target)))
                .orElse(null);
    }

    public float getTargetValue(UnitInPool target) {
        UnitTypeData unitData = Bot.OBS.getUnitTypeData(false).get(target.unit().getType());
        float costValue = unitData.getMineralCost().orElse(0) + unitData.getVespeneCost().orElse(0) * 1.5f;
        return costValue; //TODO: consider current hp??
    }

    public static int getEmpThreshold() {
        if (PosConstants.opponentRace == Race.ZERG) {
            return 200;
        }

        int numEmpsReady = UnitMicroList.getUnitSubList(GhostBasic.class).stream()
                .mapToInt(ghost -> (int) (ghost.unit.unit().getEnergy().orElse(0f) / 75))
                .sum();
        return (numEmpsReady > 4) ? 100 : 200;
    }
}
