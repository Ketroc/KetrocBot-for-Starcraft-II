package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.gamestate.EnemyCache;
import com.ketroc.gamestate.GameCache;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.Base;
import com.ketroc.models.Cost;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

public class BattlecruiserHarass extends Battlecruiser {
    Point2d curEnemyBasePos;
    boolean doJumpIn = true;

    public BattlecruiserHarass(Unit unit) {
        super(unit, ArmyManager.attackEitherPos, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
    }

    @Override
    public void onStep() {
        //TODO: map deadspace in InfluenceMaps
        if (isCasting()) {
            return;
        }

        if (curEnemyBasePos == null) {
            Point2d enemyBasePos = selectTargetBase();
            if (enemyBasePos != null) {
                curEnemyBasePos = enemyBasePos;
            }
        }

        // YAMATO
        if (isYamatoAvailable()) {
            UnitInPool newYamatoAttack = selectYamatoAttack();
            if (newYamatoAttack != null) {
                yamato(newYamatoAttack.unit());
                return;
            }
        }

        // SELECT ATTACK TARGET
        if (isAttackStep()) {
            //select a target
            UnitInPool newTargetAttack = selectTargetAttack();
            if (newTargetAttack != null) {
                attackTarget(newTargetAttack.unit());

                //scan when only burrowed targets remain
                if (UnitUtils.isBurrowed(newTargetAttack.unit()) && UnitUtils.canScan()) {
                    if (UnitUtils.getEnemyTargetsNear(unit.unit(), 15).stream()
                            .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                            .allMatch(target -> UnitUtils.isBurrowed(target.unit()))) {
                        UnitUtils.scan(newTargetAttack.unit().getPosition().toPoint2d());
                    }
                }
            }
            prevAttackFrame = Time.nowFrames();
            return;
        }

        //SET POSITIONS
        curTargetMoveTo = selectTargetMoveTo();
        if (doRepair()) {
            targetPos = PosConstants.REPAIR_BAY;
        }
        else if (curTargetMoveTo != null) {
            //lowest threat pos within 3 range of BC and 6 range of target
            targetPos = findSafestPos(curTargetMoveTo.unit().getPosition().toPoint2d());
        }
        else if (curEnemyBasePos != null) {
            targetPos = curEnemyBasePos;
        }
        else {
            targetPos = ArmyManager.attackEitherPos;
        }

        // DODGE SPLASH
        if (isInSplashDamage(unit.unit().getPosition().toPoint2d())) {
            detour();
            return;
        }

        // JUMP TO FAR AWAY PLACES
        if (isJumpAvailable() &&
                UnitUtils.getDistance(unit.unit(), targetPos) > 100 &&
                (doJumpIn || targetPos.equals(PosConstants.REPAIR_BAY))) {
            if (safeJump(targetPos.add(1, 1))) {
                return;
            }
        }

        // MOVE BC SAFELY WHEN LOW HP
        if (UnitUtils.getCurHp(unit.unit()) < 225 && !isSafe()) {
            if (isJumpAvailable()) {
                jump(PosConstants.REPAIR_BAY);
            }
            else {
                detour();
            }
            return;
        }

        //MOVE BC TODO: move to lowest damage pos that's still in attack range
        move(targetPos);
    }

    private Point2d findSafestPos(Point2d targetPos) {
        //check 12x12 around bc pos
        int bcX = InfluenceMaps.toMapCoord(unit.unit().getPosition().getX());
        int bcY = InfluenceMaps.toMapCoord(unit.unit().getPosition().getY());

        int lowestThreatVal = 9999;
        Point2d lowestThreatPos = targetPos;
        for (int x=bcX-6; x<=bcX+6; x++) {
            for (int y=bcY-6; y<=bcY+6; y++) {
                Point2d curPos = Point2d.of(x/2, y/2);
                if (Position.isOutOfBounds(curPos) || curPos.distance(targetPos) > 5.5f) {
                    continue;
                }
                //if lowest threat pos
                int curThreatVal = InfluenceMaps.pointThreatToAirValue[x][y];
                if (curThreatVal < lowestThreatVal) {
                    lowestThreatPos = curPos;
                    lowestThreatVal = curThreatVal;
                }
            }
        }
        //return Position.towards(unit.unit().getPosition().toPoint2d(), lowestThreatPos, 3);
        return lowestThreatPos;
    }


    //repair when low hp and repair is possible at home
    private boolean doRepair() {
        float curHp = UnitUtils.getCurHp(unit.unit());
        return (curHp < 175 || (curHp < unit.unit().getHealthMax().orElse(550f) &&
                                UnitUtils.getDistance(unit.unit(), PosConstants.REPAIR_BAY) < 3)) &&
                UnitUtils.canRepair(unit.unit());
    }

    @Override
    public boolean[][] getThreatMap() {
        return InfluenceMaps.pointThreatToAirPlusBuffer;
    }

    @Override
    public void onArrival() {

    }

    public UnitInPool selectTargetAttack() {
        return selectTarget(6);
    }

    public UnitInPool selectTargetMoveTo() {
        return selectTarget(15);
    }
    public UnitInPool selectTarget(float range) {
        List<UnitInPool> allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), range).stream()
                .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                .collect(Collectors.toList());
        if (allTargets.isEmpty()) {
            // SET NEW ENEMY BASE
            Point2d newTargetBase = selectTargetBase();
            if (newTargetBase != null) {
                curEnemyBasePos = newTargetBase;
            }
            return null;
        }

        // HYDRAS
        UnitInPool bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_HYDRALISK ||
                        target.unit().getType() == Units.ZERG_HYDRALISK_BURROWED)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(90f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // QUEENS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_QUEEN ||
                        target.unit().getType() == Units.ZERG_QUEEN_BURROWED)
                .max(Comparator.comparing(target ->
                        (target.unit().getHealthMax().orElse(175f) - target.unit().getHealth().orElse(175f)) +
                                ((int)(target.unit().getEnergy().orElse(0f)/50))*75))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // CORRUPTORS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_CORRUPTOR)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(200f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // SPIRE or HYDRA DEN
        if (range > 7) { //not attack targetting
            // SPIRE or HYDRA DEN
            UnitInPool aaTechStructure = Bot.OBS.getUnits(Alliance.ENEMY, target ->
                    target.unit().getType() == Units.ZERG_SPIRE ||
                    target.unit().getType() == Units.ZERG_HYDRALISK_DEN).stream()
                    .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())))
                    .orElse(null);
            if (aaTechStructure != null) {
                return aaTechStructure;
            }
        }
        else {
            bestTarget = allTargets.stream()
                    .filter(target -> target.unit().getType() == Units.ZERG_SPIRE ||
                            target.unit().getType() == Units.ZERG_HYDRALISK_DEN)
                    .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f) +
                            (target.unit().getType() == Units.ZERG_HYDRALISK_DEN ? 9999 : 0)))
                    .orElse(null);
            if (bestTarget != null) {
                return bestTarget;
            }
        }

        // MUTAS / SPORES
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.canAttackAir(target.unit()))
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // DRONES
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_DRONE ||
                        target.unit().getType() == Units.ZERG_DRONE_BURROWED)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // OVERLORDS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_OVERLORD ||
                        target.unit().getType() == Units.ZERG_OVERLORD_TRANSPORT)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // ARMY UNITS
        bestTarget = allTargets.stream()
                .filter(target -> Bot.OBS.getUnitTypeData(false).get(target.unit().getType()).getFoodRequired().orElse(0f) > 0)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        return allTargets.get(0);
    }

    protected UnitInPool selectYamatoAttack() {
        List<UnitInPool> allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), 10);
        return allTargets.stream()
                .filter(target -> UnitUtils.getCurHp(target.unit()) > 100)
                .filter(target -> isYamatoWorthy((Units)target.unit().getType()))
                .max(Comparator.comparing(target -> target.unit().getHealth().orElse(0f)))
                .orElse(null);
    }

    private Point2d selectTargetBase() {
        //pick an enemy base that isn't currently visible
        List<Base> reversedBaseList = new ArrayList<>(GameCache.baseList);
        Collections.reverse(reversedBaseList);
        Point2d enemyBasePos = reversedBaseList.stream()
                .filter(base -> base.isEnemyBase)
                .filter(base -> Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE)
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
        if (enemyBasePos != null) {
            return enemyBasePos;
        }

        //otherwise pick 1st enemy base
        enemyBasePos = reversedBaseList.stream()
                .filter(base -> base.isEnemyBase)
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
        if (enemyBasePos != null) {
            return enemyBasePos;
        }

        return null;
    }

    //TODO: all races
    protected boolean isYamatoWorthy(Units unitType) {
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.ZERG_CORRUPTOR)) {
            return unitType == Units.ZERG_CORRUPTOR;
        }

        switch (unitType) {
            case ZERG_QUEEN:
            case ZERG_SPORE_CRAWLER:
            case ZERG_CORRUPTOR:
            case ZERG_MUTALISK:
            case ZERG_SPIRE:
            case ZERG_HYDRALISK_DEN:
                return true;
        }
        return false;
    }

}
