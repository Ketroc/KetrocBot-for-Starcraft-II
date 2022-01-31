package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.GameCache;
import com.ketroc.geometry.Position;
import com.ketroc.micro.BasicUnitMicro;
import com.ketroc.micro.MicroPriority;
import com.ketroc.micro.VikingChaser;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//banshee, overlord/seer, observer,
public class AirUnitKillSquad {
    public static final int MAX_THREAT = 1;

    public static List<AirUnitKillSquad> enemyAirTargets = new ArrayList<>();

    private BasicUnitMicro raven;
    private List<VikingChaser> vikings = new ArrayList<>();
    private UnitInPool targetUnit;

    public AirUnitKillSquad(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
    }

    public BasicUnitMicro getRaven() {
        return raven;
    }

    public void setRaven(BasicUnitMicro raven) {
        this.raven = raven;
    }

    public List<VikingChaser> getVikings() {
        return vikings;
    }

    public void setVikings(List<VikingChaser> vikings) {
        this.vikings = vikings;
    }

    public UnitInPool getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
    }

    public int numVikingsRequired() {
        return numVikingsRequired((Units)targetUnit.unit().getType());
    }

    public void updateUnits() {
        updateRaven();
        updateVikings();
        if (raven != null) {
            raven.onStep();
        }
        vikings.forEach(vikingChaser -> vikingChaser.onStep());
        doScan();
    }

    private void doScan() {
        if (targetUnit != null && targetUnit.isAlive() &&
                targetUnit.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.CLOAKED &&
                vikings.size() == numVikingsRequired() &&
                vikings.stream().allMatch(viking -> UnitUtils.getDistance(viking.unit.unit(), targetUnit.unit()) < 6) && (
                        raven == null ||
                        UnitUtils.getDistance(raven.unit.unit(), targetUnit.unit()) > 20 ||
                        !raven.isSafe()
                )) {
            Point2d scanPos = Position.towards(targetUnit.unit(), vikings.get(0).unit.unit(), -2.5f);
            UnitUtils.scan(scanPos);
            System.out.println("scan for AirUnitKillSquad at " + Time.nowClock());
        }
    }

    private void updateRaven() {
        if (raven != null) {
            //set targetPos if raven is alive
            if (raven.isAlive()) {
                raven.targetPos = UnitUtils.getPosLeadingUnit(raven.unit.unit(), targetUnit.unit());
            }
            //remove dead raven
            else {
                Ignored.remove(raven.unit.getTag());
                raven = null;
            }
        }
        //assign new raven
        if (raven == null && isRavenRequired()) {
            addRaven();
        }
    }

    public boolean isRavenRequired() {
        return targetUnit.unit().getType() == Units.TERRAN_BANSHEE ||
                targetUnit.unit().getType() == Units.PROTOSS_OBSERVER ||
                targetUnit.unit().getType() == Units.PROTOSS_OBSERVER_SIEGED;
    }

    private void updateVikings() {
        //remove dead vikings
        vikings.forEach(vikingChaser -> {
            if (!vikingChaser.isAlive()) {
                Ignored.remove(vikingChaser.unit.getTag());
            }
        });
        vikings.removeIf(vikingChaser -> !vikingChaser.isAlive());

        //add new vikings
        if (vikings.size() < numVikingsRequired()) {
            addViking();
        }
    }

    private void addRaven() {
        Point2d targetPos = targetUnit.unit().getPosition().toPoint2d();
        GameCache.ravenList.stream()
                .min(Comparator.comparing(closestRaven -> UnitUtils.getDistance(closestRaven, targetPos)))
                .ifPresent(closestRaven -> {
                    raven = new BasicUnitMicro(closestRaven, targetPos, MicroPriority.SURVIVAL);
                    Ignored.add(new IgnoredUnit(closestRaven.getTag()));
                    GameCache.ravenList.remove(closestRaven);
                });
    }

    private void addViking() {
        Point2d targetPos = targetUnit.unit().getPosition().toPoint2d();
        GameCache.vikingList.stream()
                .min(Comparator.comparing(viking -> UnitUtils.getDistance(viking, targetPos)))
                .ifPresent(closestViking -> {
                    vikings.add(new VikingChaser(closestViking, targetUnit));
                    Ignored.add(new IgnoredUnit(closestViking.getTag()));
                    GameCache.ravenList.remove(closestViking);
                });
    }

    private void removeAll() {
        if (targetUnit != null) {
            Ignored.remove(targetUnit.getTag());
        }
        if (raven != null) {
            Ignored.remove(raven.unit.getTag());
        }
        vikings.forEach(vikingChaser -> Ignored.remove(vikingChaser.unit.getTag()));
    }

    //cancel killsquad is target is dead, in fog (for over 4s), or protected from vikings
    private boolean shouldCancelKillSquad() {
        return !targetUnit.isAlive() ||
                targetUnit.getLastSeenGameLoop() + 96 <= Time.nowFrames() ||
                (!vikings.isEmpty() && vikings.stream()
                        .allMatch(viking -> InfluenceMaps.getValue(
                                InfluenceMaps.pointThreatToAirValue,
                                Position.towards(targetUnit.unit(), viking.unit.unit(), 9)
                        ) >= MAX_THREAT));
    }


    //****************************************
    //************ STATIC METHODS ************
    //****************************************

    public static void onStep() {
        //remove targets that are dead or in fog of war
        enemyAirTargets.stream().forEach(killSquad -> {
            if (killSquad.shouldCancelKillSquad()) {
                killSquad.removeAll();
            }
        });
        enemyAirTargets.removeIf(killSquad -> killSquad.shouldCancelKillSquad());

        //do onStep commands
        enemyAirTargets.forEach(killSquad -> killSquad.updateUnits());
    }

    public static boolean contains(Tag targetTag) {
        return enemyAirTargets.stream().anyMatch(killSquad -> killSquad.targetUnit.getTag().equals(targetTag));
    }

    public static boolean containsWithNoVikings(Tag targetTag) {
        return enemyAirTargets.stream()
                .anyMatch(killSquad -> killSquad.targetUnit.getTag().equals(targetTag) &&
                        killSquad.vikings.isEmpty());
    }

    public static void add(UnitInPool newTarget) {
        if (canAdd(newTarget)) {
            enemyAirTargets.add(new AirUnitKillSquad(newTarget));
            Ignored.add(new IgnoredUnit(newTarget.getTag()));
        }
    }

    public static boolean canAdd(UnitInPool newTarget) {
        return GameCache.vikingList.size() >= numVikingsRequired((Units)newTarget.unit().getType()) &&
                !contains(newTarget.getTag());
    }

    public static void remove(Tag targetTagToRemove) {
        enemyAirTargets.stream()
                .filter(killSquad -> killSquad.targetUnit.getTag().equals(targetTagToRemove))
                .forEach(killSquad -> killSquad.removeAll());
        enemyAirTargets.removeIf(killSquad -> killSquad.targetUnit.getTag().equals(targetTagToRemove));
    }

    public static int numVikingsRequired(Units targetType) {
        switch (targetType) {
            case PROTOSS_WARP_PRISM: case PROTOSS_WARP_PRISM_PHASING: case ZERG_OVERSEER: case ZERG_OVERSEER_SIEGED:
            case ZERG_OVERLORD_TRANSPORT: case ZERG_OVERLORD_COCOON: case TERRAN_MEDIVAC: case TERRAN_BANSHEE:
                return 2;
        }
        return 1;
    }
}
