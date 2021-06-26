package com.ketroc.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.models.Base;
import com.ketroc.models.DelayedAction;
import com.ketroc.models.TriangleOfNodes;
import com.ketroc.utils.*;
import com.ketroc.utils.Error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            DebugHelper.addInfoLine("clusterEnemyNodeStep: " + ScvRush.clusterTriangleStep);
            DebugHelper.addInfoLine("scvRushStep: " + ScvRush.scvRushStep);
            DebugHelper.drawText("0", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition().toPoint2d(), Color.WHITE);
            DebugHelper.drawText("1", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition().toPoint2d(), Color.WHITE);
            DebugHelper.drawText("2", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition().toPoint2d(), Color.WHITE);
        }
        try {
            //update snapshots
            LocationConstants.enemyMineralTriangle.updateNodes();
            if (scvList != null) {  //TODO: hack for preventing idle scvs from getting picked up for speed mining
                scvList.forEach(scv -> Base.releaseScv(scv.unit()));
            }
            switch (scvRushStep) {
                case 0: //cluster up scvs
                    if (scvList == null) {
                        scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20);
                        scvList.forEach(scv -> Base.releaseScv(scv.unit()));
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
            Error.onException(e);
        }
        finally {
            return scvRushStep == 4;
        }

    }

    private static void attackingBase() {
        //Units commandStructureType = UnitUtils.enemyCommandStructures.get(0);

        //if command structure is dead, head home and advance to scvRushStep 4 to end
        if (UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyCommandStructures).isEmpty()) {
            //send scvs home on attack-move
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2), false);
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, true);
            scvRushStep++;
            return;
        }

        Unit enemyCommand = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyCommandStructures).get(0);

        List<UnitInPool> enemyWorkers = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, enemyCommand.getPosition().toPoint2d(), 10);

        //if scv count is 1.5x probe count then kill his command structure
        if ((scvList.size() >= enemyWorkers.size() * 2 ||
                scvList.stream().mapToDouble(u -> u.unit().getHealth().orElse(0f)).sum() >
                        enemyWorkers.stream().mapToDouble(u -> u.unit().getHealth().orElse(0f)).sum() * 2) ||
                enemyWorkers.stream().noneMatch(enemy -> UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralPos) < 4)) {
            for (UnitInPool scv : scvList) {
                //if worker < 1 distance from scv, attack worker
                if (!Bot.OBS.getUnits(Alliance.ENEMY, worker -> worker.unit().getType() == UnitUtils.enemyWorkerType &&
                        UnitUtils.getDistance(worker.unit(), scv.unit()) < 1).isEmpty()) {
                    ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                }
                //if broke, attack enemy cc
                else if (GameCache.mineralBank == 0) {
                    ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, enemyCommand, false);
                }
                //otherwise repair nearby scv or attack enemy cc
                else {
                    scvList.stream()
                            .filter(weakScv -> UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 2 &&
                                    UnitUtils.getDistance(weakScv.unit(), enemyCommand) < 20 &&
                                    weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get())
                            .findFirst()
                            .ifPresentOrElse(
                                    weakScv -> ActionHelper.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, weakScv.unit(), false),
                                    () -> ActionHelper.unitCommand(scv.unit(), Abilities.ATTACK, enemyCommand, false));
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
//                    ActionHelper.unitCommand(UnitUtils.unitInPoolToUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
//                }
//                //otherwise attack
//                else {
//                    List<Unit> attackScvs = new ArrayList<>();
//                    List<Unit> clusterScvs = new ArrayList<>();
//                    for (UnitInPool scv : scvList) {
//                        if (UnitUtils.isWeaponAvailable(scv.unit())) {
//                            attackScvs.add(scv.unit());
//                        }
//                        else {
//                            clusterScvs.add(scv.unit());
//                        }
//                    }
//                    if (!attackScvs.isEmpty()) {
//                        ActionHelper.unitCommand(attackScvs, Abilities.ATTACK, LocationConstants.myMineralPos, false);
//                    }
//                    if (!clusterScvs.isEmpty()) {
//                        ActionHelper.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.inner.unit(), false);
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
                Unit targetWorker = getTargetWorker(enemyWorkers);
                if (targetWorker != null) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, targetWorker, false);
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
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
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
            ActionHelper.unitCommand(attackScvs, Abilities.ATTACK, LocationConstants.myMineralPos, false);
        }
        if (!clusterScvs.isEmpty()) {
            ActionHelper.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
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
                ActionHelper.unitCommand(attackScvs, Abilities.ATTACK, target.unit(), false);
            }
            if (!clusterScvs.isEmpty()) {
                ActionHelper.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }
        }
        else if (isScvsOffCooldown()) {
            target = oneShotTarget(enemyWorkers, scvList);
            if (target != null) {
                ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, target.unit(), false);
            }
            else {
                ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }
        }
        else {
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
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
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
            long attackFrame = Time.nowFrames() + (Strategy.STEP_SIZE * 3);
            scvList.stream().forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(attackFrame, Abilities.ATTACK, scv, LocationConstants.myMineralPos)));
            long clusterFrame = attackFrame + (Strategy.STEP_SIZE * 3);
            scvList.stream().forEach(scv ->
                    DelayedAction.delayedActions.add(new DelayedAction(clusterFrame, Abilities.SMART, scv, LocationConstants.enemyMineralTriangle.getMiddle())));
        }
        else {
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }
    }

    private static UnitInPool oneShotTarget(List<UnitInPool> enemyWorkers, List<UnitInPool> attackScvs) {
        UnitInPool enemy = enemyWorkers.stream()
                .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit.unit(), LocationConstants.enemyMineralTriangle.getClusterPos())))
                .orElse(null);
        int numAttackers = (int)attackScvs.stream().filter(scv -> UnitUtils.getDistance(scv.unit(), enemy.unit()) < 3).count();
        if (numAttackers * 5 >= enemy.unit().getHealth().get()) {
            Print.print("target found.  #scvs: " + numAttackers + ". enemy health: " + enemy.unit().getHealth().get());
            return enemy;
        }
        return null;
    }

    private static boolean isScvAttacking(Unit scv) {
        return UnitUtils.getOrder(scv) == Abilities.ATTACK;
    }


    private static boolean isScvsTooFar() {
        return scvList.stream().anyMatch(scv -> isScvTooFar(scv.unit()));
    }

    private static boolean isScvTooFar(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) > 1.5f;
    }

    private static void clusterUp() {
        ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
    }

    private static boolean isScvsClustered() {
        return scvList.stream().allMatch(scv -> isByClusterPatch(scv.unit()));
    }

    private static boolean isByClusterPatch(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) < 1.4;
    }

    private static boolean isScvsOffCooldown() {
        return scvList.stream().allMatch(scv -> isScvOffCooldown(scv.unit()));
    }

    private static boolean isOneShotAvailable() {
        return scvList.stream().filter(scv -> isScvOffCooldown(scv.unit())).count() >= 9;
    }

    private static boolean isScvOffCooldown(Unit scv) {
        return UnitUtils.isWeaponAvailable(scv);
    }

    private static Unit getTargetWorker(List<UnitInPool> enemyWorkers) {
        for (UnitInPool enemyWorker : enemyWorkers) {
            if (scvList.stream().allMatch(scv -> UnitUtils.getDistance(scv.unit(), enemyWorker.unit()) < 0.8)) {
                return enemyWorker.unit();
            }
        }
        return null;
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        switch (clusterTriangleStep) {
            case 0:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2.7).count() > 2) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getInner().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 1:
                if (scvList.stream().filter(u -> UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2.7).count() > 2) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getOuter().unit(), false);
                }
                else {
                    clusterTriangleStep++;
                }
                break;
            case 2:
                if (scvList.stream().allMatch(u -> UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4)) {
                    ActionHelper.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getMiddle().unit(), false);
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
