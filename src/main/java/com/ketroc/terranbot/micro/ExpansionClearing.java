package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;
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
    public long checkFrame;
    public boolean isTurretActive;

    public ExpansionClearing(Point2d expansionPos) {
        this.expansionPos = expansionPos;
        StructureScv.cancelProduction(Units.TERRAN_COMMAND_CENTER, expansionPos);
    }

    private void addRaven() {
        Tag nearestRaven = GameCache.ravenList.stream()
                .filter(raven -> raven.getEnergy().orElse(0f) > 49)
                .filter(raven -> !Ignored.contains(raven.getTag()))
                .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit, expansionPos)))
                .map(Unit::getTag)
                .orElse(null);
        if (nearestRaven != null) {
            this.raven = new BasicMover(Bot.OBS.getUnit(nearestRaven), expansionPos);
            Ignored.add(new IgnoredUnit(nearestRaven));
        }
    }

    public boolean clearExpansion() {
        //remove dead raven
        if (raven != null && !raven.mover.isAlive()) {
            removeRaven();
        }
        //get a new raven
        if (raven == null) {
            addRaven();
        }
        //raven is travelling to expansion
        else if (turret == null && UnitUtils.getDistance(raven.mover.unit(), expansionPos) > 1 && !isTurretActive) {
            raven.onStep();
        }
        else if (turret == null) {
            //if autoturret cast is needed
            if (!isTurretActive) {
                Point2d centerPoint = getCenterPoint();
                //cast turret
                if (centerPoint != null) {
                    Point2d turretPos = getTurretPos(centerPoint);
                    if (turretPos != null) {
                        raven.targetPos = turretPos;
                        Bot.ACTION.unitCommand(raven.mover.unit(), Abilities.EFFECT_AUTO_TURRET, turretPos, false);
                        isTurretActive = true;
                        checkFrame = Bot.OBS.getGameLoop() + 120;
                    }
                }
                //check if base is cleared of obstructions
                else if (Bot.OBS.getGameLoop() > checkFrame) {
                    boolean result = testExpansionPos();
                    if (result) {
                        Bot.ACTION.sendChat("Blocked base cleared at: " + expansionPos, ActionChat.Channel.BROADCAST);
                        removeRaven();
                    }
                    else {
                        checkFrame = Bot.OBS.getGameLoop() + 120;
                    }
                    return result;
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
        Ignored.remove(raven.mover.getTag());
        raven = null;
    }

    private boolean testExpansionPos() {
        return !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_COMMAND_CENTER, expansionPos, 1).isEmpty() ||
                Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, expansionPos);
    }

    private void setTurret() {
        turret = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_AUTO_TURRET, expansionPos, 14.5f)
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
        if (placementList.contains(true)) {
            return turretPosList.get(placementList.indexOf(true));
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
        expoClearList.removeIf(e -> e.clearExpansion());
    }

    public static void add(Point2d expansionPos) {
        if (!contains(expansionPos)) {
            expoClearList.add(new ExpansionClearing(expansionPos));
            Bot.ACTION.sendChat("Blocked base discovered at: " + expansionPos, ActionChat.Channel.BROADCAST);
        }
    }

    public static boolean contains(Point2d expansionPos) {
        return expoClearList.stream().anyMatch(e -> e.expansionPos.distance(expansionPos) < 1);
    }

}
