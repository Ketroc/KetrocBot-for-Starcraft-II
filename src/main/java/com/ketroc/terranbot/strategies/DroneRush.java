package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.DroneDrill;
import com.ketroc.terranbot.models.DelayedAction;
import com.ketroc.terranbot.models.TriangleOfNodes;
import com.ketroc.terranbot.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DroneRush {
    public static final Point2d ENEMY_MAIN_POS = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1);
    public static int droneRushStep;
    public static int clusterTriangleStep;
    public static int noTriangleStep;

    public static List<UnitInPool> droneList;
    public static UnitInPool target;
    public static boolean isAttackingCommand;
    private static boolean clusterNow;
    private static long giveUpTargetFrame;

    public static void onStep() {
        if (Bot.isDebugOn) {
            int lines = 0;
            Bot.DEBUG.debugTextOut("clusterEnemyNodeStep: " + DroneRush.clusterTriangleStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("droneRushStep: " + DroneRush.droneRushStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.sendDebug();
        }
        try {
            //update snapshots
            LocationConstants.enemyMineralTriangle.updateNodes();
            if (droneRushStep > 0) {
                UnitUtils.removeDeadUnits(droneList);
                if (droneList.isEmpty()) {
//                    DroneDrill.droneRushBuildStep++;
                    return;
                }
            }
            if (Time.nowFrames() == Time.toFrames("4:30")) {
                Print.print("Drone list contents:");
                droneList.forEach(unitInPool -> Print.print(unitInPool));
            }

            switch (droneRushStep) {
                case 0: //cluster up drones
                    if (droneList == null) {
                        droneList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.ZERG_DRONE, LocationConstants.baseLocations.get(0), 20);
                    }
                    if (LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD) || LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD505)) { //no cluster available
                        noTriangleRush();
                    }
                    else if (clusterTriangleNode(LocationConstants.myMineralTriangle, true)) {
                        UnitInPool drone = droneList.remove(0);
                        DroneDrill.lateDrones.add(drone);
                        ActionHelper.unitCommand(drone.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
                        droneRushStep++;
                    }
                    break;
                case 1: //send drones across the map to cluster on enemy node
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                        droneRushStep++;
                    }
                    break;

                case 2: //attack probes or attack nexus or cluster
                    addNewDrones();
                    attackingBase();
                    break;
                case 3: //enemy command structure dead/flying
                    List<UnitInPool> enemyList = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> !enemy.unit().getFlying().orElse(false));
                    droneList.addAll(DroneDrill.lateDrones);
                    DroneDrill.lateDrones.clear();
                    if (!enemyList.isEmpty()) {
                        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, enemyList.get(0).unit().getPosition().toPoint2d(), false);
                    }
                    else {
                        droneRushStep++;
                    }
                    break;
                case 4: //end drone rush
                    if (!droneList.isEmpty()) {
                        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, ENEMY_MAIN_POS, false);
                        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-2), true);
                        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getOuter().unit(), true);
                    }
                    droneList.clear();
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void noTriangleRush() {
        switch (noTriangleStep) {
            case 0:
                List<Unit> dronesWithMinerals = UnitUtils.toUnitList(
                        droneList.stream().filter(drone -> UnitUtils.isCarryingResources(drone.unit())).collect(Collectors.toList())
                );
                List<Unit> dronesEmpty = UnitUtils.toUnitList(
                        droneList.stream().filter(drone -> !UnitUtils.isCarryingResources(drone.unit())).collect(Collectors.toList())
                );

                if (!dronesEmpty.isEmpty()) {
                    ActionHelper.unitCommand(dronesEmpty, Abilities.ATTACK,
                            LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
                }
                if (!dronesWithMinerals.isEmpty()) {
                    ActionHelper.unitCommand(dronesWithMinerals, Abilities.HARVEST_RETURN_DRONE, false);
                }

                if (UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG).isEmpty()) {
                    droneList.addAll(DroneDrill.lateDrones);
                    DroneDrill.lateDrones.clear();
                    ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK,
                            LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
                    noTriangleStep++;
                }
                break;
            case 1:
                for (int i = 0; i < droneList.size(); i++) {
                    UnitInPool drone = droneList.get(i);
                    if (!drone.isAlive()) {
                        droneList.remove(i--);
                    } else if (drone.unit().getHealth().get() <= 10) {
                        ActionHelper.unitCommand(drone.unit(), Abilities.SMART, LocationConstants.myMineralTriangle.getOuter().unit(), false);
                        droneList.remove(i--);
                    }
                }
        }
    }

    private static void addNewDrones() {
        List<UnitInPool> dronesReady = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.ZERG_DRONE,
                LocationConstants.enemyMineralTriangle.getInner().unit().getPosition().toPoint2d(), 2f);
        for (UnitInPool drone : dronesReady) {
            if (!droneList.contains(drone)) {
                droneList.add(drone);
                DroneDrill.lateDrones.remove(drone);
            }
        }
    }

    private static void attackingBase() {
        //if command structure is dead or flying, find last structures
        Unit enemyCommand = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyCommandStructures, ENEMY_MAIN_POS, 1).stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
        if (enemyCommand == null || enemyCommand.getFlying().orElse(false)) {
            droneRushStep++;
            return;
        }

        List<UnitInPool> enemyWorkers = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType,
                Position.midPointUnitsMedian(UnitUtils.toUnitList(droneList)), 5);

        //if drone count 2x probe count a-move
        if ((droneList.size() >= enemyWorkers.size() * 2 && enemyWorkers.stream().noneMatch(enemy ->
                UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) < 2)) ||
                enemyWorkers.stream().noneMatch(enemy ->
                        UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) < 3)) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, ENEMY_MAIN_POS, false);
            isAttackingCommand = true;
        }
        else {
            //triangle-cluster after switching from attacking his command structure
            if (isAttackingCommand) {
                if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                    isAttackingCommand = false;
                }
            }
            //cluster and attack micro
            else {
                updateTarget();
                //droneDrillMicro1(enemyWorkers);
                //droneDrillMicro2();
                droneDrillMicro3(enemyWorkers);
                //droneDrillMicro4();
                //droneDrillMicro5();
            }
        }
    }

    public static void droneDrillMicro1(List<UnitInPool> enemyWorkers) {
        //cluster if not tight together
        if (clusterNow) { //TODO: move isAlive check elsewhere?
            if (isDronesClustered()) {
                clusterNow = false;
            }
            else {
                clusterUp();
            }
        }
        if (!clusterNow) {
            if (!isDronesClusteredAndReady()) {
                clusterNow = true;
                clusterUp();
            }
            else {
                UnitInPool temp = getTargetWorker(enemyWorkers);
                Unit targetWorker = (temp == null) ? null : temp.unit();
                if (targetWorker != null) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, targetWorker, false);
                    //attack
//                    List<Unit> clusterScvs = new ArrayList<>();
//                    List<Unit> attackScvs = new ArrayList<>();
//                    for (UnitInPool scv : scvList) {
//                        if (!isScvOffCooldown(scv.unit()) || isScvTooFar(scv.unit())) {
//                            clusterScvs.add(scv.unit());
//                        } else {
//                            attackScvs.add(scv.unit());
//                        }
//                    }
//                    if (!clusterScvs.isEmpty()) {
//                        ActionHelper.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
//                    }
//                    if (!attackScvs.isEmpty()) {
//                        ActionHelper.unitCommand(attackScvs, Abilities.ATTACK, targetWorker.unit(), false);
//                    }
//                    else {
//                        targetWorker = null;
//                    }
                }
                else {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                }
            }
        }
    }

    public static void droneDrillMicro2() {
        List<Unit> attackDrones = new ArrayList<>();
        List<Unit> clusterDrones = new ArrayList<>();
        for (UnitInPool scv : droneList) {
            if (isDroneOffCooldown(scv.unit()) && !isDroneTooFar(scv.unit())) {
                attackDrones.add(scv.unit());
            }
            else {
                clusterDrones.add(scv.unit());
            }
        }
        if (!attackDrones.isEmpty()) {
            ActionHelper.unitCommand(attackDrones, Abilities.ATTACK, LocationConstants.myMineralPos, false);
        }
        if (!clusterDrones.isEmpty()) {
            ActionHelper.unitCommand(clusterDrones, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    public static void droneDrillMicro3(List<UnitInPool> enemyWorkers) {
        if (target != null) {
            List<Unit> attackDrones = new ArrayList<>();
            List<Unit> clusterDrones = new ArrayList<>();
            for (UnitInPool drone : droneList) {
                if (isDroneOffCooldown(drone.unit())) {
                    attackDrones.add(drone.unit());
                }
                else {
                    clusterDrones.add(drone.unit());
                }
            }
            if (!attackDrones.isEmpty()) {
                Print.print("attackDrones.size() = " + attackDrones.size());
                ActionHelper.unitCommand(attackDrones, Abilities.ATTACK, target.unit(), false);
            }
            if (!clusterDrones.isEmpty()) {
                ActionHelper.unitCommand(clusterDrones, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }
        }
        else if (isDronesOffCooldown()) {
            target = oneShotTarget(enemyWorkers, droneList);
            if (target != null) {
                ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, target.unit(), false);
            }
            else { //TODO: bug this only allows 1 drone to attack
                ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }
        }
        else {
            ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    private static void updateTarget() {
        if (target != null) {
            if (!target.isAlive() || UnitUtils.getDistance(target.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) > 1.5f) {
                Print.print((!target.isAlive())?"target killed":"target cleared cuz out of range");
                target = null;
            }
        }
    }

    private static void droneDrillMicro4() {
        if (!DelayedAction.delayedActions.isEmpty()) {
            return;
        }
        if (isDronesClustered()) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
            long attackFrame = Time.nowFrames() + (Strategy.STEP_SIZE * 3);
            droneList.forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(attackFrame, Abilities.ATTACK, scv, LocationConstants.myMineralPos)));
            long clusterFrame = attackFrame + (Strategy.STEP_SIZE * 3);
            droneList.forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(clusterFrame, Abilities.SMART, scv, LocationConstants.enemyMineralTriangle.getMiddle())));
        }
        else {
            ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    private static UnitInPool oneShotTarget(List<UnitInPool> enemyWorkers, List<UnitInPool> attackDrones) {
        UnitInPool enemy = enemyWorkers.stream()
                .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit.unit(), LocationConstants.enemyMineralTriangle.getClusterPos())))
                .orElse(null);
        int numAttackers = (int)attackDrones.stream().filter(drone -> UnitUtils.getDistance(drone.unit(), enemy.unit()) < 3).count();
        if (numAttackers * 5 >= enemy.unit().getHealth().get()) {
            Print.print("target found.  #drones: " + numAttackers + ". enemy health: " + enemy.unit().getHealth().get());
            return enemy;
        }
        return null;
    }

    private static boolean isScvAttacking(Unit scv) {
        return UnitUtils.getOrder(scv) == Abilities.ATTACK;
    }


    private static boolean isDronesTooFar() {
        return droneList.stream().anyMatch(scv -> isDroneTooFar(scv.unit()));
    }

    private static boolean isDroneTooFar(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) > 1.4f;
    }

    private static void clusterUp() {
        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
    }

    private static boolean isDronesClustered() {
        return droneList.stream().allMatch(scv -> isByClusterPatch(scv.unit()));
    }

    private static boolean isByClusterPatch(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) < 1.4;
    }

    private static boolean isScvsClustering() {
        return droneList.stream()
                .anyMatch(scv -> !scv.unit().getOrders().isEmpty() &&
                        scv.unit().getOrders().get(0).getTargetedUnitTag().isPresent() &&
                        scv.unit().getOrders().get(0).getTargetedUnitTag().equals(LocationConstants.enemyMineralTriangle.getMiddle().getTag()));
    }

    private static boolean isDronesClusteredAndReady() {
        return droneList.stream().allMatch(drone -> isDroneOffCooldown(drone.unit()) &&
                UnitUtils.getDistance(drone.unit(), LocationConstants.enemyMineralTriangle.getMiddle().unit()) < 1.4);
    }

    private static boolean isDronesOffCooldown() {
        return droneList.stream().allMatch(drone -> isDroneOffCooldown(drone.unit()));
    }

    private static boolean isOneShotAvailable() {
        return droneList.stream().filter(drone -> isDroneOffCooldown(drone.unit())).count() >= 9;
    }

    private static boolean isDroneOffCooldown(Unit drone) {
        return UnitUtils.isWeaponAvailable(drone);
    }

    private static boolean isDroneReadyAndInRange(Unit drone, Unit target) {
        return UnitUtils.isWeaponAvailable(drone) &&
                UnitUtils.getDistance(drone, target) <= 0.8f;
    }

    private static UnitInPool getTargetWorker(List<UnitInPool> enemyWorkers) {
        for (UnitInPool enemyWorker : enemyWorkers) {
            if (droneList.stream().allMatch(scv -> UnitUtils.getDistance(scv.unit(), enemyWorker.unit()) < 0.8)) {
                return enemyWorker;
            }
        }
        return null;
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        return clusterTriangleNode(triangle, false);
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle, Boolean returnMinerals) {
        switch (clusterTriangleStep) {
            case 0:
                if (droneList.stream().anyMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2)) {
                    if (returnMinerals) {
                        List<Unit> dronesWithMinerals = UnitUtils.toUnitList(
                                droneList.stream().filter(drone -> UnitUtils.isCarryingResources(drone.unit())).collect(Collectors.toList())
                        );
                        List<Unit> dronesEmpty = UnitUtils.toUnitList(
                                droneList.stream().filter(drone -> !UnitUtils.isCarryingResources(drone.unit())).collect(Collectors.toList())
                        );

                        if (!dronesEmpty.isEmpty()) {
                            ActionHelper.unitCommand(dronesEmpty, Abilities.SMART, triangle.getInner().unit(), false);
                        }
                        if (!dronesWithMinerals.isEmpty()) {
                            ActionHelper.unitCommand(dronesWithMinerals, Abilities.HARVEST_RETURN_DRONE, false);
                        }
                    }
                    else {
                        ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getInner().unit(), false);
                    }
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 1:
                if (droneList.stream().anyMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2)) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getOuter().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 2:
                if (droneList.stream().anyMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4)) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getMiddle().unit(), false);
                }
                else {
                    clusterTriangleStep = 0;
                    return true;
                }
                break;

        }
        return false;
    }

}
