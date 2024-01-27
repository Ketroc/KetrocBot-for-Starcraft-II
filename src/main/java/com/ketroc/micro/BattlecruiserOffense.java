package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
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
import com.ketroc.utils.*;

import java.util.*;
import java.util.stream.Collectors;

public class BattlecruiserOffense extends Battlecruiser {
    public static boolean doJumpIn = false;

    Point2d curEnemyBasePos;

    public BattlecruiserOffense(Unit unit) {
        super(unit, ArmyManager.attackEitherPos, MicroPriority.SURVIVAL);
        doDetourAroundEnemy = true;
    }

    @Override
    public void onStep() {
        if (!isAlive() ||
                targetPos == PosConstants.REPAIR_BAY ||
                !MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP)) {
            removeMe = true;
            return;
        }

        //TODO: map deadspace in InfluenceMaps
        if (isCasting()) {
            return;
        }

        Point2d enemyBasePos = selectTargetBase();
        if (enemyBasePos != null) {
            curEnemyBasePos = ArmyManager.attackEitherPos; //TODO fix this
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

        //SET POSITIONS
        posMoveTo = selectTargetMoveTo();

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
        else if (curEnemyBasePos != null) {
            targetPos = curEnemyBasePos;
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

    public Point2d selectTargetMoveTo() {
        List<UnitInPool> allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), 15).stream()
                .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                .collect(Collectors.toList());
        if (allTargets.isEmpty()) {
            // SET NEW ENEMY BASE
            Point2d newTargetBase = selectTargetBase();
            if (newTargetBase != null) {
                curEnemyBasePos = newTargetBase;
                return null;
            }
            allTargets = UnitUtils.getEnemyTargetsNear(unit.unit(), 999).stream()
                    .filter(target -> !UnitUtils.IGNORED_TARGETS.contains(target.unit().getType()))
                    .collect(Collectors.toList());
            if (allTargets.isEmpty()) {
                return null;
            }
        }
        switch (PosConstants.opponentRace) {
            case ZERG:
                return getZergMoveToTarget(allTargets);
            default: //case PROTOSS:
                return getProtossMoveToTarget(allTargets);
        }
    }

    private Point2d getZergMoveToTarget(List<UnitInPool> allTargets) {
        UnitInPool bestTarget;

        // QUEENS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_QUEEN ||
                        target.unit().getType() == Units.ZERG_QUEEN_BURROWED)
                .max(Comparator.comparing(target ->
                        (target.unit().getHealthMax().orElse(175f) - target.unit().getHealth().orElse(175f)) +
                                ((int)(target.unit().getEnergy().orElse(0f)/50))*75))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // SPIRE
        UnitInPool aaTechStructure = Bot.OBS.getUnits(Alliance.ENEMY, target -> target.unit().getType() == Units.ZERG_SPIRE).stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())))
                .orElse(null);
        if (aaTechStructure != null) {
            return aaTechStructure.unit().getPosition().toPoint2d();
        }

        // OVERLORDS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_OVERLORD ||
                        target.unit().getType() == Units.ZERG_OVERLORD_TRANSPORT)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // HATCHERIES
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.COMMAND_STRUCTURE_TYPE.contains(target.unit().getType()))
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // STRUCTURES (NOT-SPORES)
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.isStructure(target.unit().getType()) && target.unit().getType() != Units.ZERG_SPORE_CRAWLER)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(9999f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // CONTINUE TO ENEMY BASE
        if (curEnemyBasePos != null && UnitUtils.getDistance(unit.unit(), curEnemyBasePos) > 1) {
            return curEnemyBasePos;
        }

        // NEXT STRUCTURE
        Optional<UnitInPool> closestEnemyStructure = Bot.OBS.getUnits(Alliance.ENEMY, target -> UnitUtils.isStructure(target.unit().getType())).stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())));
        if (closestEnemyStructure.isPresent()) {
            return closestEnemyStructure.get().unit().getPosition().toPoint2d();
        }

        // ANY ENEMY UNIT IN RANGE
        return allTargets.stream()
                .filter(target -> target.unit().getHealth().orElse(0f) > 0)
                .min(Comparator.comparing(target -> target.unit().getHealth().get()))
                .map(target -> target.unit().getPosition().toPoint2d())
                .orElse(null);
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

        // INFESTORS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_INFESTOR ||
                        target.unit().getType() == Units.ZERG_INFESTOR_BURROWED)
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

        // VIPERS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_VIPER)
                .min(Comparator.comparing(target -> target.unit().getHealth().orElse(150f)))
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

        // OVERLORDS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.ZERG_OVERLORD ||
                        target.unit().getType() == Units.ZERG_OVERLORD_TRANSPORT)
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

    private Point2d getProtossMoveToTarget(List<UnitInPool> allTargets) {
        UnitInPool bestTarget;

        // OVERCHARGED BATTERY
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_SHIELD_BATTERY && target.unit().getBuffs().contains(Buffs.BATTERY_OVERCHARGE))
                .findFirst()
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // NOT DRY BATTERY
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_SHIELD_BATTERY && target.unit().getEnergy().orElse(0f) > 50f)
                .max(Comparator.comparing(target -> target.unit().getEnergy().orElse(200f)))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // STARGATE
        UnitInPool aaTechStructure = Bot.OBS.getUnits(Alliance.ENEMY, target -> target.unit().getType() == Units.PROTOSS_STARGATE).stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())))
                .orElse(null);
        if (aaTechStructure != null) {
            return aaTechStructure.unit().getPosition().toPoint2d();
        }

        // CYBER CORE
        aaTechStructure = Bot.OBS.getUnits(Alliance.ENEMY, target -> target.unit().getType() == Units.PROTOSS_CYBERNETICS_CORE).stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())))
                .orElse(null);
        if (aaTechStructure != null) {
            return aaTechStructure.unit().getPosition().toPoint2d();
        }

        // PHASING WARP PRISM
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_WARP_PRISM_PHASING)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // PYLONS
        bestTarget = allTargets.stream()
                .filter(target -> target.unit().getType() == Units.PROTOSS_PYLON)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // NEXUS
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.COMMAND_STRUCTURE_TYPE.contains(target.unit().getType()))
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // STRUCTURES (NOT-CANNONS)
        bestTarget = allTargets.stream()
                .filter(target -> UnitUtils.isStructure(target.unit().getType()) && target.unit().getType() != Units.PROTOSS_PHOTON_CANNON)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .orElse(null);
        if (bestTarget != null) {
            return bestTarget.unit().getPosition().toPoint2d();
        }

        // CONTINUE TO ENEMY BASE
        if (curEnemyBasePos != null && UnitUtils.getDistance(unit.unit(), curEnemyBasePos) > 1) {
            return curEnemyBasePos;
        }

        // NEXT STRUCTURE
        Optional<UnitInPool> closestEnemyStructure = Bot.OBS.getUnits(Alliance.ENEMY, target -> UnitUtils.isStructure(target.unit().getType())).stream()
                .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())));
        if (closestEnemyStructure.isPresent()) {
            return closestEnemyStructure.get().unit().getPosition().toPoint2d();
        }

        // ANY ENEMY UNIT IN RANGE
        return allTargets.stream()
                .filter(target -> target.unit().getHealth().orElse(0f) > 0)
                .min(Comparator.comparing(target -> UnitUtils.getCurHp(target.unit())))
                .map(target -> target.unit().getPosition().toPoint2d())
                .orElse(null);
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
        //pick newest enemy expansion base
        return GameCache.baseList.stream()
                .filter(base -> base.isEnemyBase)
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
    }

    //TODO: all races
    protected boolean isYamatoWorthy(Unit enemyUnit) {
        Units enemyType = (Units)enemyUnit.getType();
        List<Units> saveYamatoFor = new ArrayList<>();
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.ZERG_CORRUPTOR)) {
            saveYamatoFor.add(Units.ZERG_CORRUPTOR);
        }
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> UnitUtils.INFESTOR_TYPE.contains(enemy.getType()))) {
            saveYamatoFor.addAll(UnitUtils.INFESTOR_TYPE);
        }
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.PROTOSS_TEMPEST)) {
            saveYamatoFor.add(Units.PROTOSS_TEMPEST);
        }
        if (EnemyCache.enemyList.stream().anyMatch(enemy -> enemy.getType() == Units.PROTOSS_VOIDRAY)) {
            saveYamatoFor.add(Units.PROTOSS_VOIDRAY);
        }
        if (!saveYamatoFor.isEmpty()) {
            return saveYamatoFor.contains(enemyType);
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

}
