package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Tester;
import com.ketroc.terranbot.managers.ActionErrorManager;
import com.ketroc.terranbot.utils.Time;

import java.util.List;
import java.util.stream.Collectors;

public class TestingBot extends Bot {
    public static UnitInPool bunker;
    public static UnitInPool marine;
    public float z;
    public Unit commandCenter;

    public TestingBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    @Override
    public void onGameStart() {
        super.onGameStart();

        debug().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
        int playerId = observation().getPlayerId();
//        debug().debugCreateUnit(Units.NEUTRAL_MINERAL_FIELD, Point2d.of(108.5f, 100.5f), 0, 1);
//        debug().debugCreateUnit(Units.TERRAN_CYCLONE, Point2d.of(100, 100), playerId, 1);
//        debug().debugCreateUnit(Units.TERRAN_SUPPLY_DEPOT, Point2d.of(40, 40), playerId, 1);
////        debug().debugCreateUnit(Units.PROTOSS_PHOENIX, Point2d.of(114, 120), observation().getGameInfo().getPlayersInfo().iterator().next().getPlayerId(), 1);
        debug().sendDebug();

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
        super.onStep();
        ActionErrorManager.onStep();
//        if (Time.nowFrames() == 10) {
//            Unit scv = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV).get(0).unit();
//            System.out.println("Bot.QUERY.pathingDistance(scv, Point2d.of(140, 130)) = " + Bot.QUERY.pathingDistance(scv, Point2d.of(140, 130)));
//            System.out.println("Bot.QUERY.pathingDistance(scv, Point2d.of(40, 40)) = " + Bot.QUERY.pathingDistance(scv, Point2d.of(40, 40)));
//        }

//        List<Unit> scvs = observation().getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV).stream().map(UnitInPool::unit).collect(Collectors.toList());
//        actions().unitCommand(scvs.remove(0), Abilities.BUILD_COMMAND_CENTER, Point2d.of(1, 250), false);
////        List<UnitInPool> scvs = observation().getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV &&
////                !scv.unit().getOrders().isEmpty());
////
////        if (!scvs.isEmpty()) {
////            Tag mineralNode = scvs.get(0).unit().getOrders().get(0).getTargetedUnitTag().orElse(null);
////
////            if (mineralNode != null) {
////                System.out.println(observation().getUnit(mineralNode).unit().getPosition().toPoint2d());
////            }
////        }
        if (Time.nowFrames() == 100) {
            long start = System.currentTimeMillis();
            List<Point2d> expansions = Tester.calculateExpansionLocations(OBS);
            System.out.println(System.currentTimeMillis()-start);
            List<Unit> scvs = OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV).stream().map(UnitInPool::unit).collect(Collectors.toList());
            //expansions.forEach(p -> ACTION.unitCommand(scvs.remove(0), Abilities.BUILD_COMMAND_CENTER, p, false));
        }


        List<UnitInPool> cyclone = Bot.OBS.getUnits(Alliance.SELF, c -> c.unit().getType() == Units.TERRAN_CYCLONE);
        int w=0;

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
        ACTION.sendActions();
        DEBUG.sendDebug();
        if (Time.nowFrames() == 100) {
            int q = 1;
        }

    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
    }


}