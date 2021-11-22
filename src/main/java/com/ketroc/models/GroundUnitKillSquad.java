package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.micro.BasicUnitMicro;
import com.ketroc.micro.Chaser;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//banshee, overlord/seer, observer,
public class GroundUnitKillSquad {
    public static final int MAX_THREAT = 3;

    public static List<GroundUnitKillSquad> enemyGroundTargets = new ArrayList<>();

    private Chaser chaser;
    private Chaser raven;
    private UnitInPool targetUnit;

    public GroundUnitKillSquad(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
    }

    public BasicUnitMicro getRaven() {
        return raven;
    }

    public void setRaven(Chaser raven) {
        this.raven = raven;
    }

    public UnitInPool getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
    }

    public Chaser getChaser() {
        return chaser;
    }

    public void setChaser(Chaser chaser) {
        this.chaser = chaser;
    }

    public void updateUnits() {
        updateChaser();
        if (chaser != null) {
            chaser.onStep();
        }
        if (isRavenRequired()) {
            updateRaven();
            if (raven != null) {
                raven.onStep();
            }
        }
    }

    private void updateChaser() {
        //remove dead chasers
        if (chaser != null && chaser.removeMe) {
            removeChaser();
        }

        //assign new chaser
        if (chaser == null) {
            addChaser();
        }
    }

    private void updateRaven() {
        //remove dead raven
        if (raven != null && raven.removeMe) {
            removeRaven();
        }

        //assign new chaser
        if (raven == null) {
            addRaven();
        }
    }

    public boolean isRavenRequired() {
        return targetUnit.unit().getType() == Units.TERRAN_GHOST ||
                targetUnit.unit().getType() == Units.PROTOSS_DARK_TEMPLAR ||
                targetUnit.unit().getType() == Units.ZERG_LURKER_MP; //TODO: add zerg units when burrow-tech is detected
    }

    public List<Units> getChaserTypes() {
        switch ((Units)targetUnit.unit().getType()) {
//            case ZERG_DRONE: case PROTOSS_PROBE: case TERRAN_SCV:
//            case ZERG_ZERGLING: case ZERG_BANELING: case PROTOSS_ZEALOT:
//            case TERRAN_REAPER: case TERRAN_HELLION_TANK:
//                return List.of(Units.TERRAN_HELLION, Units.TERRAN_CYCLONE, Units.TERRAN_BANSHEE);
//            case ZERG_ROACH: case ZERG_HYDRALISK: case ZERG_QUEEN: case TERRAN_SENSOR_TOWER:
//            case PROTOSS_PYLON: case PROTOSS_SHIELD_BATTERY:
//                return List.of(Units.TERRAN_CYCLONE, Units.TERRAN_BANSHEE);
//            case ZERG_RAVAGER: case ZERG_LURKER_MP: case ZERG_SPINE_CRAWLER:
//            case ZERG_SPINE_CRAWLER_UPROOTED: case TERRAN_MARAUDER:
//                return List.of(Units.TERRAN_BANSHEE);
//            case ZERG_SPORE_CRAWLER_UPROOTED: case ZERG_SPORE_CRAWLER: case TERRAN_MISSILE_TURRET:
//                return List.of(Units.TERRAN_CYCLONE);
            case ZERG_DRONE: case ZERG_ZERGLING: case ZERG_BANELING: case PROTOSS_DARK_TEMPLAR:
            case PROTOSS_PROBE: case PROTOSS_ZEALOT: case PROTOSS_ADEPT:
            case TERRAN_SCV: case TERRAN_HELLION_TANK:
                return List.of(Units.TERRAN_HELLION);
        }
        return Collections.emptyList();
    }

    private void removeChaser() {
        Ignored.remove(chaser.unit.getTag());
        chaser = null;
    }

    private void removeRaven() {
        Ignored.remove(raven.unit.getTag());
        raven = null;
    }

    private void addChaser() {
        List<Units> validChaserTypes = getChaserTypes();
        for (Units chaserType : validChaserTypes) {
            List<Unit> validChasers = UnitUtils.getMyUnitsOfType(chaserType); //TODO: handle removing ignoreds from unit lists/maps in gamecache
            if (validChasers.isEmpty()) {
                continue;
            }
            validChasers.stream()
                    .filter(u -> !Ignored.contains(u.getTag()))
                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u, targetUnit.unit())))
                    .ifPresent(u -> {
                        chaser = new Chaser(u, targetUnit);
                        Ignored.add(new IgnoredUnit(u.getTag()));
                    });
            return;
        }
    }

    private void addRaven() {
        GameCache.ravenList.stream()
                .filter(closestRaven -> !Ignored.contains(closestRaven.getTag()))
                .min(Comparator.comparing(closestRaven -> UnitUtils.getDistance(closestRaven, targetUnit.unit())))
                .ifPresent(closestRaven -> {
                    raven = new Chaser(closestRaven, targetUnit);
                    Ignored.add(new IgnoredUnit(closestRaven.getTag()));
                    GameCache.ravenList.remove(closestRaven);
                });
    }

    private void removeAll() {
        if (targetUnit != null) {
            Ignored.remove(targetUnit.getTag());
        }
        if (chaser != null) {
            Ignored.remove(chaser.unit.getTag());
        }
        if (raven != null) {
            Ignored.remove(raven.unit.getTag());
        }
    }

    //cancel killsquad is target is dead, in fog (for over 4s), or protected from vikings
    private boolean shouldCancelKillSquad() {
        return !targetUnit.isAlive() ||
                targetUnit.getLastSeenGameLoop() + 96 <= Time.nowFrames() ||
                !UnitUtils.isEnemyUnitSolo(targetUnit.unit());
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

    public static boolean isValidEnemyType(Units unitType) {
        switch (unitType) {
//            case ZERG_DRONE: case ZERG_ZERGLING: case ZERG_QUEEN:
//            case ZERG_BANELING: case ZERG_ROACH: case ZERG_RAVAGER:
//            case ZERG_HYDRALISK: case ZERG_LURKER_MP: case ZERG_SPINE_CRAWLER:
//            case ZERG_SPINE_CRAWLER_UPROOTED: case ZERG_SPORE_CRAWLER: case ZERG_SPORE_CRAWLER_UPROOTED:
//            case PROTOSS_PROBE: case PROTOSS_ZEALOT: case PROTOSS_PYLON:
//            case PROTOSS_SHIELD_BATTERY: case PROTOSS_DARK_TEMPLAR:
//            case TERRAN_SCV: case TERRAN_MISSILE_TURRET: case TERRAN_SENSOR_TOWER:
//            case TERRAN_HELLION_TANK:
            case ZERG_DRONE: case ZERG_ZERGLING: case ZERG_BANELING: case PROTOSS_DARK_TEMPLAR:
            case PROTOSS_PROBE: case PROTOSS_ZEALOT: case TERRAN_SCV: case TERRAN_HELLION_TANK:
                return true;
        }
        return false;
    }
}
