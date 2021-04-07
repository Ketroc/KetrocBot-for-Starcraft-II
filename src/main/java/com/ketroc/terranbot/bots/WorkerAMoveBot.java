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

public class WorkerAMoveBot extends S2Agent {
    public static ObservationInterface obs;
    public static ActionInterface action;

    @Override
    public void onGameStart() {
        obs = observation();
        action = actions();

        Point2d enemyMainPos = obs.getGameInfo().getStartRaw().get().getStartLocations().iterator().next();
        List<UnitInPool> myWorkers = obs.getUnits(Alliance.SELF, u -> UnitUtils.WORKER_TYPE.contains(u.unit().getType()));
        action.unitCommand(UnitUtils.toUnitList(myWorkers), Abilities.ATTACK, enemyMainPos, false);
    }

    @Override
    public void onStep() {

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