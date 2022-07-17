package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.List;

public class Tank extends BasicUnitMicro {
    public static final float TARGET_POS_RADIUS = 1.3f;
    public static boolean isLongDelayedUnsiege = true;

    protected long lastActiveFrame; //last frame that this tank was sieged with an enemy target
    protected int framesDelayToUnSiege = 0;

    public Tank(UnitInPool unit, Point2d targetPos) {
        this(unit, targetPos, MicroPriority.SURVIVAL);
    }

    public Tank(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
        framesDelayToUnSiege = getFrameDelayToUnsiege();
    }

    public void siege() {
        ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_SIEGE_MODE,false);
        groundAttackRange = 13 + unit.unit().getRadius() + 0.25f;
    }

    public void unsiege() {
        ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_UNSIEGE,false);
        groundAttackRange = 7 + unit.unit().getRadius() + 0.25f;
    }

    @Override
    public UnitInPool selectTarget() { //TODO: replace with version that prioritizes 1shot kills
        Unit tank = unit.unit();

        //use basic micro for unsieged tank
        if (tank.getType() == Units.TERRAN_SIEGE_TANK) {
            return super.selectTarget();
        }

        //if no targets in range
        List<UnitInPool> enemyTargetsInRange = getEnemyTargetsInRange(12.8f);
        if (enemyTargetsInRange.isEmpty()) {
            return null;
        }

        //prioritize enemy tanks
        UnitInPool weakestEnemyTankInRange = enemyTargetsInRange.stream()
                .filter(u -> UnitUtils.SIEGE_TANK_TYPE.contains(u.unit().getType()))
                .min(Comparator.comparing(u -> u.unit().getHealth().orElse(175f)))
                .orElse(null);
        if (weakestEnemyTankInRange != null) {
            return weakestEnemyTankInRange;
        }

        //find largest splash damage
        float xTank = tank.getPosition().getX();
        float yTank = tank.getPosition().getY();

        int xMin = 0;
        int xMax = InfluenceMaps.toMapCoord(PosConstants.SCREEN_TOP_RIGHT.getX());
        int yMin = 0;
        int yMax = InfluenceMaps.toMapCoord(PosConstants.SCREEN_TOP_RIGHT.getY());
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
                double distance = Position.distance(x / 2f, y / 2f, xTank, yTank);
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
            if (PosConstants.opponentRace == Race.ZERG) {
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

    protected boolean doSiegeUp() {
        //don't siege if retreating
        if (!ArmyManager.doOffense && UnitUtils.getDistance(unit.unit(), ArmyManager.attackGroundPos) > 15) { //attackGroundPos is home pos or enemy units near my bases
            return false;
        }

        //don't siege if on enemy ramp
        if (UnitUtils.getDistance(unit.unit(), PosConstants.enemyRampPos) < 3.5f) {
            return false;
        }

        int rangeToSiege = (ArmyManager.doOffense && UnitUtils.numMyOffensiveSiegedTanks() < 2) ? 18 : 13;
        return !getEnemyTargetsInRange(rangeToSiege).isEmpty();
    }

    //alternate between ~1.5s and ~6s for unsiege delay to stagger the tanks
    protected int getFrameDelayToUnsiege() {
        isLongDelayedUnsiege = !isLongDelayedUnsiege;
        return isLongDelayedUnsiege ? 144 : 36;
    }

    //random change unsiege delay
    protected void randomFrameDelayToggle() {
        if (Math.random() < 0.0005 * Launcher.STEP_SIZE) {
            framesDelayToUnSiege =  framesDelayToUnSiege == 36 ? 144 : 36;
        }
    }

    protected boolean doUnsiege() {
        //unsiege immediately if no targets but threat from enemy air or enemy ground in my blind spot exists
        if (getEnemyTargetsInRange(12.8f).isEmpty() &&
                (InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, unit.unit().getPosition().toPoint2d()) ||
                        isEnemyTargetsInTankBlindSpot())) {
            return true;
        }

        //unsiege when no enemy targets for awhile
        if (unit.unit().getWeaponCooldown().orElse(1f) == 0f &&
                UnitUtils.getDistance(unit.unit(), targetPos) > TARGET_POS_RADIUS + 2 &&
                getEnemyTargetsInRange(12.8f).isEmpty()) {
            return isUnsiegeWaitTimeComplete();
        }
        lastActiveFrame = Time.nowFrames();
        return false;
    }

    protected boolean isUnsiegeWaitTimeComplete() {
        return lastActiveFrame + framesDelayToUnSiege < Time.nowFrames();
    }

    protected boolean isEnemyTargetsInTankBlindSpot() {
        return !Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                UnitUtils.getDistance(enemy.unit(), unit.unit()) - unit.unit().getRadius() - enemy.unit().getRadius() < 2 &&
                    !enemy.unit().getFlying().orElse(true) &&
                    !enemy.unit().getHallucination().orElse(false) &&
                    enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                    !UnitUtils.isSnapshot(enemy.unit())).isEmpty();
    }

    //if enemy sieged tank nearby and it can't see
    protected Unit getEnemyTankToSiege() {
        Unit enemyTank = getClosestEnemySiegedTank();
        if (enemyTank == null) {
            return null;
        }

        float distanceToEnemyTank = UnitUtils.getDistance(enemyTank, unit.unit());
        if (distanceToEnemyTank > 17 ||
                (UnitUtils.isSnapshot(enemyTank) && UnitUtils.numScansAvailable() == 0)) {
            return null;
        }

        //don't bother trying to move in on an enemy tank with full vision
        if (distanceToEnemyTank + unit.unit().getRadius() * 2 > 13 &&
                canEnemyTankSeeMaxSiegeRange(enemyTank)) {
            return null;
        }
        return enemyTank;
    }

    private boolean canEnemyTankSeeMaxSiegeRange(Unit enemyTank) {
        //edge of my tank at pos where it will siege (test vision here)
        Point2d enemyVisionPos = Position.towards(enemyTank, unit.unit(), 12.9f + enemyTank.getRadius());

        //check if enemy can see my siege position
        if (InfluenceMaps.getValue(InfluenceMaps.pointInEnemyVision, enemyVisionPos)) {
            return true;
        }
        return false;
    }

    protected Unit getClosestEnemySiegedTank() {
        List<UnitInPool> enemyTankList = Bot.OBS.getUnits(Alliance.ENEMY, u -> u.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED);
        if (UnitUtils.numScansAvailable() > 0) { //only check tanks in fog of war if scan is available
            enemyTankList.addAll(EnemyUnitMemory.getAllOfType(Units.TERRAN_SIEGE_TANK_SIEGED));
        }
        return enemyTankList.stream()
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), unit.unit())))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    protected void scanEnemyTank(Unit enemyTank) {
        Point2d scanPos = Position.towards(enemyTank, unit.unit(), -5);
        UnitUtils.scan(scanPos);
    }

    @Override
    protected void detour() {
        if (!UnitUtils.isInMyMain(unit.unit())) {
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, ArmyManager.retreatPos, false);
            return;
        }
        super.detour();
    }

    protected List<UnitInPool> getEnemyTargetsInRange(float range) {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy -> {
            if (enemy.unit().getFlying().orElse(true) ||
                    UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) ||
                    enemy.unit().getHallucination().orElse(false) ||
                    enemy.unit().getDisplayType() != DisplayType.VISIBLE ||
                    UnitUtils.isSnapshot(enemy.unit())) {
                return false;
            }
            float distance = UnitUtils.getDistance(enemy.unit(), unit.unit());
            return distance <= (UnitUtils.canMove(enemy.unit()) ? range : Math.min(range, 12.8f)) +
                            unit.unit().getRadius() + enemy.unit().getRadius() &&
                    distance - unit.unit().getRadius() + enemy.unit().getRadius() > 2;
        });
    }
}
