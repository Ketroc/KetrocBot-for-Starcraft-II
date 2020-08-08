package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.*;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.spatial.PointI;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.UnitUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestingBot  extends S2Agent {
    public static UnitInPool bunker;
    public static UnitInPool marine;
    public float z;
    public Unit commandCenter;

    @Override
    public void onGameStart() {
//        debug().debugGiveAllTech().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
//        int playerId = observation().getPlayerId();
//        debug().debugCreateUnit(Units.TERRAN_BARRACKS, Point2d.of(100, 100), playerId, 1);
////        debug().debugCreateUnit(Units.TERRAN_HELLION, Point2d.of(114, 120), playerId, 10);
////        debug().debugCreateUnit(Units.PROTOSS_PHOENIX, Point2d.of(114, 120), observation().getGameInfo().getPlayersInfo().iterator().next().getPlayerId(), 1);
//        debug().sendDebug();

//        commandCenter = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
//        z = commandCenter.getPosition().getZ();
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println((Units)unit.getType() + ".add(Point2d.of(" + unit.getPosition().getX() + "f, " + unit.getPosition().getY() + "f));");

    }

    @Override
    public void onStep() {
        //testing how to cancel a unit production
//        if (observation().getGameLoop() > 20) {
//            Unit barracks = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BARRACKS).get(0).unit();
//            if (barracks.getActive().orElse(true)) {
//                actions().unitCommand(barracks, Abilities.CANCEL_LAST, false);
//                System.out.println("cancelled rax unit");
//            }
//
//            Unit cc = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
//            if (cc.getActive().orElse(true)) {
//                actions().unitCommand(cc, Abilities.CANCEL, false);
//                System.out.println("cancelled cc unit");
//            }
//        }


//        if (observation().getGameLoop() > 705) {
//            List<Unit> lowHellions = UnitUtils.unitInPoolToUnitList(observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_HELLION && z - u.unit().getPosition().getZ() > 1));
//            List<Unit> highHellions = UnitUtils.unitInPoolToUnitList(observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_HELLION && z - u.unit().getPosition().getZ() <= 1));
//            Unit medivac = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MEDIVAC).get(0).unit();
//            if (medivac.getCargoSpaceTaken().orElse(0) > 0) {
//                actions().unitCommand(medivac, Abilities.UNLOAD_ALL_AT_MEDIVAC, medivac, false);
//            }
//            if (!lowHellions.isEmpty()) {
//                actions().unitCommand(lowHellions, Abilities.SMART, medivac, false);
//            }
//            if (!highHellions.isEmpty()) {
//                actions().unitCommand(highHellions, Abilities.SMART, commandCenter, false);
//            }
//        }




//        if (observation().getGameLoop() == 305) {
//            Set<Tag> bunkers = new HashSet<>();
//            bunkers.add(observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BUNKER).get(0).getTag());
//            actions().unitCommand(bunkers, Abilities.UNLOAD_UNIT_BUNKER, false);
//            actions().sendActions();
//            System.out.println("done");
//        }
//        List<UnitInPool> enemies = observation().getUnits(Alliance.ENEMY);
//        if (!enemies.isEmpty()) {
//            int x = 0;
//        }


//        if (observation().getGameLoop() == 30) {
//            bunker = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MEDIVAC).get(0);
//            marine = observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MARINE).get(0);
//        }
//        if (observation().getGameLoop() == 300) {
//            List<Unit> both = new ArrayList<>();
//            both.add(bunker.unit());
//            both.add(marine.unit());
//            actions().unitCommand(both, Abilities.CANCEL_QUEUE1, false);
//            actions().sendActions();
//        }
//        if (observation().getGameLoop() == 305) {
//            actions().unitCommand(marine.unit(), Abilities.CANCEL_QUEUE1, false);
//            actions().sendActions();
//        }
//        if (observation().getGameLoop() == 310) {
//            actions().unitCommand(bunker.unit(), Abilities.CANCEL_QUEUE1, false);
//            actions().sendActions();
//        }
//        if (observation().getGameLoop() == 315) {
//            actions().unitCommand(bunker.unit(), Abilities.CANCEL_QUEUE1, marine.unit(), false);
//            actions().sendActions();
//        }
//        if (observation().getGameLoop() == 320) {
//            actions().unitCommand(marine.unit(), Abilities.CANCEL_QUEUE1, bunker.unit(), false);
//            actions().sendActions();
//        }
//        if (observation().getGameLoop() == 325) {
//            actions().unitCommand(marine.getTag(), Abilities.CANCEL_QUEUE1, bunker.unit(), false);
//            actions().sendActions();
//            System.out.println("done");
//        }
//        //print z coordinate
//        List<UnitInPool> scvs = observation().getUnits(Alliance.SELF,
//                u -> u.unit().getType() == Units.TERRAN_SCV &&
//                !u.unit().getOrders().isEmpty() &&
//                u.unit().getOrders().get(0).getAbility() == Abilities.MOVE);
//        if (!scvs.isEmpty()) {
//            System.out.println("z: " + scvs.get(0).unit().getPosition().getZ());
//        }
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
    }


}