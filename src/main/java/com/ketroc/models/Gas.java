package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.*;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;
import io.vertx.codegen.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Gas {
    private Unit node;
    private Unit refinery;
    private List<UnitInPool> scvs = new ArrayList<>();
    private Point2d nodePos;
    private Point2d ccPos;
    private Point2d byNodePos;
    private Point2d byCCPos;
    private boolean isDriedUp;


    // ========= CONSTRUCTORS ===========

    public Gas(Unit node, Point2d ccPos) {
        this.ccPos = ccPos;
        this.node = node;
        nodePos = node.getPosition().toPoint2d();
        initMiningPositions();
    }

    public void initMiningPositions() {
        Rectangle gasRect = new GeyserShape(nodePos);
        byNodePos = gasRect.intersection(new Line(nodePos, ccPos)).iterator().next();
        adjustForMissileTurrets();
        byCCPos = new Octagon(ccPos).intersection(new Line(byNodePos, ccPos)).iterator().next();
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
                        turretRect.intersection(new GeyserShape(node)).stream()
                                .min(Comparator.comparing(intersectPos -> intersectPos.distance(byNodePos)))
                                .ifPresent(p -> {
                                    byNodePos = p;
                                });
                    }
                });
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

    public Point2d getByNodePos() {
        return byNodePos;
    }

    public void setByNodePos(Point2d byNodePos) {
        this.byNodePos = byNodePos;
    }

    public Point2d getByCCPos() {
        return byCCPos;
    }

    public void setByCCPos(Point2d byCCPos) {
        this.byCCPos = byCCPos;
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

    @Nullable
    public UnitInPool getAndReleaseScv() {
        UnitInPool gasScv = scvs.stream()
                .filter(scv -> Time.nowFrames() == scv.getLastSeenGameLoop()) //not in the refinery
                .max(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), nodePos) +
                        (!UnitUtils.isCarryingResources(scv.unit()) ? 1000 : 0)))
                .orElse(null);
        if (gasScv != null) {
            scvs.remove(gasScv);
        }
        return gasScv;
    }

    public void harvestMicro(Unit scv) {
        float distToNode = UnitUtils.getDistance(scv, byNodePos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            //start speed MOVE
            if (distToNode < 1.75f && distToNode > 1f) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNodePos, false);
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
            if (distToCC < 1.75f && distToCC > 1f) {
                UnitInPool cc = getCC();
                if (cc != null) {
                    ActionHelper.unitCommand(scv, Abilities.MOVE, byCCPos, false);
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
        new GeyserShape(nodePos).draw(Color.RED);
    }
}
