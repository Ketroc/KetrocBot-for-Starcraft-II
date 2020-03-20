package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.models.Base;

import java.util.Collections;
import java.util.List;

public class ArmyManager {
    public static Point2d retreatPos;
    public static Point2d attackPos;

    public static void onStep() {
        //set attack location
        if (UnitUtils.isUnitTypesNearby(Units.TERRAN_BANSHEE, ArmyManager.attackPos, 1)) {
            LocationConstants.rotateBaseAttackIndex();
        }
        ArmyManager.attackPos = LocationConstants.myExpansionLocations.get(LocationConstants.myExpansionLocations.size()-LocationConstants.baseAttackIndex).toPoint2d();



        //position siege tanks
        for(UnitInPool tank : GameState.allFriendliesMap.getOrDefault(Units.TERRAN_SIEGE_TANK, Collections.emptyList())) {
            //if tank is idle
            if (tank.unit().getOrders().isEmpty()) {
                Point2d pos = setTankLocation();
                if (pos != null) {
                    Bot.ACTION.unitCommand(tank.unit(), Abilities.ATTACK, pos, false)
                            .unitCommand(tank.unit(), Abilities.MORPH_SIEGE_MODE, true);
                }
            }
        }

        //position marines
        for(UnitInPool marine : GameState.allFriendliesMap.getOrDefault(Units.TERRAN_MARINE, Collections.emptyList())) {
            if (marine.unit().getOrders().isEmpty()) { //for each idle marine
                Unit bunker1 = null;
                Unit bunker2 = null;
                for (UnitInPool bunker : GameState.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList())) {
                    if (bunker.unit().getPosition().toPoint2d().distance(LocationConstants.BUNKER1) < 1) { //if BUNKER1
                        bunker1 = bunker.unit();
                    }
                    else {
                        bunker2 = bunker.unit();
                    }
                }
                if (bunker1 != null && bunker2 != null) {
                    Unit targetBunker;
                    if (bunker1.getCargoSpaceTaken().get() > 0 && bunker2.getCargoSpaceTaken().get() < 1) {
                        targetBunker = bunker2;
                    } else {
                        targetBunker = bunker1;
                    }
                    Bot.ACTION.unitCommand(marine.unit(), Abilities.SMART, targetBunker, false);
                }
            }
        }

        //attack banshees
        if (Switches.BansheeDiveTarget == null) { //TODO: doesn't handle a failed dive (always be reassessing??)
            for (Unit detector : GameState.enemyDetector) {
                if (!detector.getFlying().orElse(false)) {
                    if (shouldBansheesDive(detector)) {
                        Switches.BansheeDiveTarget = Bot.OBS.getUnit(detector.getTag());
                        break;
                    }
                }
            }
        }
        if (Switches.BansheeDiveTarget != null) {
            if (GameState.bansheeList.size() < 2) { //turn off the dive switch if it failed
                Switches.BansheeDiveTarget = null;
            }
            Bot.ACTION.unitCommand(GameState.bansheeList, Abilities.ATTACK, Switches.BansheeDiveTarget.unit(), false);
        }
        else {
            for (Unit banshee : GameState.bansheeList) {
                giveBansheeCommand(banshee);
            }
        }
//        if (attackPosViking == null) { //set viking attack position back home if no banshees are engaging TODO: delete me if viking doesn't need new attack point
//            attackPosViking = retreatPos;
//        }

        //attack vikings
        for (Unit viking : GameState.vikingList) {
            giveVikingCommand(viking);
        }

        //repair station
        int numInjured = Bot.OBS.getUnits(Alliance.SELF, u -> { //get number of injured army units in dock
            return (u.unit().getType() == Units.TERRAN_VIKING_FIGHTER || u.unit().getType() == Units.TERRAN_BANSHEE) &&
                    UnitUtils.getHealthPercentage(u.unit()) < 100 &&
                    u.unit().getPosition().toPoint2d().distance(retreatPos) < 5;
        }).size();
        if (numInjured > 0) {
            int numRepairingScvs = Bot.OBS.getUnits(Alliance.SELF, u -> { //get number of scvs currently repairing (ie, on attack move)
                return u.unit().getType() == Units.TERRAN_SCV &&
                        !u.unit().getOrders().isEmpty() &&
                        (u.unit().getOrders().get(0).getAbility() == Abilities.ATTACK || u.unit().getOrders().get(0).getAbility() == Abilities.EFFECT_REPAIR);
            }).size();  //TODO: move this to com.ketroc.terranbot.GameState.startFrame() ??
            int numScvsToSend = Strategy.NUM_SCVS_REPAIR_STATION - numRepairingScvs; //decide 5 or 10 total scvs to repair at dock
            if (numScvsToSend > 1) {
                List<Unit> availableScvs = WorkerManager.unitInPoolToUnitList(WorkerManager.getAvailableScvs(retreatPos, 30, false));
                if (availableScvs.size() > numScvsToSend) {
                    availableScvs = availableScvs.subList(0, numScvsToSend);
                }
                if (!availableScvs.isEmpty()) {
                    for (Unit scv : availableScvs) { //turn on autocast repair for all scvs selected
                        if (!scv.getBuffs().contains(Buffs.AUTOMATED_REPAIR)) {
                            Bot.ACTION.toggleAutocast(scv.getTag(), Abilities.EFFECT_REPAIR_SCV);
                        }
                    }
                    Bot.ACTION.unitCommand(availableScvs, Abilities.ATTACK, retreatPos, false) //a-move scvs to repair station and queue up mining minerals afterwards
                            .unitCommand(availableScvs, Abilities.SMART, GameState.mineralNodeRally.unit(), true);
                }
            }
        }
    }

    private static Point2d setTankLocation() {
//        //if tank at eng base - REMOVED: skipping tank at main ramp
//        if (!com.ketroc.terranbot.UnitUtils.isUnitTypeNearby(Units.TERRAN_SIEGE_TANK_SIEGED, com.ketroc.terranbot.LocationConstants.DEPOT2, 5)) {
//            return com.ketroc.terranbot.LocationConstants.DEPOT2;
//        }
        //loop through bases looking for tank
        for (Base base : GameState.baseList) {
            Unit cc = base.getCc();
            if (cc.getType() == Units.TERRAN_ORBITAL_COMMAND) { //ignore main base and macro OCs
                continue;
            }
            if (!UnitUtils.isUnitTypesNearby(Units.TERRAN_SIEGE_TANK_SIEGED, cc.getPosition().toPoint2d(), 12)) {
                return calculateTankPosition(cc.getPosition().toPoint2d());
            }
        }
        return null;
    }

    private static Point2d calculateTankPosition(Point2d ccPos) {//pick position away from enemy main base like a knight move (3x1)
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.myExpansionLocations.get(LocationConstants.myExpansionLocations.size()-1).getX();
        float yEnemy = LocationConstants.myExpansionLocations.get(LocationConstants.myExpansionLocations.size()-1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 2;
        float yMove = 2;

        if (Math.abs(xDistance) > Math.abs(yDistance)) { //move 3x1
            xMove = 5f;
        }
        else { //move 1x3
            yMove = 5f;
        }
        if (xDistance > 0) {
            xMove *= -1;
        }
        if (yDistance > 0) {
            yMove *= -1;
        }
        return Point2d.of(xCC + xMove, yCC + yMove);
    }

    public static Abilities decideStarportUnit() { //TODO: move to com.ketroc.terranbot.BuildManager??
        BuildManager.vikingsRequired = calcNumVikingsNeeded();
        if (GameState.vikingList.size() < BuildManager.vikingsRequired) {
            BuildManager.vikingsRequired--;
            return Abilities.TRAIN_VIKING_FIGHTER;
        }
        return Abilities.TRAIN_BANSHEE;
    }

    public static int calcNumVikingsNeeded() { //TODO: update for vs zerg and protoss
        float answer = 0;
        int numEnemyRaven = 0;
        for (Unit enemy : GameState.enemyIsAir) {
            switch ((Units)enemy.getType()) {
                case TERRAN_VIKING_ASSAULT: case TERRAN_VIKING_FIGHTER:
                    answer += 1.3;
                    break;
                case TERRAN_BATTLECRUISER:
                    answer += 3;
                    break;
                case TERRAN_RAVEN:
                    answer += 1;
                    numEnemyRaven++;
                    break;
                case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                    answer += 1;
            }
        }
        answer = Math.max((float)numEnemyRaven*4, answer); //at least 4 vikings per raven

        if (GameState.bansheeList.size() > 5) {
            answer = Math.max(answer, 3); //minimum 3 vikings once banshee count is 6+
        }
        return (int)answer;
    }

    public static void giveBansheeCommand(Unit banshee) {
        int x = Math.round(banshee.getPosition().getX());
        int y = Math.round(banshee.getPosition().getY());
        boolean isUnsafe = GameState.pointUnsafeFromAir[x][y] || GameState.pointUnsafeFromGround[x][y];
        boolean isInDetectionRange = GameState.pointDetected[x][y];
        boolean isInBansheeRange = GameState.pointInBansheeRange[x][y];
        boolean canAttack = banshee.getWeaponCooldown().orElse(1f) == 0f;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0f) > Strategy.ENERGY_BEFORE_CLOAKING;

        if (UnitUtils.getHealthPercentage(banshee) < 100 && banshee.getPosition().toPoint2d().distance(retreatPos) < 3) {  //banshee under 100% health and at repair bay
            if (cloakState == CloakState.CLOAKED_ALLIED) {
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_OFF_BANSHEE, false);
            }
            else {
                Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
            }
        }
        else if (UnitUtils.getHealthPercentage(banshee) < Strategy.RETREAT_HEALTH) {
            Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
        }
        else if (canAttack && isInBansheeRange) {
            //attack
            Bot.ACTION.unitCommand(banshee, Abilities.ATTACK, attackPos, false);
        }
        else if (isUnsafe) {
            if (isInDetectionRange) {
                //retreat
                Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
            }
            else if (cloakState == CloakState.CLOAKED_ALLIED && banshee.getEnergy().get() > 2) {
                if (isInBansheeRange) {
                    //retreat
                    Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
                }
                else {
                    //attack
                    Bot.ACTION.unitCommand(banshee, Abilities.ATTACK, attackPos, false);
                }
            }
            else if (canCloak) {
                //cloak
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            }
            else {
                //retreat
                Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
            }
        }
        else if (!canCloak && cloakState != CloakState.CLOAKED_ALLIED) {
            //retreat
            Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
        }
        else {
            if (isInBansheeRange) {
                //retreat
                Bot.ACTION.unitCommand(banshee, Abilities.MOVE, retreatPos, false);
            }
            else {
                //attack
                Bot.ACTION.unitCommand(banshee, Abilities.ATTACK, attackPos, false);
            }
        }
    }

    private static void giveVikingCommand(Unit viking) {
        int x = Math.round(viking.getPosition().getX());
        int y = Math.round(viking.getPosition().getY());
        boolean isUnsafe = GameState.pointUnsafeFromGround[x][y];
        boolean isInVikingRange = GameState.pointInVikingRange[x][y];
        boolean canAttack = viking.getWeaponCooldown().orElse(1f) == 0f;

        if (UnitUtils.getHealthPercentage(viking) < 100 && viking.getPosition().toPoint2d().distance(retreatPos) < 3) {  //viking under 100% health and at retreat position
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, retreatPos, false);
        }
        else if (UnitUtils.getHealthPercentage(viking) < Strategy.RETREAT_HEALTH) {
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, retreatPos, false);
        }
        else if (!canAttack) {
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, retreatPos, false);
        }
        else if (isInVikingRange) {
            Bot.ACTION.unitCommand(viking, Abilities.ATTACK, attackPos, false);
        }
        else if (isUnsafe) {
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, retreatPos, false);
        }
        else {
            Bot.ACTION.unitCommand(viking, Abilities.ATTACK, attackPos, false);
        }
    }

    private static boolean shouldBansheesDive(Unit detector) {
        List<UnitInPool> banshees = UnitUtils.getUnitsNearbyOfType(Units.TERRAN_BANSHEE, detector.getPosition().toPoint2d(), 16);
        //calculate point from detector
        Point2d threatPoint = getPointFromA(detector.getPosition().toPoint2d(), retreatPos, UnitUtils.getAirAttackRange(detector));
        Bot.DEBUG.debugBoxOut(Point.of(threatPoint.getX()-0.4f, threatPoint.getY()-0.4f, GameState.z),
                Point.of(threatPoint.getX()+0.4f, threatPoint.getY()+0.4f, GameState.z),
                Color.GREEN);
        //decide to dive
        return isEnoughBansheesToDive(banshees.size(), GameState.threatToAir[(int)threatPoint.getX()][(int)threatPoint.getY()]);
    }

    //10% of threatlevel + 3 = num banshees to dive
    private static boolean isEnoughBansheesToDive(int numBanshees, int threatLevel) {
        return numBanshees >= Math.round(threatLevel/8) + 4;
    }

    public static Point2d getPointFromA(Point2d a, Point2d b, float distance) {
        double ratio = distance / a.distance(b);
        int newX = (int)(((b.getX() - a.getX()) * ratio) + a.getX());
        int newY = (int)(((b.getY() - a.getY()) * ratio) + a.getY());
        return Point2d.of(newX, newY);
    }
}
