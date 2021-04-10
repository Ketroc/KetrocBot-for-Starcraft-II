package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.*;

import java.util.ArrayList;
import java.util.List;

public class MineralPatch {
    private Unit node;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d ccPos;
    private Point2d byNode;
    private float distanceToHarvest = 1.38f + (Strategy.STEP_SIZE > 2 ? 0.5f : 0);
    private float distanceToCC = 3f + (Strategy.STEP_SIZE > 2 ? 0.5f : 0);;
    private Point2d nodePos;
    private Point2d byCC;

    public MineralPatch(Unit node, Point2d ccPos) {
        this.ccPos = ccPos;
        this.node = node;
        nodePos = node.getPosition().toPoint2d();
        byNode = Position.towards(nodePos, ccPos, 0.2f);
        byCC = Position.towards(ccPos, nodePos, 2.15f);
        float angle = Position.getAngle(byCC, byNode);
        if ((angle > 70 && angle < 120) || (angle > 240 && angle < 300)) { //mining angle is up or down
            byNode = nodePos;
        }
        else if ((angle > 130 && angle < 230) || angle > 310 || angle < 50) { //mining angle is left or right or diagonal
            distanceToHarvest += 0.17f;
            distanceToCC += 0.07f;
        }
    }

    public Unit getNode() {
        return node;
    }

    public void setNode(Unit node) {
        this.node = node;
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

    public Point2d getNodePos() {
        return nodePos;
    }

    public void setNodePos(Point2d nodePos) {
        this.nodePos = nodePos;
    }

    public Point2d getByCC() {
        return byCC;
    }

    public void setByCC(Point2d byCC) {
        this.byCC = byCC;
    }

    public void harvestMicro(Unit scv) {
        float distToNode = UnitUtils.getDistance(scv, nodePos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            //start speed MOVE
            if (distToNode < 3.1f && distToNode > distanceToHarvest) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNode, false);
            }
            //fix bounce
            else if (!node.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }
        }
        else if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.MOVE)) {
            //end speed MOVE
            if (distToNode <= distanceToHarvest) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }
        }
        else {
            //put wayward scv back to work (if not floating cc nearby -- avoid landing conflict)
            if (scv.getOrders().size() < 2) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }

        }
    }

    public void distanceHarvestMicro(Unit scv) {
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            if (!node.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }
        }
        //put wayward scv back to work
        else if (scv.getOrders().size() < 2) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
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

    public void distanceReturnMicro(Unit scv) {
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability != Abilities.HARVEST_RETURN) &&
                scv.getOrders().size() < 2) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
        }
    }

    public void updateUnit() {
        node = Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.getDistance(u.unit(), nodePos) < 0.5f).stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
        if (node == null) {
            onNodeDepleted();
        }
    }

    private void onNodeDepleted() {
        if (!scvs.isEmpty()) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvs), Abilities.STOP, false);
        }
    }
}
