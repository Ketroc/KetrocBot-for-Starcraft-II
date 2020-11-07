package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.ActionInterface;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;

public class _12PoolBot extends S2Agent {
    public static final List<Units> MINERAL_NODE_TYPE = new ArrayList<>(Arrays.asList(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750));

    public static ObservationInterface obs;
    public static ActionInterface action;

    public static UnitInPool mainHatchery;
    public static Point2d enemyMainPos;
    public static Point2d poolPos;
    public static UnitInPool mineralNode;

    public static final Map<Units, Integer> myUnitCounts = new HashMap<>();
    public static final Map<Units, List<Unit>> myUnits = new HashMap<>();

    @Override
    public void onGameStart() {
        obs = observation();
        action = actions();

        mainHatchery = obs.getUnits(Alliance.SELF, unitInPool -> unitInPool.unit().getType() == Units.ZERG_HATCHERY).get(0);
        enemyMainPos = obs.getGameInfo().getStartRaw().get().getStartLocations().iterator().next(); //only works on 2player map
        poolPos = Point2d.of(mainHatchery.unit().getPosition().getX(), mainHatchery.unit().getPosition().getY() + 9);
        mineralNode = obs.getUnits(Alliance.NEUTRAL, unitInPool -> {
            return unitInPool.unit().getType() instanceof Units &&
                    MINERAL_NODE_TYPE.contains((Units)unitInPool.unit().getType()) &&
                    unitInPool.unit().getDisplayType() == DisplayType.VISIBLE;
        }).get(0);
    }

    @Override
    public void onStep() {
        //create map of all my units and unit counts
        myUnitCounts.clear();
        myUnits.clear();
        for (UnitInPool uip : obs.getUnits(Alliance.SELF)) {
            Units unitType = (Units)uip.unit().getType();
            myUnitCounts.put(unitType, myUnitCounts.getOrDefault(unitType, 0) + 1);
            if (!myUnits.containsKey(unitType)) {
                myUnits.put(unitType, new ArrayList<>());
            }
            myUnits.get(unitType).add(uip.unit());
        }

        //build pool
        if (myUnitCounts.getOrDefault(Units.ZERG_SPAWNING_POOL, 0) == 0) {
            if (obs.getMinerals() >= 200) {
                action.unitCommand(myUnits.get(Units.ZERG_DRONE).get(0), Abilities.BUILD_SPAWNING_POOL, poolPos, false);
            }
            return;
        }

        //build drones to 13
        if (obs.getFoodWorkers() < 13) {
            List<Unit> larvaList = myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList());
            if (!larvaList.isEmpty() && obs.getMinerals() >= 50) {
                action.unitCommand(larvaList.get(0), Abilities.TRAIN_DRONE, false)
                        .unitCommand(larvaList.get(0), Abilities.SMART, mineralNode.unit(), true);
            }
            return;
        }

        //build overlord
        if (myUnitCounts.get(Units.ZERG_OVERLORD) < 2) {
            if (obs.getMinerals() >= 100 && !isUnitInEgg(Abilities.TRAIN_OVERLORD)) {
                List<Unit> larvaList = myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList());
                action.unitCommand(larvaList.get(0), Abilities.TRAIN_OVERLORD, false);
            }
            return;
        }

        //wait for pool to complete
        if (myUnits.get(Units.ZERG_SPAWNING_POOL).get(0).getBuildProgress() < 1.0f) {
            return;
        }

        //build lings
        for (Unit larva : myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList())) {
            if (obs.getMinerals() >= 50) {
                action.unitCommand(larva, Abilities.TRAIN_ZERGLING, false);
            }
            else {
                break;
            }
        }

        //send lings
        for (Unit zergling : myUnits.getOrDefault(Units.ZERG_ZERGLING, Collections.emptyList())) {
            if (zergling.getOrders().isEmpty()) {
                action.unitCommand(zergling, Abilities.ATTACK, enemyMainPos,false);
            }
        }

        //send actions
        action.sendActions();
    }

    private boolean isUnitInEgg(Abilities trainUnitType) {
        List<Unit> eggs = myUnits.getOrDefault(Units.ZERG_EGG, Collections.emptyList());
        for (Unit egg : eggs) {
            if (UnitUtils.getOrder(egg) == trainUnitType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {

    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {

    }
}