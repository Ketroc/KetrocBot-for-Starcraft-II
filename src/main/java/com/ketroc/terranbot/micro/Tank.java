package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.*;

import java.util.List;

public class Tank extends BasicUnitMicro {
    private long lastActiveFrame;

    public Tank(UnitInPool unit, Point2d targetPos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
    }

    public Tank(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    @Override
    public UnitInPool selectTarget() {
        Unit tank = unit.unit();

        //use basic micro for unsieged tank
        if (tank.getType() == Units.TERRAN_SIEGE_TANK) {
            return super.selectTarget();
        }

        //if no targets in range
        if (UnitUtils.getEnemyTargetsInRange(tank).isEmpty()) {
            return null;
        }

        float xTank = tank.getPosition().getX();
        float yTank = tank.getPosition().getY();

        int xMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getX();
        int xMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX());
        int yMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getY();
        int yMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY());
        int range = 13;
        int xStart = Math.max(Math.round(2*(xTank - range)), xMin);
        int yStart = Math.max(Math.round(2*(yTank - range)), yMin);
        int xEnd = Math.min(Math.round(2*(xTank + range)), xMax);
        int yEnd = Math.min(Math.round(2*(yTank + range)), yMax);


        //get x,y of max value
        int bestValueX = -1;
        int bestValueY = -1;
        int bestValue = 0;
        for (int x = xStart; x <= xEnd; x++) {
            for (int y = yStart; y <= yEnd; y++) {
                float distance = Position.distance(x / 2f, y / 2f, xTank, yTank);
                if (InfluenceMaps.pointPFTargetValue[x][y] > bestValue &&
                        distance < range && distance > 4f) {
                    bestValueX = x;
                    bestValueY = y;
                    bestValue = InfluenceMaps.pointPFTargetValue[x][y];
                }
            }
        }

        //get unit based on best (x,y)
        UnitInPool bestTargetUnit = null;
        if (bestValue == 0) {
            if (LocationConstants.opponentRace == Race.ZERG) {
                bestTargetUnit = UnitUtils.getClosestEnemyUnitOfType(Units.ZERG_CHANGELING_MARINE, tank.getPosition().toPoint2d());
            }
        }
        else {
            Point2d bestTargetPos = Point2d.of(bestValueX / 2f, bestValueY / 2f);

            //get enemy Unit near bestTargetPos
            List<UnitInPool> enemyTargets = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                    UnitUtils.getDistance(enemy.unit(), bestTargetPos) < 1f && !enemy.unit().getFlying().orElse(false));
            if (!enemyTargets.isEmpty()) {
                bestTargetUnit = enemyTargets.get(0);
            }
        }
        return bestTargetUnit;
    }

    protected boolean siegeUpMicro() {
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 1 || !getEnemiesInRange(13).isEmpty()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE, false);
            return true;
        }
        return false;
    }

    protected boolean unsiegeMicro() {
        if (unit.unit().getWeaponCooldown().orElse(1f) == 0f &&
                UnitUtils.getDistance(unit.unit(), targetPos) > 1 &&
                getEnemiesInRange(15).isEmpty()) {
            if (lastActiveFrame + 150 > Time.nowFrames()) {
                ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_UNSIEGE, false);
                return true;
            }
            return false;
        }
        lastActiveFrame = Time.nowFrames();
        return false;
    }

    protected List<UnitInPool> getEnemiesInRange(float range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                (!UnitUtils.canMove(enemy.unit())
                        ? UnitUtils.getDistance(enemy.unit(), unit.unit()) <= 13 + enemy.unit().getRadius()
                        : UnitUtils.getDistance(enemy.unit(), unit.unit()) <= range + enemy.unit().getRadius()) &&
                !enemy.unit().getFlying().orElse(true) &&
                !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                !enemy.unit().getHallucination().orElse(false) &&
                enemy.unit().getDisplayType() == DisplayType.VISIBLE);
    }

    //if enemy sieged tank nearby and it can't see
    protected Unit getEnemyTankToSiege() {
        Unit enemyTank = UnitUtils.getClosestEnemyOfType(Units.TERRAN_SIEGE_TANK_SIEGED, unit.unit().getPosition().toPoint2d());
        if (enemyTank == null || enemyTank.getDisplayType() == DisplayType.HIDDEN || UnitUtils.getDistance(enemyTank, unit.unit()) > 17) {
            return null;
        }
        //edge of where my tank will siege (test vision here)
        Point2d enemyVisionPos = Position.towards(enemyTank.getPosition().toPoint2d(),
                unit.unit().getPosition().toPoint2d(),
                12.9f + enemyTank.getRadius());

        //check if enemy can see my siege position
        if (InfluenceMaps.getValue(InfluenceMaps.pointInEnemyVision, enemyVisionPos)) {
            return null;
        }

        return enemyTank;

    }
}
