package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.Set;

public class MarineOffense extends Marine {
    private static final Set<Units> DONT_CHASE_TYPES = Set.of(
            Units.TERRAN_REAPER, Units.TERRAN_HELLION,
            Units.PROTOSS_ADEPT, Units.PROTOSS_ADEPT_PHASE_SHIFT, Units.PROTOSS_ORACLE, Units.PROTOSS_INTERCEPTOR);

    private Unit closestEnemyThreat;
    private boolean doStutterForward;

    public MarineOffense(Unit unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    public MarineOffense(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        closestEnemyThreat = getClosestEnemyThreat();
        setDoStutterForward();
        setTargetPos();
        super.onStep();
    }

    private void setDoStutterForward() {
        if (closestEnemyThreat == null) {
            doStutterForward = true;
            return;
        }
        //doStutterForward if enemy outranges me (or is weaponless)
        double marineAttackRange = 5.375 + closestEnemyThreat.getRadius();
        doStutterForward = UnitUtils.getDistance(unit.unit(), closestEnemyThreat) > marineAttackRange ||
                UnitUtils.getGroundAttackRange(closestEnemyThreat) + 0.375f > marineAttackRange;
    }

    @Override
    public boolean isSafe() {
        return doStutterForward; //||
                //!InfluenceMaps.getValue(InfluenceMaps.pointInMarineRange, unit.unit().getPosition().toPoint2d());
    }

    private Unit getClosestEnemyThreat() {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                !enemy.unit().getHallucination().orElse(false) &&
                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                (!ArmyManager.doOffense || !DONT_CHASE_TYPES.contains(enemy.unit().getType())) &&
                enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                (!UnitUtils.isSnapshot(enemy.unit()) || enemy.unit().getType() == Units.PROTOSS_PYLON) &&
                (UnitUtils.canAttackGround(enemy.unit()) || enemy.unit().getType() == Units.PROTOSS_PYLON) &&
                UnitUtils.getDistance(unit.unit(), enemy.unit()) < 15)
                .stream()
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(unit.unit(), enemy.unit())))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    @Override
    protected void setTargetPos() {
        //if none, then set to attackGroundPos
        if (closestEnemyThreat != null) {
            targetPos = closestEnemyThreat.getPosition().toPoint2d();
        }
        else if (ArmyManager.attackEitherPos == null) {
            setFinishHimTarget();
        }
        else {
            targetPos = ArmyManager.attackEitherPos;
        }
    }
}
