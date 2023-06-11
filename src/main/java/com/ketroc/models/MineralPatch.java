package com.ketroc.models;

import SC2APIProtocol.Debug;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.*;
import com.ketroc.utils.*;
import io.vertx.codegen.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MineralPatch {
    private static final float BY_NODE_DISTANCE = 1.3f;

    private Unit node;
    private List<UnitInPool> scvs = new ArrayList<>();
    private UnitInPool mule;
    private Point2d initialMulePos;
    private UnitInPool speedMineScv1;
    private UnitInPool speedMineScv2;
    private long lastMuleAddedFrame = -999;
    private boolean isMuleSpeedMineNeeded;
    private boolean isTurboMiningNeeded;
    private Point2d ccPos;
    private Point2d byNodePos;
    private Point2d nodePos;
    private Point2d byCCPos;
    private boolean isClosePatch;

    public static int muleSuccesses; //TODO: remove
    public static int muleFails; //TODO: remove

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
        adjustForTanksAndTurrets();
        setIsClosePatch();
        byCCPos = new Octagon(ccPos).intersection(new Line(byNodePos, ccPos)).iterator().next();
        isTurboMiningNeeded = byCCPos.distance(byNodePos) > 2.2f;
    }

    private void setIsClosePatch() {
        isClosePatch = UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType()) &&
                (Math.abs(ccPos.getX() - nodePos.getX()) < 1 || Math.abs(ccPos.getY() - nodePos.getY()) < 1);
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

    public void adjustForTanksAndTurrets() {
        Base base = getBase();
        if (base == null) {
            return;
        }

        base.getInMineralLinePositions().stream()
                .filter(defenseUnitPositions -> defenseUnitPositions.getUnit() != null)
                .map(DefenseUnitPositions::getPos)
                .forEach(turretPos -> {
                    Rectangle turretRect = new Rectangle(turretPos, 1.4f);
//                    turretRect.draw(Color.RED);
//                    new MineralShape(node).draw(Color.RED);
//                    DebugHelper.drawBox(byNodePos, Color.WHITE, 0.1f);
                    if (turretRect.contains(byNodePos)) {
                        turretRect.intersection(new MineralShape(node)).stream()
                                .min(Comparator.comparing(intersectPos -> intersectPos.distance(byNodePos)))
                                .ifPresent(p -> {
                                    byNodePos = p;
//                                    DebugHelper.drawBox(byNodePos, Color.YELLOW, 0.1f);
                                });
                    }
//                    Bot.DEBUG.sendDebug();
//                    int souidf = 21384;
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

    public UnitInPool getMule() {
        return mule;
    }

    public void setMule(UnitInPool mule) {
        this.mule = mule;
        isMuleSpeedMineNeeded = true;
        initialMulePos = mule.unit().getPosition().toPoint2d();
        ActionHelper.unitCommand(mule.unit(), Abilities.MOVE, Position.towards(mule.unit(), ccPos, 1.2f), true);
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

    public boolean isClosePatch() {
        return isClosePatch;
    }

    public long getLastMuleAddedFrame() {
        return lastMuleAddedFrame;
    }

    public void setLastMuleAddedFrame(long lastMuleAddedFrame) {
        this.lastMuleAddedFrame = lastMuleAddedFrame;
    }

    public UnitInPool getSpeedMineScv1() {
        return speedMineScv1;
    }

    public boolean isSpeedMineScv(Tag scvTag) {
        return speedMineScv1 != null && speedMineScv1.getTag().equals(scvTag);
    }

    public void setSpeedMineScv1(UnitInPool speedMineScv1) {
        this.speedMineScv1 = speedMineScv1;
    }

    public void setSpeedMineScvs() {
        speedMineScv1 = scvs.stream()
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), nodePos)))
                .get();
        speedMineScv2 = scvs.stream()
                .filter(scv -> !scv.getTag().equals(speedMineScv1.getTag())).findFirst()
                .get();
    }

    @Nullable
    public UnitInPool getAndReleaseScv() { //get scv (prefer scv that is not carrying minerals or about to)
        UnitInPool scv = scvs.stream()
                .filter(miningScv -> speedMineScv1 == null || !miningScv.getTag().equals(speedMineScv1.getTag()))
                .max(Comparator.comparing(miningScv -> UnitUtils.getDistance(miningScv.unit(), nodePos) +
                        (!UnitUtils.isCarryingResources(miningScv.unit()) ? 1000 : 0)))
                .orElse(null);
        if (scv != null) {
            scvs.remove(scv);
        }
        return scv;
    }

    public void harvestMicro(Unit scv) {
        if (speedMineScv1 != null) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            return;
        }

        float distToNode = UnitUtils.getDistance(scv, byNodePos);
        if (doTurboMine() && distToNode < 0.3f) {
            Unit otherScv = getOtherNodeScv(scv);
            if (UnitUtils.getDistance(scv, otherScv) < 0.35f &&
                    UnitUtils.getDistance(otherScv, byCCPos) < UnitUtils.getDistance(scv, byCCPos) - 0.15f &&
                    UnitUtils.getOrder(otherScv) == Abilities.HARVEST_RETURN) {
                if (UnitUtils.getOrder(scv) != Abilities.HOLD_POSITION) {
                    ActionHelper.unitCommand(scv, Abilities.HOLD_POSITION, false);
                }
                return;
            }
        }

        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER)) {
            //start speed MOVE
            if (distToNode < 2f && distToNode > 1f) {
                ActionHelper.unitCommand(scv, Abilities.MOVE, byNodePos, false);
                ActionHelper.unitCommand(scv, Abilities.SMART, node, true);
                return;
            }
            //fix bounce
            if (!node.getTag().equals(UnitUtils.getTargetUnitTag(scv))) {
                ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
                return;
            }
        }

        //put wayward scv back to work
        if (ActionIssued.getCurOrder(scv).isEmpty() || UnitUtils.isMiningScvStuck(scv)) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            return;
        }

        //complete turbomine (when near node on move command, mine again when other scv is done)
        if (distToNode < 1f &&
                (!doTurboMine() || UnitUtils.getDistance(scv, getOtherNodeScv(scv)) > 0.5f) &&
                UnitUtils.hasOrder(scv, Abilities.HOLD_POSITION)) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_GATHER, node, false);
            return;
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
        if (speedMineScv1 != null) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
            return;
        }

        float distToByCCPos = UnitUtils.getDistance(scv, byCCPos);
        if (ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_RETURN)) {
            // speed-mine
            if (doTurboReturn(scv) || (distToByCCPos < 2f && distToByCCPos > 1f)) {
                UnitInPool cc = getCC();
                if (cc != null && cc.unit().getBuildProgress() >= 1f) {
                    ActionHelper.unitCommand(scv, Abilities.MOVE, byCCPos, false);
                    ActionHelper.unitCommand(scv, Abilities.SMART, cc.unit(), true);
                }
                else {
                    ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
                }
                return;
            }
        }

        // put wayward scv back to work
        if (ActionIssued.getCurOrder(scv).isEmpty()) {
            ActionHelper.unitCommand(scv, Abilities.HARVEST_RETURN, false);
            return;
        }
    }

    private boolean doTurboReturn(Unit scv) {
        return doTurboMine() &&
                UnitUtils.getDistance(scv, byNodePos) < 0.5f &&
                UnitUtils.getOrder(getOtherNodeScv(scv)) == Abilities.HOLD_POSITION;
    }

    public void distanceReturnMicro(Unit scv) {
        if (ActionIssued.getCurOrder(scv).stream().noneMatch(order -> order.ability == Abilities.HARVEST_RETURN)) {
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
        scvs.forEach(scv -> {
            UnitUtils.returnAndStopScv(scv);
            Ignored.remove(scv.getTag());
        });
        endMuleSpeedMine();
        scvs.clear();
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

    /*
        mule exists - onunitcreated()
        add scv - shouldScvBeSet() && scv == null
        speed mine micro - scv != null
        release scv - !isNewMule()
        release mule - !mule.isAlive()
     */
    public void onStep() {
        if (mule == null) { // mule not yet set
            return;
        }
        if (!mule.isAlive()) { // mule died

            //TODO: remove
            boolean isSuccess = !mule.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) &&
                    UnitUtils.getDistance(mule.unit(), byCCPos) < 3;
            if (isSuccess) {
                muleSuccesses++;
            }
            else {
                muleFails++;
            }

            mule = null;
            endMuleSpeedMine();
            return;
        }
        if (speedMineScv1 == null && shouldScvBeSet() && hasNewMule()) { // set scv
            setSpeedMineScvs();
        }
        if (!shouldScvBeSet()) { // no micro needed until scv is set
            return;
        }
        if (speedMineScv1 == null || !scvs.contains(speedMineScv1)) { // if scv was never set or has been taken away
            endMuleSpeedMine();
            return;
        }
        if (!hasNewMule()) { // if speed-mine timer expired
            endMuleSpeedMine();
            return;
        }

        // get speedMineScvs into position
        ActionHelper.unitCommand(speedMineScv1.unit(), Abilities.MOVE, Position.towards(initialMulePos, byCCPos, -0.1f), false);
        ActionHelper.unitCommand(speedMineScv2.unit(), Abilities.SMART, getCC().unit(), false);

        // queue up mule speed mining
        if (isMuleSpeedMineNeeded) {
            if (UnitUtils.isCarryingResources(mule.unit())) {
                ActionHelper.unitCommand(mule.unit(), Abilities.SMART, getCC().unit(), true);
                ActionHelper.unitCommand(mule.unit(), Abilities.MOVE, Position.midPoint(byNodePos, byCCPos), true);
                ActionHelper.unitCommand(mule.unit(), Abilities.SMART, node, true);
                isMuleSpeedMineNeeded = false;
            }
        }
    }

    public void endMuleSpeedMine() {
        isMuleSpeedMineNeeded = false;
        speedMineScv1 = null;
        speedMineScv2 = null;
    }

    public boolean hasNewMule() {
        return Time.nowFrames() - lastMuleAddedFrame < 190;
    }

    public boolean shouldScvBeSet() {
        return Time.nowFrames() - lastMuleAddedFrame > 128;
    }

    private boolean doTurboMine() {
        return isTurboMiningNeeded && getScvs().size() == 2;
    }

    private Unit getOtherNodeScv(Unit thisScv) {
        return getScvs().stream()
                .filter(scv -> !scv.getTag().equals(thisScv.getTag()))
                .map(UnitInPool::unit)
                .findFirst()
                .orElse(null);
    }
    private boolean isMiningPatch(Unit scv) {
        if (scv == null) {
            return false;
        }
        return ActionIssued.getCurOrder(scv).stream().anyMatch(order -> order.ability == Abilities.HARVEST_GATHER) &&
                UnitUtils.getDistance(scv, byNodePos) < 0.5f;
    }

    private boolean isTurboMineComplete(Unit scv) {
        if (scv == null) {
            return true;
        }
        return UnitUtils.getDistance(scv, byNodePos) > 0.5f;
    }
}
