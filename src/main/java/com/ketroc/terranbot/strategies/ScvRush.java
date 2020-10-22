package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.DelayedAction;
import com.ketroc.terranbot.models.TriangleOfNodes;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;

public class ScvRush {
    public static int clusterMyNodeStep;
    public static int clusterTriangleStep;

    public static int scvRushStep;
    public static List<UnitInPool> scvList;
    public static UnitInPool target;
    public static boolean isAttackingCommand;
    private static boolean clusterNow = false;

    public static boolean onStep() {
        if (KetrocBot.isDebugOn) {
            int lines = 0;
            Bot.DEBUG.debugTextOut("clusterEnemyNodeStep: " + ScvRush.clusterTriangleStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("scvRushStep: " + ScvRush.scvRushStep, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition(), Color.WHITE, 10);
        }
        try {
            //update snapshots
            LocationConstants.enemyMineralTriangle.updateNodes();

            switch (scvRushStep) {
                case 0: //cluster up scvs
                    if (scvList == null) {
                        scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20);
                    }
                    if (clusterTriangleNode(LocationConstants.myMineralTriangle)) {
                        scvRushStep++;
                    }
                    break;

                case 1: //send scvs across the map to cluster on enemy node
                    if (clusterTriangleNode(LocationConstants.myMineralTriangle)) {
                        scvRushStep++;
                    }
                    break;

                case 2: //send scvs across the map to cluster on enemy node
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                        scvRushStep++;
                    }
                    break;

                case 3: //attack probes or attack nexus or cluster
                    UnitUtils.removeDeadUnits(scvList);
                    if (scvList.isEmpty()) {
                        scvRushStep++;
                    }
                    else {
                        attackingBase();
                    }
                    break;
                case 4: //all scvs dead or enemy command structure dead
                    Switches.scvRushComplete = true;
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            return scvRushStep == 4;
        }

    }

    private static void attackingBase() {
        //Units commandStructureType = UnitUtils.enemyCommandStructures.get(0);

        //if command structure is dead, head home and advance to scvRushStep 4 to end
        if (UnitUtils.getEnemyUnitsOfTypes(UnitUtils.enemyCommandStructures).isEmpty()) {
            //send scvs home on attack-move
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2), false)
                    .unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, true);
            scvRushStep++;
            return;
        }

        Unit enemyCommand = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyCommandStructures).get(0);

        List<UnitInPool> enemyWorkers = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, enemyCommand.getPosition().toPoint2d(), 10);

        //if scv count triples probe count then kill his command structure
        if (scvList.size() >= enemyWorkers.size() * 1.5 ||
                enemyWorkers.stream().noneMatch(enemy -> UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralPos) < 4)) {
            for (UnitInPool scv : scvList) {
                //if worker < 1 distance from scv, attack worker
                if (!Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                        UnitUtils.getDistance(worker.unit(), scv.unit()) < 1).isEmpty()) {
                    Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                }
                else {
                    List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, weakScv -> weakScv.unit().getType() == Units.TERRAN_SCV &&
                            UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 2 &&
                            weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get());

                    //if not broke and near weak scv, then repair it
                    if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, nearbyWeakScvs.get(0).unit(), false);
                    }

                    //otherwise attack command structure
                    else {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, enemyCommand, false);
                    }
                }
            }
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
                //droneDrillMicro4(enemyWorkers);
//                //cluster if no enemy worker in range
//                Unit closestWorker = UnitUtils.getClosestEnemyOfType(UnitUtils.enemyWorkerType, scvList.get(0).unit().getPosition().toPoint2d());
//                if (UnitUtils.getDistance(closestWorker, scvList.get(0).unit()) > 0.7) { // &&
////                            scvList.stream().anyMatch(u -> u.unit().getWeaponCooldown().get() > 0f)) {
//                    Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
//                }
//                //otherwise attack
//                else {
//                    List<Unit> attackScvs = new ArrayList<>();
//                    List<Unit> clusterScvs = new ArrayList<>();
//                    for (UnitInPool scv : scvList) {
//                        if (scv.unit().getWeaponCooldown().orElse(0f) == 0f) {
//                            attackScvs.add(scv.unit());
//                        }
//                        else {
//                            clusterScvs.add(scv.unit());
//                        }
//                    }
//                    if (!attackScvs.isEmpty()) {
//                        Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, LocationConstants.myMineralPos, false);
//                    }
//                    if (!clusterScvs.isEmpty()) {
//                        Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
//                    }
//                }
            }
        }
    }

    public static void droneDrillMicro1(List<UnitInPool> enemyWorkers) {
        //cluster if not tight together
        if (clusterNow) { //TODO: move isAlive check elsewhere?
            if (isScvsClustered()) {
                clusterNow = false;
            }
            else {
                clusterUp();
            }
        }
        if (!clusterNow) {
            if (isScvsTooFar() || !isScvsOffCooldown()) {
                clusterNow = true;
                clusterUp();
            }
            else {
                UnitInPool temp = getTargetWorker(enemyWorkers);
                Unit targetWorker = (temp == null) ? null : temp.unit();
                if (targetWorker != null) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, targetWorker, false);
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
//                        Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
//                    }
//                    if (!attackScvs.isEmpty()) {
//                        Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, targetWorker.unit(), false);
//                    }
//                    else {
//                        targetWorker = null;
//                    }
                }
                else {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                }
            }
        }
    }

    public static void droneDrillMicro2() {
        List<Unit> attackScvs = new ArrayList<>();
        List<Unit> clusterScvs = new ArrayList<>();
        for (UnitInPool scv : scvList) {
            if (isScvOffCooldown(scv.unit()) && !isScvTooFar(scv.unit())) {
                attackScvs.add(scv.unit());
            }
            else {
                clusterScvs.add(scv.unit());
            }
        }
        if (!attackScvs.isEmpty()) {
            Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, LocationConstants.myMineralPos, false);
        }
        if (!clusterScvs.isEmpty()) {
            Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    public static void droneDrillMicro3(List<UnitInPool> enemyWorkers) {
        if (target != null) {
            List<Unit> attackScvs = new ArrayList<>();
            List<Unit> clusterScvs = new ArrayList<>();
            for (UnitInPool scv : scvList) {
                if (isScvOffCooldown(scv.unit())) {
                    attackScvs.add(scv.unit());
                }
                else {
                    clusterScvs.add(scv.unit());
                }
            }
            if (!attackScvs.isEmpty()) {
                Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, target.unit(), false);
            }
            if (!clusterScvs.isEmpty()) {
                Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }
        }
        else if (isScvsOffCooldown()) {
            target = oneShotTarget(enemyWorkers);
            if (target != null) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, target.unit(), false);
            }
            else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }
        }
        else {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    private static void updateTarget() {
        if (target != null) {
            if (!target.isAlive() || UnitUtils.getDistance(target.unit(), LocationConstants.enemyMineralPos) > 2.5f) {
                target = null;
            }
        }
    }

    private static void droneDrillMicro4(List<UnitInPool> enemyWorkers) {
        if (!DelayedAction.delayedActions.isEmpty()) {
            return;
        }
        if (isScvsClustered()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
            long attackFrame = Bot.OBS.getGameLoop() + (Strategy.SKIP_FRAMES * 3);
            scvList.stream().forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(attackFrame, Abilities.ATTACK, scv, LocationConstants.myMineralPos)));
            long clusterFrame = attackFrame + (Strategy.SKIP_FRAMES * 3);
            scvList.stream().forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(clusterFrame, Abilities.SMART, scv, LocationConstants.enemyMineralTriangle.getMiddle())));
        }
        else {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    private static UnitInPool oneShotTarget(List<UnitInPool> enemyWorkers) {
        for (UnitInPool enemy : enemyWorkers) {
            int numAttackers = Bot.OBS.getUnits(Alliance.SELF, scv ->
                    scv.unit().getType() == Units.TERRAN_SCV && UnitUtils.getDistance(scv.unit(), enemy.unit()) <= 0.9).size();
            if (numAttackers * 5 >= enemy.unit().getHealth().get()) {
                return enemy;
            }
        }
        return null;
    }

    private static boolean isScvAttacking(Unit scv) {
        return !scv.getOrders().isEmpty() && scv.getOrders().get(0).getAbility() == Abilities.ATTACK;
    }


    private static boolean isScvsTooFar() {
        return scvList.stream().anyMatch(scv -> isScvTooFar(scv.unit()));
    }

    private static boolean isScvTooFar(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) > 1.5f;
    }

    private static void clusterUp() {
        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
    }

    private static boolean isScvsClustered() {
        return scvList.stream().allMatch(scv -> isByClusterPatch(scv.unit()));
    }

    private static boolean isByClusterPatch(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) < 1.4;
    }

    private static boolean isScvsClustering() {
        return scvList.stream()
                .anyMatch(scv -> !scv.unit().getOrders().isEmpty() &&
                        scv.unit().getOrders().get(0).getTargetedUnitTag().isPresent() &&
                        scv.unit().getOrders().get(0).getTargetedUnitTag().equals(LocationConstants.enemyMineralTriangle.getMiddle().getTag()));
    }

    private static boolean isScvsOffCooldown() {
        return scvList.stream().allMatch(scv -> isScvOffCooldown(scv.unit()));
    }

    private static boolean isOneShotAvailable() {
        return scvList.stream().filter(scv -> isScvOffCooldown(scv.unit())).count() >= 9;
    }

    private static boolean isScvOffCooldown(Unit scv) {
        return scv.getWeaponCooldown().orElse(0f) == 0;
    }

    private static UnitInPool getTargetWorker(List<UnitInPool> enemyWorkers) {
        for (UnitInPool enemyWorker : enemyWorkers) {
            if (scvList.stream().allMatch(scv -> UnitUtils.getDistance(scv.unit(), enemyWorker.unit()) < 0.8)) {
                return enemyWorker;
            }
        }
        return null;
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        switch (clusterTriangleStep) {
            case 0:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2.7).count() > 2) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getInner().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 1:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2.7).count() > 2) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getOuter().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 2:
                if (scvList.stream().allMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4)) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getMiddle().unit(), false);
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
