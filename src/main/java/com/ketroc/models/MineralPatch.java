package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Line;
import com.ketroc.geometry.MineralShape;
import com.ketroc.geometry.Rectangle;
import com.ketroc.geometry.Octagon;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MineralPatch {
    private static final float BY_NODE_DISTANCE = 1.3f;

    private Unit node;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d ccPos;
    private Point2d byNodePos;
    //private float distanceToHarvest = 1.38f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);
    //private float distanceToCC = 3f + (Launcher.STEP_SIZE > 2 ? 0.8f : 0);;
    private Point2d nodePos;
    private Point2d byCCPos;

    public MineralPatch(Unit node, Point2d ccPos) {
        this.ccPos = ccPos;
        this.node = node;
        nodePos = node.getPosition().toPoint2d();
        initMiningPositions();
    }

    public void initMiningPositions() {
        Rectangle mineralRect = new MineralShape(nodePos);
        byNodePos = mineralRect.intersection(new Line(nodePos, ccPos)).iterator().next();
        adjustForNearbyMinerals();
        adjustForMissileTurrets();
        byCCPos = new Octagon(ccPos).intersection(new Line(byNodePos, ccPos)).iterator().next();
    }

    private void adjustForNearbyMinerals() {
        List<UnitInPool> blockingMineralNodes = Bot.OBS.getUnits(Alliance.NEUTRAL, otherMineralPatch ->
                UnitUtils.MINERAL_NODE_TYPE.contains(otherMineralPatch.unit().getType()) &&
                        !node.getTag().equals(otherMineralPatch.getTag()) &&
                        new MineralShape(otherMineralPatch).contains(byNodePos));
        if (blockingMineralNodes.isEmpty()) {
            return;
        }
        new MineralShape(node).intersection(new MineralShape(blockingMineralNodes.stream()
                        .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), byNodePos)))
                        .get()))
                .stream()
                .min(Comparator.comparing(p -> p.distance(byNodePos)))
                .ifPresent(p -> byNodePos = p);
    }

    public void adjustForMissileTurrets() {
        Base base = getBase();
        if (base == null) {
            return;
        }

        base.getTurrets().stream()
                .filter(defenseUnitPositions -> defenseUnitPositions.getUnit() != null)
                .map(DefenseUnitPositions::getPos)
                .forEach(turretPos -> {
                    Rectangle turretRect = new Rectangle(turretPos, 1.4f);
                    if (turretRect.contains(byNodePos)) {
                        turretRect.intersection(new MineralShape(node)).stream()
                                .min(Comparator.comparing(intersectPos -> intersectPos.distance(byNodePos)))
                                .ifPresent(p -> byNodePos = p);
                    }
                });
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

    public Point2d getByNodePos() {
        return byNodePos;
    }

    public void setByNodePos(Point2d byNodePos) {
        this.byNodePos = byNodePos;
    }

    public Point2d getNodePos() {
        return nodePos;
    }

    public void setNodePos(Point2d nodePos) {
        this.nodePos = nodePos;
    }

    public Point2d getByCCPos() {
        return byCCPos;
    }

    public void setByCCPos(Point2d byCCPos) {
        this.byCCPos = byCCPos;
    }

    public void harvestMicro(Unit scv) {
        float distToNode = UnitUtils.getDistance(scv, byNodePos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            //start speed MOVE
            if (distToNode < 2f && distToNode > 1f) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNodePos, false);
                ActionHelper.unitCommand(scv, Abilities.SMART, node, true);
            }
            //fix bounce
            else if (!node.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }
        }
        else if (ActionIssued.getCurOrder(scv).isEmpty()) {
            //put wayward scv back to work
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
        }
    }

    public void distanceHarvestMicro(Unit scv) {
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            if (!node.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            }
        }
        //put wayward scv back to work
        else {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
        }
    }

    public void returnMicro(Unit scv) {
        float distToCC = UnitUtils.getDistance(scv, byCCPos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_RETURN)) {
            //start speed MOVE
            if (distToCC < 2f && distToCC > 1f) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byCCPos, false);
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

    public void distanceReturnMicro(Unit scv) {
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability != Abilities.HARVEST_RETURN)) {
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

    private UnitInPool getCC() {
        return getBase().getCc();
    }

    private Base getBase() {
        return GameCache.baseList.stream()
                .filter(base -> base.getCcPos().distance(ccPos) < 1)
                .findAny()
                .orElse(null);
    }

    public void visualMiningLayout() {
        DebugHelper.drawBox(byNodePos, Color.WHITE, 0.1f);
        DebugHelper.drawBox(byCCPos, Color.WHITE, 0.1f);
        new MineralShape(nodePos).draw(Color.RED);
    }
}
