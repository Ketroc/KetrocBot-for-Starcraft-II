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
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//banshee, overlord/seer, observer,
public class GroundUnitKillSquad {
    public static final int MAX_THREAT = 3;

    public static List<GroundUnitKillSquad> enemyGroundTargets = new ArrayList<>();

    private BasicUnitMicro raven;
    private List<VikingChaser> vikings = new ArrayList<>();
    private UnitInPool targetUnit;
    private int numVikingsRequired;

    public GroundUnitKillSquad(UnitInPool targetUnit) {
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

    public int getNumVikingsRequired() {
        return numVikingsRequired;
    }

    public void setNumVikingsRequired(int numVikingsRequired) {
        this.numVikingsRequired = numVikingsRequired;
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
                vikings.size() == 2 &&
                vikings.stream().allMatch(viking -> UnitUtils.getDistance(viking.unit.unit(), targetUnit.unit()) < 6) &&
                (raven == null || UnitUtils.getDistance(raven.unit.unit(), targetUnit.unit()) > 20)) {
            Point2d scanPos = Position.towards(targetUnit.unit(), vikings.get(0).unit.unit(), -3.5f);
            UnitUtils.scan(scanPos);
            System.out.println("scan for AirUnitKillSquad at " + Time.nowClock());
        }
    }

    private void updateRaven() {
        if (raven != null) {
            //set targetPos if raven is alive
            if (raven.isAlive()) {
                raven.targetPos = targetUnit.unit().getPosition().toPoint2d();
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

        //add new vikings up to 2 TODO: (assign different amounts of vikings for different targets?)
        if (vikings.size() < 2) {
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
        enemyGroundTargets.stream().forEach(killSquad -> {
            if (killSquad.shouldCancelKillSquad()) {
                killSquad.removeAll();
            }
        });
        enemyGroundTargets.removeIf(killSquad -> killSquad.shouldCancelKillSquad());

        //do onStep commands
        enemyGroundTargets.forEach(killSquad -> killSquad.updateUnits());
    }

    public static boolean contains(Tag targetTag) {
        return enemyGroundTargets.stream().anyMatch(killSquad -> killSquad.targetUnit.getTag().equals(targetTag));
    }

    public static boolean containsWithNoVikings(Tag targetTag) {
        return enemyGroundTargets.stream()
                .anyMatch(killSquad -> killSquad.targetUnit.getTag().equals(targetTag) &&
                        killSquad.vikings.isEmpty());
    }

    public static void add(UnitInPool newTarget) {
        if (!contains(newTarget.getTag())) {
            enemyGroundTargets.add(new GroundUnitKillSquad(newTarget));
            Ignored.add(new IgnoredUnit(newTarget.getTag()));
        }
    }

    public static void remove(Tag targetTagToRemove) {
        enemyGroundTargets.stream()
                .filter(killSquad -> killSquad.targetUnit.getTag().equals(targetTagToRemove))
                .forEach(killSquad -> killSquad.removeAll());
        enemyGroundTargets.removeIf(killSquad -> killSquad.targetUnit.getTag().equals(targetTagToRemove));
    }
}
