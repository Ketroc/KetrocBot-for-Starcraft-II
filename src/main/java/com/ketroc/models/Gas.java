package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.geometry.Position;
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
//    private float distanceToHarvest = 2.05f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);
//    private float distanceToCC = 3.15f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);
    private Point2d byCCPos;


    // ========= CONSTRUCTORS ===========

    public Gas(Unit node, Point2d ccPos) {
        this.node = node;
        this.ccPos = ccPos;
        this.nodePos = node.getPosition().toPoint2d();
        byNode = Position.towards(nodePos, ccPos, 1.8f);
        byCCPos = Position.towards(ccPos, nodePos, 2.8f);
//        float angle = Position.getAngle(byCC, byNode);
//        if ((angle > 20 && angle < 70) || (angle > 110 && angle < 160) ||
//                (angle > 200 && angle < 250) || (angle > 280 && angle < 330)) { //mining diagonal
//            distanceToHarvest += 0.1f;
//            distanceToCC += 0.1f;
//        }
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

    public Point2d getByCCPos() {
        return byCCPos;
    }

    public void setByCCPos(Point2d byCCPos) {
        this.byCCPos = byCCPos;
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
            if (distToNode < 4.5f && distToNode > 2.5f) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNode, false);
                ActionHelper.unitCommand(scv, Abilities.SMART, refinery, true);
            }
        }
        else if (ActionIssued.getCurOrder(scv).isEmpty()) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, refinery, false);
        }
    }

    public void returnMicro(Unit scv) {
        float distToCC = UnitUtils.getDistance(scv, byCCPos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_RETURN)) {
            //start speed MOVE
            if (distToCC < 1.5f && distToCC > 1f) {
                UnitInPool cc = getCC();
                if (cc != null) {
                    ActionHelper.unitCommand(scv, Abilities.SMART, cc.unit(), true);
                }
                else {
                    ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
                }
            }
        }
        //put wayward scv back to work
        else if (ActionIssued.getCurOrder(scv).isEmpty()) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
        }
    }

    public void updateUnit() {
        node = Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.getDistance(u.unit(), nodePos) < 0.5f).stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
        refinery = Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.REFINERY_TYPE.contains(u.unit().getType()) &&
                        UnitUtils.getDistance(u.unit(), nodePos) < 0.5f)
                .stream()
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
        if (refinery == null || refinery.getVespeneContents().orElse(0) == 0) {
            onRefineryDeath();
        }
    }

    private void onRefineryDeath() {
        if (!scvs.isEmpty()) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(scvs), Abilities.STOP, false);
            scvs.clear();
        }
    }

    private UnitInPool getCC() {
        return getBase().getCc();
    }

    private Base getBase() {
        return GameCache.baseList.stream()
                .filter(base -> base.getCcPos().equals(ccPos))
                .findAny()
                .get();
    }
}
