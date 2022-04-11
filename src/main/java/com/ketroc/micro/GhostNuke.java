package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.models.Base;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class GhostNuke extends Ghost {

    public GhostNuke(UnitInPool ghost, Point2d nukeTargetPos) {
        super(ghost, nukeTargetPos);
        targetPos = nukeTargetPos;
        doDetourAroundEnemy = true;
    }

    @Override
    public void onStep() { //TODO: attack & snipe overseer/overseer_sieged? (emp/attack observers?, attack ravens?)
        //remove object when ghost dies / out of cloak energy / spotted by Overseer / nuke is called
        if (unit == null ||
                !unit.isAlive() ||
                unit.unit().getEnergy().orElse(0f) < 7 ||
                unit.unit().getOrders().stream().anyMatch(order -> order.getAbility() == Abilities.EFFECT_NUKE_CALL_DOWN) ||
                isWithinOverseerVision()) {
            removeMe = true;
            return;
        }

        //cloak if in enemy vision and 65 range from target TODO: is enemy creep in my enemyVisionMap?
        if (!isCloaked() && canCloak() &&
                (InfluenceMaps.getValue(InfluenceMaps.pointInEnemyVision, unit.unit().getPosition().toPoint2d()) ||
                        UnitUtils.getDistance(unit.unit(), targetPos) < 65)) {
            cloak();
            return;
        }

        //nuke when in range
        if (isSafe()) {
            if (UnitUtils.getDistance(unit.unit(), targetPos) <= 11) {
                nuke(targetPos);
                return;
            }

            //nuke if close, but running low on energy
            if (UnitUtils.getDistance(unit.unit(), targetPos) <= 16 && unit.unit().getEnergy().orElse(0f) < 18) {
                Point2d newNukePos = Position.towards(unit.unit(), targetPos, 11);
                if (Bot.OBS.getVisibility(newNukePos) == Visibility.VISIBLE) {
                    nuke(newNukePos);
                    return;
                }
            }
        }

        super.onStep();
    }

    @Override
    public boolean isSafe() {
        return !isDetected() && !isInSplashDamage();
    }

    @Override
    public boolean isSafe(Point2d pos) {
        return !UnitUtils.isDetected(pos) && !isInSplashDamage(pos);
    }

    @Override
    protected boolean attackIfAvailable() {
        return false;
    }

    @Override
    public void onArrival() {

    }

    public boolean isWithinOverseerVision() {
        return !Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                enemy.unit().getType() == Units.ZERG_OVERSEER &&
                        UnitUtils.getDistance(unit.unit(), enemy.unit()) <= 11).isEmpty();
    }


    // ********************************
    // ******** STATIC METHODS ********
    // ********************************

    public static void addGhost() {
        Point2d baseNukePos = selectNukePos();
        if (baseNukePos == null) {
            return;
        }
        UnitInPool ghostUip = selectGhost(baseNukePos);
        if (ghostUip == null) {
            return;
        }
        UnitMicroList.remove(ghostUip.getTag());
        UnitMicroList.add(new GhostNuke(ghostUip, baseNukePos));

    }

    private static UnitInPool selectGhost(Point2d baseNukePos) {
        //at least 85 energy / not on front line or detected / nearest to nuke target
        return UnitMicroList.getUnitSubList(Ghost.class).stream()
                .filter(ghost -> !(ghost instanceof GhostNuke))
                .filter(ghost -> ghost.unit.unit().getEnergy().orElse(0f) > 85f - (ghost.isCloaked() ? 25 : 0))
                .filter(Ghost::isSafe)
                .filter(ghost -> !ghost.isDetected())
                .min(Comparator.comparing(ghostBasic -> UnitUtils.getDistance(ghostBasic.unit.unit(), baseNukePos)))
                .map(ghostBasic -> ghostBasic.unit)
                .orElse(null);
    }

    private static Point2d selectNukePos() {
        //TODO: select a base away from my army's attack pos

        //for now, select a random enemy base that isn't his main and isn't in vision of any of my units
        List<Base> enemyExpansions = GameCache.baseList.subList(3, GameCache.baseList.size() - 1).stream() //exclude my main/nat/3rd, and enemy main
                .filter(base -> base.isEnemyBase) //enemy bases only
                .filter(base -> Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE) //not a base I have units at
                .collect(Collectors.toList());
        if (enemyExpansions.isEmpty()) {
            return null;
        }
        return enemyExpansions.stream()
                .skip(new Random().nextInt(enemyExpansions.size()))
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
    }
}
