package com.ketroc.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.launchers.Launcher;
import com.ketroc.utils.LocationConstants;

public class EnemyDebugTestBot extends S2Agent {
    public int myId;
    public int enemyId;
    public Point2d mySpawnPos;
    public Point2d enemySpawnPos;

    @Override
    public void onGameStart() {
        myId = observation().getPlayerId();
        enemyId = observation().getGameInfo().getPlayersInfo().stream()
                .map(PlayerInfo::getPlayerId)
                .filter(id -> id != myId)
                .findFirst()
                .get();
        mySpawnPos = observation().getStartLocation().toPoint2d();
        enemySpawnPos = observation().getGameInfo().getStartRaw().get().getStartLocations().stream()
                .filter(pos -> mySpawnPos.distance(pos) > 10)
                .findFirst().get();

        //debug().debugFastBuild().debugGiveAllTech().debugGiveAllResources();
//        debug().debugCreateUnit(Units.TERRAN_GHOST_ACADEMY, mySpawnPos, myId, 1);
//        debug().debugCreateUnit(Units.TERRAN_REFINERY, Position.towards(enemySpawnPos, mySpawnPos, -4), myId, 1);
//        debug().debugCreateUnit(Units.TERRAN_REFINERY, Position.towards(enemySpawnPos, mySpawnPos, -4), myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_VOIDRAY, Position.towards(enemySpawnPos, mySpawnPos, 9), myId, 1);
//        debug().debugCreateUnit(Units.TERRAN_COMMAND_CENTER, LocationConstants.baseLocations.get(2), myId, 1);
//        debug().debugCreateUnit(Units.PROTOSS_TEMPEST, mySpawnPos, enemyId, 1);
//        debug().debugCreateUnit(Units.TERRAN_RAVEN, mySpawnPos, myId, 1);
        debug().sendDebug();


    }


    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        int w = 293847;
    }

    @Override
    public void onBuildingConstructionComplete(UnitInPool unitInPool) {

    }

    @Override
    public void onStep() {

        if (at(100)) {
            //debug().debugCreateUnit(Units.ZERG_HATCHERY, LocationConstants.baseLocations.get(1), myId, 1);
        }

//        if (at(1320)) {
//            observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV)
//                    .stream()
//                    .findFirst()
//                    .ifPresent(u -> actions().unitCommand(u.unit(), Abilities.BUILD_BUNKER, LocationConstants.BUNKER_NATURAL, false));
//            Point2d marinePos = Position.towards(LocationConstants.BUNKER_NATURAL, LocationConstants.enemyMainBaseMidPos, 3);
//            debug().debugCreateUnit(Units.TERRAN_MARINE, marinePos, myId, 1);
//        }


        actions().sendActions();
        debug().sendDebug();
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
    }

    @Override
    public void onGameEnd() {
    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {
    }

    public boolean at(long frame) {
        return observation().getGameLoop() >= frame && observation().getGameLoop() < frame + Launcher.STEP_SIZE;
    }
}