package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.StructureScv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExpansionClearing {
    public static List<ExpansionClearing> expoClearList = new ArrayList<>();

    public Point2d expansionPos;
    private int defenseStep;
    public BasicMover raven;
    public List<UnitInPool> blockers;
    public UnitInPool turret;
    public boolean isTurretActive;

    public ExpansionClearing(Point2d expansionPos) {
        this.expansionPos = expansionPos;
        StructureScv.cancelProduction(Units.TERRAN_COMMAND_CENTER, expansionPos);
    }

    private void addRaven() {
        if (raven != null) {
            Ignored.remove(raven.mover.getTag());
            raven = null;
        }
        Tag nearestRaven = GameCache.ravenList.stream()
                .filter(raven -> raven.getEnergy().orElse(0f) > 49)
                .filter(raven -> !Ignored.contains(raven.getTag()))
                .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit, expansionPos)))
                .map(Unit::getTag)
                .orElse(null);
        if (nearestRaven != null) {
            this.raven = new BasicMover(Bot.OBS.getUnit(nearestRaven), expansionPos);
        }
    }

    public boolean clearExpansion() {
        //abandon clearing if a command structure is on location
        if (!Bot.OBS.getUnits(structure -> UnitUtils.COMMAND_STRUCTURE_TYPE.contains(structure.unit().getType()) &&
                UnitUtils.getDistance(structure.unit(), expansionPos) < 5).isEmpty()) {
            return true;
        }

        //get a new raven
        if (raven == null || !raven.mover.isAlive()) {
            addRaven();
        }
        //raven is travelling to expansion
        else if (turret == null && UnitUtils.getDistance(raven.mover.unit(), expansionPos) > 3 && !isTurretActive) {
            raven.onStep();
        }
        else if (turret == null) {

            //if autoturret cast is needed
            if (!isTurretActive) {

                //determine autoturret position
                Point2d centerPoint = getCenterPoint();
                if (centerPoint != null) {
                    if (raven.mover.unit().getEnergy().orElse(0f) < 50) {
                        addRaven();
                        return false;
                    }
                    Point2d turretPos = getTurretPos(centerPoint);

                    //cast autoturret
                    if (turretPos != null) {
                        raven.targetPos = turretPos;
                        Bot.ACTION.unitCommand(raven.mover.unit(), Abilities.EFFECT_AUTO_TURRET, turretPos, false);
                        isTurretActive = true;
                    }
                }
                //check if base is cleared of obstructions
                else {
                    return testExpansionPos();
                }
            }
            //if autoturret is in the process of being cast
            else {
                setTurret();
            }
        }
        //turret has expired
        else if (!turret.isAlive()) {
            turret = null;
            raven.targetPos = expansionPos;
            isTurretActive = false;
        }
        //move raven on turret and wait for it to expire
        else {
            raven.onStep();
        }
        return false;
    }

    public boolean hasAutoturretQueued(Unit raven) {
        return raven.getOrders().stream().anyMatch(order -> order.getAbility() == Abilities.EFFECT_AUTO_TURRET);
    }

    private void removeRaven() {
        if (raven != null) {
            Ignored.remove(raven.mover.getTag());
            raven = null;
        }
    }

    private boolean testExpansionPos() {
        return !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_RAVEN, expansionPos, 6).isEmpty() &&
                Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, expansionPos);
    }

    private void setTurret() {
        turret = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_AUTO_TURRET, raven.mover.unit().getPosition().toPoint2d(), 5f)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Point2d getTurretPos(Point2d centerPoint) {
        List<Point2d> turretPosList = Position.getSpiralList(Position.toWholePoint(centerPoint), 4);

        List<QueryBuildingPlacement> queryList = turretPosList.stream()
                .map(p -> QueryBuildingPlacement.placeBuilding().useAbility(Abilities.EFFECT_AUTO_TURRET).on(p).build())
                .collect(Collectors.toList());

        List<Boolean> placementList = Bot.QUERY.placement(queryList);
        float turretRange = (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) ? 8 : 7;
        for (int i=0; i<placementList.size(); i++) {
            if (placementList.get(i) && UnitUtils.getDistance(blockers.get(0).unit(), turretPosList.get(i)) < turretRange) {
                return turretPosList.get(i);
            }
        }
        return null;
    }

    private Point2d getCenterPoint() {
        updateBlockers();
        return Position.midPointUnitInPools(blockers);
    }

    private void updateBlockers() {
        //every unit that can block a cc (including creep)
        blockers = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> { return
                ((enemy.unit().getType() == Units.ZERG_OVERLORD ||
                        enemy.unit().getType() == Units.ZERG_OVERLORD_TRANSPORT) &&
                        UnitUtils.getDistance(enemy.unit(), expansionPos) < 8.5f) ||
                ((enemy.unit().getType() == Units.ZERG_CREEP_TUMOR ||
                        enemy.unit().getType() == Units.ZERG_CREEP_TUMOR_BURROWED) &&
                        UnitUtils.getDistance(enemy.unit(), expansionPos) < 14.5f) ||
                (!enemy.unit().getFlying().orElse(true) &&
                        UnitUtils.getDistance(enemy.unit(), expansionPos) < 4f);
        });
    }

    // ************************************
    // ********* STATIC METHODS ***********
    // ************************************
    public static void onStep() {
        for (int i=0; i<expoClearList.size(); i++) {
            ExpansionClearing expo = expoClearList.get(i);
            if (expo.clearExpansion()) {
                remove(expo);
                i--;
            }
        }
    }

    public static void add(Point2d expansionPos) {
        if (!contains(expansionPos)) {
            expoClearList.add(new ExpansionClearing(expansionPos));
            Bot.ACTION.sendChat("Blocked expansion at: " + expansionPos, ActionChat.Channel.BROADCAST);
        }
    }

    public static void remove(ExpansionClearing expo) {
        Bot.ACTION.sendChat("Expansion cleared at: " + expo.expansionPos, ActionChat.Channel.BROADCAST);
        expoClearList.remove(expo);
        expo.removeRaven();
    }

    public static boolean contains(Point2d expansionPos) {
        return expoClearList.stream().anyMatch(e -> e.expansionPos.distance(expansionPos) < 1);
    }

    //if a visible unit is blocking an expansion (like a hatchery or stalker)
    public static boolean isVisiblyBlockedByUnit(Point2d expansionPos) {
        return Bot.OBS.getUnits(Alliance.ENEMY, u ->
                UnitUtils.getDistance(u.unit(), expansionPos) < 5 && //is within 5 distance
                        (u.unit().getDisplayType() == DisplayType.SNAPSHOT || //TODO: add not autoturret to snapshots
                                (!u.unit().getFlying().orElse(false) && //is ground unit
                                        u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.NOT_CLOAKED && //is not cloaked
                                        !u.unit().getType().toString().contains("BURROWED")))).isEmpty(); //is not burrowed
    }
}
