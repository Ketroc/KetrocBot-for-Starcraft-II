import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.response.ResponseGameInfo;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

class Bot extends S2Agent {
    LinkedList<StructureToCreate> toBuild = new LinkedList<StructureToCreate>();
    public static ObservationInterface OBS;
    boolean makeGas = true; //TODO: delete, for testing only


    @Override
    public void onGameStart() {
        OBS = observation();

        //set map and spawn location
        Unit cc1Unit = observation().getUnits(Alliance.SELF, c -> c.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();
        LocationConstants.init(MapNames.TRITON, (cc1Unit.getPosition().getY() > 100) ? true : false);


        toBuild.add(new StructureToCreate(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.DEPOT1));
        toBuild.add(new StructureToCreate(Units.TERRAN_BARRACKS, LocationConstants.BARRACKS));
        toBuild.add(new StructureToCreate(Units.TERRAN_COMMAND_CENTER, LocationConstants.BASE2.getLocation()));
        toBuild.add(new StructureToCreate(Units.TERRAN_BUNKER, LocationConstants.BUNKER1));
        toBuild.add(new StructureToCreate(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.DEPOT2));
        toBuild.add(new StructureToCreate(Units.TERRAN_BUNKER, LocationConstants.BUNKER2));
        toBuild.add(new StructureToCreate(Units.TERRAN_REFINERY, LocationConstants.BASE2.getGas1()));
        toBuild.add(new StructureToCreate(Units.TERRAN_REFINERY, LocationConstants.BASE2.getGas2()));

    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {

    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        System.out.println(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() +
                ") at: " + currentGameTime());
    }

    @Override
    public void onStep() {
        if (!toBuild.isEmpty()) {
            if (toBuild.getFirst().buildStructure(actions())) {
                LocationConstants.BASE2.initGases();
                toBuild.removeFirst();
            }
        }
//
//        if (OBS.getMinerals() > 75 && makeGas) {
//            List<UnitInPool> scvList = OBS.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV);
//            List<UnitInPool> gasList = Bot.OBS.getUnits(Alliance.NEUTRAL, gas -> gas.unit().getType() == Units.NEUTRAL_VESPENE_GEYSER);
//            actions().unitCommand(scvList.get(0).unit(), Abilities.BUILD_REFINERY, gasList.get(0).unit(), false);
//            makeGas = false;
//        }
    } // end onStep()

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {

    }

    public boolean afterTime(String time) {
        long seconds = convertStringToSeconds(time);
        return observation().getGameLoop()/22.4 > seconds;
    }

    public boolean beforeTime(String time) {
        long seconds = convertStringToSeconds(time);
        return observation().getGameLoop()/22.4 < seconds;
    }

    public long convertStringToSeconds(String time) {
        String[] arrTime = time.split(":");
        return Integer.parseInt(arrTime[0])*60 + Integer.parseInt(arrTime[1]);
    }
    public String convertGameLoopToStringTime(long gameLoop) {
        return convertSecondsToString(Math.round(gameLoop/22.4));
    }

    public String convertSecondsToString(long seconds) {
        return seconds/60 + ":" + String.format("%02d", seconds%60);
    }
    public String currentGameTime() {
        return convertGameLoopToStringTime(observation().getGameLoop());
    }

    public Unit findScvNearestBase(Unit cc) {
        return findNearestScv(cc.getPosition().toPoint2d(), true);
    }

    public Unit findNearestScv(Point2d pt, boolean isHoldingMinerals) {
        List<UnitInPool> scvList;
        scvList = observation().getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && //is scv
                ((isHoldingMinerals) ? scv.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) : true));  //is holding minerals

        UnitInPool closestScv = scvList.get(0);
        double closestDistance = pt.distance(closestScv.unit().getPosition().toPoint2d());
        scvList.remove(0);
        for (UnitInPool scv : scvList) {
            double curDistance = pt.distance(scv.unit().getPosition().toPoint2d());
            if (curDistance < closestDistance) {
                closestScv = scv;
                closestDistance = curDistance;
            }
        }

        return closestScv.unit();
    }

    public Unit findNearestScv(Point2d pt) {
        return findNearestScv(pt, false);
    }

}
