package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.Position;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;

public class Gas {
    private Unit node;
    private Unit refinery;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d nodePos;
    private Point2d ccPos;
    private Point2d byNode;
    private float distanceToHarvest = 2.05f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);
    private float distanceToCC = 3.15f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);
    private Point2d byCC;
    private boolean isDriedUp;


    // ========= CONSTRUCTORS ===========

    public Gas(Unit node, Point2d ccPos) {
        this.node = node;
        this.ccPos = ccPos;
        this.nodePos = node.getPosition().toPoint2d();
        byNode = Position.towards(nodePos, ccPos, 1f);
        byCC = Position.towards(ccPos, nodePos, 2.15f);
        float angle = Position.getAngle(byCC, byNode);
        if ((angle > 20 && angle < 70) || (angle > 110 && angle < 160) ||
                (angle > 200 && angle < 250) || (angle > 280 && angle < 330)) { //mining diagonal
            distanceToHarvest += 0.1f;
            distanceToCC += 0.1f;
        }

    }

    // ========= GETTERS AND SETTERS =========

    public Unit getNode() {
        return node;
    }

    public void setNode(Unit node) {
        this.node = node;
    }

    public Unit getRefinery() {
        return refinery;
    }

    public void setRefinery(Unit refinery) {
        this.refinery = refinery;
    }

    public Point2d getNodePos() {
        return nodePos;
    }

    public void setNodePos(Point2d nodePos) {
        this.nodePos = nodePos;
    }

    public List<UnitInPool> getScvs() {
        return scvs;
    }

    public void setScvs(List<UnitInPool> scvs) {
        this.scvs = scvs;
    }

    public Point2d getByNode() {
        return byNode;
    }

    public void setByNode(Point2d byNode) {
        this.byNode = byNode;
    }

    public Point2d getByCC() {
        return byCC;
    }

    public void setByCC(Point2d byCC) {
        this.byCC = byCC;
    }

    public boolean isDriedUp() {
        return isDriedUp;
    }

    // ============= METHODS ==============

    public boolean isAvailable() {
        return refinery == null &&
                node.getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY &&
                UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.GAS_STRUCTURE_TYPES, nodePos, 1).isEmpty();
    }

    public void harvestMicro(Unit scv) {
        float distToNode = UnitUtils.getDistance(scv, nodePos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            //start speed MOVE
            if (distToNode < 3.9f && distToNode > distanceToHarvest) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNode, false);
            }
        }
        else if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.MOVE)) {
            //end speed MOVE
            if (distToNode <= distanceToHarvest) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, refinery, false);
            }
        }
        else {
            //put wayward scv back to work (if not floating cc nearby -- avoid landing conflict)
            if (scv.getOrders().size() < 2) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, refinery, false);
            }

        }
    }

    public void returnMicro(Unit scv) {
        float distToCC = UnitUtils.getDistance(scv, ccPos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_RETURN)) {
            //start speed MOVE
            if (distToCC < 4.7f && distToCC > distanceToCC) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byCC, false);
            }
        }
        else if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.MOVE)) {
            //end speed MOVE
            if (distToCC <= distanceToCC) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
            }
        }
        //put wayward scv back to work
        else {
            if (scv.getOrders().size() < 2) { //not being pushed away to make room
                ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
            }
        }
    }

    //update Unit objects for geyser and refinery
    public void updateUnit() {
        //ignore dried up geysers
        if (isDriedUp) {
            return;
        }

        //update geyser Unit
        node = Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.getDistance(u.unit(), nodePos) < 0.5f).stream()
                .map(UnitInPool::unit)
                .findAny()
                .orElse(null);

        //no further updates if out of vision
        if (node.getDisplayType() != DisplayType.VISIBLE) {
            return;
        }

        //set if geyser is dried up
        isDriedUp = node.getVespeneContents().orElse(1500) == 0;

        //update refinery Unit
        boolean hadRefinery = refinery != null; //had refinery last frame
        refinery = Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.REFINERY_TYPE.contains(u.unit().getType()) &&
                        UnitUtils.getDistance(u.unit(), nodePos) < 0.5f)
                .stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);

        //free up gas scvs if refinery dried up or died
        if (hadRefinery && (isDriedUp || refinery == null)) {
            onRefineryDeath();
        }
    }

    private void onRefineryDeath() {
        if (!scvs.isEmpty()) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvs), Abilities.HARVEST_RETURN, false);
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvs), Abilities.STOP, true);
            scvs.clear();
        }
    }

}
