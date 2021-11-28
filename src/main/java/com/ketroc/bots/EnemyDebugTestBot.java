package com.ketroc.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.launchers.Launcher;

public class EnemyDebugTestBot extends S2Agent {
    public int myId;
    public int enemyId;
    public Point2d mySpawnPos;
    public Point2d enemySpawnPos;

    public long nukeStartFrame;
    public long nukeEffectEndFrame;
    public long nukeEndFrame;

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

//        if (at(100)) {
//            debug().debugCreateUnit(Units.PROTOSS_PHOENIX, Point2d.of(100, 100), enemyId, 1);
//            debug().debugCreateUnit(Units.TERRAN_GHOST_ACADEMY, Point2d.of(100, 100), myId, 1);
//            debug().debugCreateUnit(Units.TERRAN_FACTORY, Point2d.of(100, 100), myId, 1);
//        }

//        if (at(1320)) {
//            observation().getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV)
//                    .stream()
//                    .findFirst()
//                    .ifPresent(u -> actions().unitCommand(u.unit(), Abilities.BUILD_BUNKER, LocationConstants.BUNKER_NATURAL, false));
//            Point2d marinePos = Position.towards(LocationConstants.BUNKER_NATURAL, LocationConstants.enemyMainBaseMidPos, 3);
//            debug().debugCreateUnit(Units.TERRAN_MARINE, marinePos, myId, 1);
//        }

//        if (nukeStartFrame == 0 && observation().getEffects().stream().anyMatch(effect -> effect.getEffect() == Effects.NUKE_PERSISTENT)) {
//            nukeStartFrame = observation().getGameLoop();
//        }
//
//        if (nukeStartFrame != 0 && nukeEffectEndFrame == 0 && observation().getEffects().stream().noneMatch(effect -> effect.getEffect() == Effects.NUKE_PERSISTENT)) {
//            nukeEffectEndFrame = observation().getGameLoop();
//        }
//
//        if (nukeEndFrame == 0 && observation().getUnits(Alliance.SELF).stream().anyMatch(u -> u.unit().getHealth().get() < u.unit().getHealthMax().get())) {
//            nukeEndFrame = observation().getGameLoop();
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