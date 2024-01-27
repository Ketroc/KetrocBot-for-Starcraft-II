package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
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
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

//TODO: build this entire class
public class BattlecruiserDefense extends Battlecruiser {
    public BattlecruiserDefense(Unit unit) {
        super(unit, ArmyManager.attackEitherPos, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
    }

    @Override
    public void onStep() {
        if (!isAlive()) {
            onDeath();
            return;
        }

        //TODO: map deadspace in InfluenceMaps
        if (isCasting()) {
            return;
        }

        // SELECT ATTACK TARGET
        if (isAttackStep()) {
            //select a target
            UnitInPool newTargetAttack = selectTargetAttack();
            if (newTargetAttack != null) {
                attackTarget(newTargetAttack.unit());

                //scan when only burrowed targets remain
                if (UnitUtils.isBurrowed(newTargetAttack.unit()) &&
                        UnitUtils.canScan() &&
                        !UnitUtils.isInMyDetection(newTargetAttack.unit().getPosition().toPoint2d()) &&
                        UnitUtils.getEnemyTargetsNear(unit.unit(), 15).stream()
                            .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                            .allMatch(target -> UnitUtils.isBurrowed(target.unit()) &&
                                    !UnitUtils.isInMyDetection(target.unit().getPosition().toPoint2d()))) {
                    UnitUtils.scan(newTargetAttack.unit().getPosition().toPoint2d());
                }
            }
            prevAttackFrame = Time.nowFrames();
            return;
        }

        // YAMATO
        if (isYamatoAvailable()) {
            UnitInPool newYamatoAttack = selectYamatoAttack();
            if (newYamatoAttack != null) {
                yamato(newYamatoAttack.unit());
                return;
            }
        }

        // SET TARGET POS
        if (doRepair()) {
            targetPos = PosConstants.REPAIR_BAY;
        }
        else if (posMoveTo != null) {
            //lowest threat pos within 3 range of BC and 7 range of target
            targetPos = findSafestPos(posMoveTo);
        }
        else if (ArmyManager.attackEitherPos != null) {
            targetPos = ArmyManager.attackEitherPos;
        }
        else {
            setFinishHimTarget();
        }

        // DODGE SPLASH
        if (isInSplashDamage(unit.unit().getPosition().toPoint2d())) {
            detour();
            return;
        }

        // JUMP TO FAR AWAY PLACES
        if (isJumpAvailable() &&
                UnitUtils.getDistance(unit.unit(), targetPos) > 100 &&
                targetPos.equals(PosConstants.REPAIR_BAY)) {
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
        //dodge enemy dps while still going towards the target

        //when in attack range of target, move the safest position while still in range to attack target


        //check 12x12 around bc pos
        int bcX = InfluenceMaps.toMapCoord(unit.unit().getPosition().getX());
        int bcY = InfluenceMaps.toMapCoord(unit.unit().getPosition().getY());

        int lowestThreatVal = 9999;
        Point2d lowestThreatPos = targetPos;
        for (int x=bcX-6; x<=bcX+6; x++) {
            for (int y=bcY-6; y<=bcY+6; y++) {
                Point2d curPos = Point2d.of(x/2, y/2);
                if (Position.isOutOfBounds(curPos) || curPos.distance(targetPos) > 6.5f) {
                    continue;
                }
                //if lowest threat pos
                int curThreatVal = InfluenceMaps.pointThreatToAirPlusBufferValue[x][y];
                if (curThreatVal < Math.max(lowestThreatVal, 4) ||
                        (curThreatVal == lowestThreatVal && curPos.distance(targetPos) < lowestThreatPos.distance(targetPos))) {
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
        return UnitUtils.canRepair(unit.unit()) &&
                (needRepairs() || (doStayInRepairBay() && inRepairBay()));
    }

    @Override
    public boolean[][] getThreatMap() {
        return InfluenceMaps.pointThreatToAirPlusBuffer;
    }

    @Override
    public void onArrival() {

    }

    public UnitInPool selectTargetAttack() {
        List<UnitInPool> allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), ATTACK_RANGE).stream()
                .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                .collect(Collectors.toList());
        if (allTargets.isEmpty()) {
            return null;
        }
        switch (PosConstants.opponentRace) {
            case ZERG:
                return getZergAttackTarget(allTargets);
            default: //case PROTOSS:
                return getProtossAttackTarget(allTargets);
        }
    }

    private UnitInPool getZergAttackTarget(List<UnitInPool> allTargets) {
        if (allTargets.isEmpty()) {
            return null;
        }

        // DRONES
        UnitInPool bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_DRONE ||
                        target.unit().getType() == Units.ZERG_DRONE_BURROWED)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // HYDRAS
        bestTarget = allTargets.stream()
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

        // MUTAS / SPORES
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.canAttackAir(target.unit()))
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // SPIRE
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_SPIRE)
                .findFirst()
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

    private UnitInPool getProtossAttackTarget(List<UnitInPool> allTargets) {
        if (allTargets.isEmpty()) {
            return null;
        }

        // PROBES
        UnitInPool bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_PROBE)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // VOID RAYS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_VOIDRAY)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // TEMPESTS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_TEMPEST)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // STALKERS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_STALKER)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // ALL OTHER AIR THREATS
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.canAttackAir(target.unit()))
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // STARGATE OR CYBER
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_STARGATE ||
                        target.unit().getType() == Units.PROTOSS_CYBERNETICS_CORE)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // PYLONS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_PYLON)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // NEXUS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_NEXUS)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        // ARMY UNITS
        bestTarget = allTargets.stream()
                .filter(target -> Bot.OBS.getUnitTypeData(false).get(target.unit().getType()).getFoodRequired().orElse(0f) > 0)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget;
        }

        return allTargets.get(0);
    }

    protected UnitInPool selectYamatoAttack() {
        List<UnitInPool> allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), 11.5f).stream()
                .filter(enemy -> UnitUtils.isEnemyWithinMyVisionRange(enemy.unit()))
                .collect(Collectors.toList());
        return allTargets.stream()
                .filter(target -> UnitUtils.getCurHp(target.unit()) > 100 || target.unit().getType() == Units.PROTOSS_TEMPEST)
                .filter(target -> isYamatoWorthy(target.unit()))
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
    protected boolean isYamatoWorthy(Unit enemyUnit) {
        Units enemyType = (Units)enemyUnit.getType();
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.ZERG_CORRUPTOR)) {
            return enemyType == Units.ZERG_CORRUPTOR;
        }
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.PROTOSS_TEMPEST)) {
            return enemyType == Units.PROTOSS_TEMPEST;
        }
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.PROTOSS_VOIDRAY)) {
            return enemyType == Units.PROTOSS_VOIDRAY;
        }

        switch (enemyType) {
            case ZERG_QUEEN: case ZERG_CORRUPTOR:
            case ZERG_MUTALISK: //case ZERG_SPIRE: case ZERG_HYDRALISK_DEN:
            case PROTOSS_VOIDRAY: case PROTOSS_TEMPEST: case PROTOSS_CARRIER:
            case PROTOSS_STALKER: case PROTOSS_MOTHERSHIP:
            case PROTOSS_ARCHON:
                return true;
            case ZERG_SPORE_CRAWLER: case ZERG_SPORE_CRAWLER_UPROOTED:
                return posMoveTo != null && UnitUtils.getDistance(enemyUnit, posMoveTo) < 3.5f; // yamato spores that are protecting BC's target
            case PROTOSS_PHOTON_CANNON:
                if (!enemyUnit.getPowered().orElse(true)) {
                    return false;
                }
                return posMoveTo != null && UnitUtils.getDistance(enemyUnit, posMoveTo) < 3.5f; // yamato cannons that are protecting BC's target
        }
        return false;
    }

    protected boolean doStayInRepairBay() {
        return unit.unit().getHealth().orElse(550f) < unit.unit().getHealthMax().orElse(550f) ||
                jumpCooldownRemaining() > 672; //30sec until jump available
    }

    @Override
    protected boolean needRepairs() {
        int minHpToRepair = UnitUtils.isAnyBaseUnderAttack() ? 200 : 530;
        return unit.unit().getHealth().orElse(400f) < minHpToRepair;
    }
}
