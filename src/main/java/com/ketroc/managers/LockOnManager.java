package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.geometry.Position;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

//tracks enemy cyclone lock ons
public class LockOnManager {
    private static Set<CycloneLockData> lockTrackingSet = new HashSet<>();

    public static void onStep() {
        removeLockOns();
        nullOutWrongGuesses();
    }

    public static void removeLockOns() {
        lockTrackingSet.removeIf(data -> !data.getTargetUnit().isAlive() || //target unit died
                    !data.getTargetUnit().unit().getBuffs().contains(Buffs.LOCK_ON)); //lock-on ended
    }

    public static void nullOutWrongGuesses() {
        lockTrackingSet.stream()
                .filter(data -> data.getEnemyCyclone() == null || !data.getEnemyCyclone().isAlive() || //enemy cyclone died
                            Time.nowFrames() - data.getCastedGameFrame() > 320 || //max time expired
                            (data.getEnemyCyclone().getLastSeenGameLoop() == Time.nowFrames() &&
                                    UnitUtils.getDistance(data.getEnemyCyclone().unit(), data.getTargetUnit().unit()) > 16)) //enemy cyclone out of range
                .forEach(data -> data.setEnemyCyclone(null));
    }

    public static Unit getCyclone(Unit targetUnit) {
        return getByTargetUnit(targetUnit)
                .map(CycloneLockData::getEnemyCyclone)
                .map(UnitInPool::unit)
                .orElse(null);
    }

    public static Point2d getPosToFleeFrom(Unit targetUnit) {
        //get best guess cyclone
        Unit bestGuessCyclone = getCyclone(targetUnit);
        if (bestGuessCyclone != null) {
            return bestGuessCyclone.getPosition().toPoint2d();
        }

        //if no guess, get median position of visible cyclones
        List<Unit> nearbyCyclones = UnitUtils.getVisibleEnemyUnitsOfType(Units.TERRAN_CYCLONE).stream()
                .filter(cyclone -> UnitUtils.getDistance(targetUnit, cyclone.unit()) < 16)
                .map(UnitInPool::unit)
                .collect(Collectors.toList());
        if (!nearbyCyclones.isEmpty()) {
            return Position.midPointUnitsMedian(nearbyCyclones);
        }

        //retreat home if no cyclones visible
        return ArmyManager.retreatPos;
    }

    private static Optional<CycloneLockData> getByTargetUnit(Unit targetUnit) {
        return lockTrackingSet.stream()
                .filter(data -> data.getTargetUnit().getTag().equals(targetUnit.getTag()))
                .findFirst();
    }

    private static Optional<CycloneLockData> getByCyclone(Unit enemyCyclone) {
        return lockTrackingSet.stream()
                .filter(data -> data.getEnemyCyclone() != null && data.getEnemyCyclone().getTag().equals(enemyCyclone.getTag()))
                .findFirst();
    }
}
