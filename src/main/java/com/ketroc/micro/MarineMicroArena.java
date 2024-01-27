package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.bots.MicroArenaBot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.ArmyManager;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class MarineMicroArena extends Marine {
    private Unit closestEnemyThreat;
    private boolean doStutterForward;
    List<Point2d> pathPosList;

    public MarineMicroArena(Unit unit, List<Point2d> pathPosList) {
        super(unit, pathPosList.get(0), MicroPriority.DPS);
        this.pathPosList = new ArrayList<>(pathPosList);
    }

    public MarineMicroArena(UnitInPool unit, List<Point2d> pathPosList) {
        super(unit, pathPosList.get(0), MicroPriority.DPS);
        this.pathPosList = new ArrayList<>(pathPosList);
    }

    @Override
    public void onStep() {
        closestEnemyThreat = getClosestEnemyThreatToGround();
        doStutterForward = doStutterForward(unit.unit(), closestEnemyThreat);
        setTargetPos();
        super.onStep();
    }

    @Override
    public boolean isSafe(Point2d pos) {
        return doStutterForward;
    }

    @Override
    protected void setTargetPos() {
        // attack any threats within 15 range (army, workers, pylons -- excluding reapers/hellions/oracles/adepts)
        if (closestEnemyThreat != null) {
            targetPos = closestEnemyThreat.getPosition().toPoint2d();
        }
        else {
            updatePosList();
            targetPos = pathPosList.get(0);
        }
    }

    private void updatePosList() {
        //remove current pos if near it and it's not the last position in the list
        if (pathPosList.size() > 1 && UnitUtils.getDistance(unit.unit(), pathPosList.get(0)) < 2) {
            pathPosList.remove(0);
        }
    }

    @Override
    protected Point2d findDetourPos() {
        //step away from closest enemy threat
        return Position.towards(unit.unit(), closestEnemyThreat, -3);
    }
}
