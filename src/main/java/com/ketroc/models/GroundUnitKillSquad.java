package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.micro.BasicUnitMicro;
import com.ketroc.micro.Chaser;
import com.ketroc.micro.Hellbat;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.InfluenceMaps;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//banshee, overlord/seer, observer,
public class GroundUnitKillSquad {
    public static final int MAX_THREAT = 3;
    public static long prevHellionMorphFrame;
    public static long createdFrame;

    public static List<GroundUnitKillSquad> enemyGroundTargets = new ArrayList<>();

    private Chaser chaser;
    private Chaser raven;
    private UnitInPool targetUip;

    public GroundUnitKillSquad(UnitInPool targetUip) {
        this.targetUip = targetUip;
    }

    public BasicUnitMicro getRaven() {
        return raven;
    }

    public void setRaven(Chaser raven) {
        this.raven = raven;
    }

    public UnitInPool getTargetUip() {
        return targetUip;
    }

    public void setTargetUip(UnitInPool targetUip) {
        this.targetUip = targetUip;
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
        return targetUip.unit().getType() == Units.TERRAN_GHOST ||
                targetUip.unit().getType() == Units.PROTOSS_DARK_TEMPLAR ||
                targetUip.unit().getType() == Units.ZERG_LURKER_MP; //TODO: add zerg units when burrow-tech is detected
    }

    public List<Units> getChaserTypes() {
        switch ((Units) targetUip.unit().getType()) {
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
                return List.of(Units.TERRAN_HELLION, Units.TERRAN_MARAUDER);
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

    //FIXME: marauders are never picked cuz they are all objects in UnitMicroList
    private void addChaser() {
        List<Units> validChaserTypes = getChaserTypes();
        for (Units chaserType : validChaserTypes) {
            List<Unit> validChasers = UnitUtils.myUnitsOfType(chaserType); //TODO: handle removing ignoreds from unit lists/maps in gamecache
            if (validChasers.isEmpty()) {
                continue;
            }
            validChasers.stream()
                    .filter(u -> !Ignored.contains(u.getTag()))
                    .min(Comparator.comparing(u -> UnitUtils.getDistance(u, targetUip.unit())))
                    .ifPresent(u -> {
                        chaser = new Chaser(u, targetUip);
                        Ignored.add(new IgnoredUnit(u.getTag()));
                    });
            return;
        }
        //if no chaser found, try morphing the nearest hellbat
        if (Time.after(prevHellionMorphFrame + 24) &&
                Time.after(createdFrame + 24) && //don't morph on the step where I see the first unit of a bigger army
                validChaserTypes.contains(Units.TERRAN_HELLION) &&
                UnitMicroList.numOfUnitClass(Hellbat.class) > 0 &&
                !Hellbat.isAnyMorphing()) {
            morphNearestHellbat();
        }
    }

    private void morphNearestHellbat() {
        UnitMicroList.getUnitSubList(Hellbat.class).stream()
                .filter(hellbat ->
                        !InfluenceMaps.getValue(
                                InfluenceMaps.pointThreatToGroundPlusBuffer,
                                hellbat.unit.unit().getPosition().toPoint2d()
                        )
                )
                .min(Comparator.comparing(hellbat -> UnitUtils.getDistance(hellbat.unit.unit(), targetUip.unit())))
                .ifPresent(hellbat -> {
                    hellbat.morph(true);
                    prevHellionMorphFrame = Time.nowFrames();
                });
    }

    private void addRaven() {
        GameCache.ravenList.stream()
                .filter(closestRaven -> !Ignored.contains(closestRaven.getTag()))
                .min(Comparator.comparing(closestRaven -> UnitUtils.getDistance(closestRaven, targetUip.unit())))
                .ifPresent(closestRaven -> {
                    raven = new Chaser(closestRaven, targetUip);
                    Ignored.add(new IgnoredUnit(closestRaven.getTag()));
                    GameCache.ravenList.remove(closestRaven);
                });
    }

    private void removeAll() {
        if (targetUip != null) {
            Ignored.remove(targetUip.getTag());
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
        return !targetUip.isAlive() ||
                targetUip.getLastSeenGameLoop() + 96 <= Time.nowFrames() ||
                !UnitUtils.isEnemyUnitSolo(targetUip.unit());
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
        return enemyGroundTargets.stream().anyMatch(killSquad -> killSquad.targetUip.getTag().equals(targetTag));
    }

    public static void add(UnitInPool newTarget) { //TODO: don't create object if no chasers available
        if (!contains(newTarget.getTag())) {
            enemyGroundTargets.add(new GroundUnitKillSquad(newTarget));
            Ignored.add(new IgnoredUnit(newTarget.getTag()));
        }
    }

    public static void remove(Tag targetTagToRemove) {
        enemyGroundTargets.stream()
                .filter(killSquad -> killSquad.targetUip.getTag().equals(targetTagToRemove))
                .forEach(killSquad -> killSquad.removeAll());
        enemyGroundTargets.removeIf(killSquad -> killSquad.targetUip.getTag().equals(targetTagToRemove));
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
