package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.micro.Cyclone;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        debug().debugCreateUnit(Units.TERRAN_GHOST_ACADEMY, mySpawnPos, myId, 1);
        debug().debugCreateUnit(Units.TERRAN_REFINERY, Position.towards(enemySpawnPos, mySpawnPos, -4), myId, 1);
        debug().debugCreateUnit(Units.TERRAN_REFINERY, Position.towards(enemySpawnPos, mySpawnPos, -4), myId, 1);
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
        if (Time.nowFrames() % Strategy.STEP_SIZE != 0) {
            return;
        }

        if (Time.nowFrames() == 100) {
            observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_GHOST_ACADEMY)
                    .forEach(u -> actions().unitCommand(u.unit(), Abilities.BUILD_NUKE, false));
        }

        if (Time.nowFrames() == 200) {
            observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_GHOST)
                    .forEach(u -> actions().unitCommand(u.unit(), Abilities.EFFECT_NUKE_CALL_DOWN, enemySpawnPos, false));
        }

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
}