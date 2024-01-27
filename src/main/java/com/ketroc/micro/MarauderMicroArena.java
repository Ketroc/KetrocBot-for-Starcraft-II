package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.UnitAttribute;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MarauderMicroArena extends BasicUnitMicro {
    private Unit closestEnemyThreat;
    private boolean doStutterForward;
    List<Point2d> pathPosList;
    private boolean isOnRetreatPath;

    public MarauderMicroArena(Unit unit, List<Point2d> pathPosList) {
        super(unit, pathPosList.get(0), MicroPriority.DPS);
        this.pathPosList = new ArrayList<>(pathPosList);
    }

    public MarauderMicroArena(UnitInPool unit, List<Point2d> pathPosList) {
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
            //TODO: continue to next position
            updatePosList();
            targetPos = pathPosList.get(0);
        }
    }

    private void updatePosList() {
        //if nearly dead, switch to the side path
        if (!isOnRetreatPath && UnitUtils.getCurHp(unit.unit()) < 10) {
            setRetreatPath();
        }

        //remove current pos if near it and it's not the last position in the list
        if (pathPosList.size() > 1 && UnitUtils.getDistance(unit.unit(), pathPosList.get(0)) < 2) {
            pathPosList.remove(0);
        }
    }

    protected void setRetreatPath() {
        //TODO: set pathPosList to long path
        isOnRetreatPath = true;
    }

    @Override
    protected List<UnitInPool> getValidTargetList(boolean includeIgnoredTargets) {
        //TODO: return list of armored army units otherwise use default method
        List<UnitInPool> armoredArmyTargets = UnitUtils.getEnemyTargetsInRange(unit.unit()).stream()
                .filter(enemyUip -> UnitUtils.canAttack(enemyUip.unit()) && UnitUtils.getAttributes(enemyUip.unit()).contains(UnitAttribute.ARMORED))
                .collect(Collectors.toList());
        return armoredArmyTargets.isEmpty() ? super.getValidTargetList(false) : armoredArmyTargets;
    }

    @Override
    protected Point2d findDetourPos() {
        //step away from closest enemy threat
        return Position.towards(unit.unit(), closestEnemyThreat, -3);
    }

    @Override
    protected boolean doStutterForward(Unit attackUnit, Unit closestEnemyThreat) {
        if (closestEnemyThreat == null) {
            return true;
        }
        //doStutterForward if enemy outranges me (or is weaponless)
        double myAttackRange = UnitUtils.getAttackRange_NoRadius(attackUnit, closestEnemyThreat);
        double enemyAttackRange = UnitUtils.getAttackRange_NoRadius(closestEnemyThreat, attackUnit);

        return enemyAttackRange == 0 ||
                myAttackRange < enemyAttackRange ||
                myAttackRange + attackUnit.getRadius() + closestEnemyThreat.getRadius() - 0.3f < UnitUtils.getDistance(attackUnit, closestEnemyThreat) ||
                UnitUtils.isEnemyRetreating(closestEnemyThreat, attackUnit.getPosition().toPoint2d());
    }
}
