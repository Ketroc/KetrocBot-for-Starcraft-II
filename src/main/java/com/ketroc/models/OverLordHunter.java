package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.micro.StructureFloater;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OverLordHunter {
    public static OverLordHunter overlordHunter;
    public static Map<Tag, Long> prevCheckedOverlords = new HashMap<>();

    private UnitInPool barracks;
    private UnitInPool overlord;
    //private boolean cantFindOverlord;
    private boolean isAborting;

    public UnitInPool getBarracks() {
        return barracks;
    }

    public void setBarracks(UnitInPool barracks) {
        this.barracks = barracks;
    }

    public UnitInPool getOverlord() {
        return overlord;
    }

    public void setOverlord(UnitInPool overlord) {
        this.overlord = overlord;
    }

    public boolean isAborting() {
        return isAborting;
    }

    public void setAborting(boolean aborting) {
        isAborting = aborting;
    }

    public OverLordHunter(UnitInPool overlord, UnitInPool barracks) {
        this.overlord = overlord;
        this.barracks = barracks;
    }

    public void onStep() {
        setOverlord();

        //decide to abort
        if (!isAborting && doAbort()) {
            isAborting = true;
            ActionHelper.unitCommand(barracks.unit(), Abilities.STOP, false);
            UnitMicroList.remove(barracks.getTag());
            return;
        }

        //decide to reengage
        if (isAborting && !doAbort()) {
            isAborting = false;
            ActionHelper.unitCommand(barracks.unit(), Abilities.STOP, false);
            UnitMicroList.remove(barracks.getTag());
            return;
        }

        //barracks handling
        if (!isAborting) {
            barracksSpotting();
            if (!isBarracksLanded()) {
                unloadBunker();
            }
        }
        else {
            barracksLanding();
        }
    }

    private void unloadBunker() {
        UnitUtils.getNatBunker().ifPresent(bunker -> {
            if (bunker.unit().getCargoSpaceTaken().orElse(0) > 0) {
                ActionHelper.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
            }
        });
    }

    private void setOverlord() {
        //overlord died
        if (overlord != null && !overlord.isAlive()) {
            overlord = null;
        }

        //can't find overlord
        if (overlord != null &&
                barracks != null &&
                UnitUtils.getDistance(barracks.unit(), overlord.unit()) < 0.1f &&
                overlord.getLastSeenGameLoop() != Time.nowFrames()) {
            prevCheckedOverlords.put(overlord.getTag(), Time.nowFrames());
            overlord = null;
            return;
        }

        //overlord is unreachable by marines
        if (overlord != null && !UnitUtils.isReachableToAttack(overlord.unit(), 5.1f)) {
            prevCheckedOverlords.put(overlord.getTag(), Time.nowFrames());
            overlord = null;
            return;
        }

        //set another overlord
        if (overlord == null) {
            getClosestOverlord().ifPresent(ol -> overlord = ol);
        }
    }

    //abort when under attack, overlord killed, or if rax is at last known location of overlord and it's not visible
    private boolean doAbort() {
        return overlord == null || UnitUtils.isAnyBaseUnderAttack();
    }

    public boolean isComplete() {
        return isBarracksLanded() && doAbort();
    }

    private void barracksSpotting() {
        StructureFloater barracksSpotter = getBarracksFloater();

        //lift barracks when marine production is idle
        if (barracksSpotter == null) {
            if (UnitUtils.getOrder(barracks.unit()) == null) {
                UnitMicroList.add(new StructureFloater(barracks, overlord.unit().getPosition().toPoint2d(), false));
                LocationConstants._3x3Structures.add(0, Position.toHalfPoint(barracks.unit().getPosition().toPoint2d()));
            }
            return;
        }

        //move towards overlord
        barracksSpotter.targetPos = overlord.unit().getPosition().toPoint2d();
    }

    private StructureFloater getBarracksFloater() {
        return UnitMicroList.getUnitSubList(StructureFloater.class).stream()
                .filter(structure -> structure.unit.getTag().equals(barracks.getTag()))
                .findFirst()
                .orElse(null);
    }

    private void barracksLanding() {
        StructureFloater barracksLander = getBarracksFloater();

        //send barracks to land
        if (barracksLander == null) {
            Point2d landingPos = LocationConstants._3x3Structures.remove(0);
            UnitMicroList.add(new StructureFloater(barracks, landingPos, true));
        }
    }

    private boolean isBarracksLanded() {
        return barracks.unit().getType() == Units.TERRAN_BARRACKS ||
                UnitUtils.getOrder(barracks.unit()) == Abilities.LAND;
    }

    //ready when natural has PF or bunker
    public static boolean isReadyToHunt() {
        return ((GameCache.baseList.get(1).isMyBase() &&
                        GameCache.baseList.get(1).getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS) ||
                        (UnitUtils.getNatBunker().stream()
                                .anyMatch(bunker -> bunker.unit().getBuildProgress() == 1))) &&
                !UnitUtils.isAnyBaseUnderAttack();
    }

    public static void manageOverlordHunter() {
        //remove complete hunt
        if (overlordHunter != null && overlordHunter.isComplete()) {
            overlordHunter = null;
        }

        //create new hunt
        if (overlordHunter == null &&
                isReadyToHunt() &&
                UnitUtils.numMyUnits(Units.TERRAN_MARINE, false) > 0 &&
                UnitUtils.numMyUnits(UnitUtils.BARRACKS_TYPE, false) > 0) {
            getClosestOverlord().ifPresent(ol -> {
                Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.BARRACKS_TYPE.contains(u.unit().getType()) &&
                                !Ignored.contains(u.getTag()))
                        .stream()
                        .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), ol.unit())))
                        .ifPresent(barracks -> overlordHunter = new OverLordHunter(ol, barracks));
            });
        }

        //run overlord hunting
        if (overlordHunter != null) {
            overlordHunter.onStep();
        }
    }

    private static Optional<UnitInPool> getClosestOverlord() {
        return UnitUtils.getEnemyUnitsOfType(Units.ZERG_OVERLORD).stream()
                .filter(ol -> !OverLordHunter.isOverlordLost(ol))
                .filter(ol -> UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(0).getCcPos()) < 20 ||
                        UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(1).getCcPos()) < 20 ||
                        UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(2).getCcPos()) < 20)
                .filter(ol -> UnitUtils.isReachableToAttack(ol.unit(), 5.1f))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.BUNKER_NATURAL)));
    }

    private static boolean isOverlordLost(UnitInPool ol) {
        return prevCheckedOverlords.getOrDefault(ol.getTag(), 0L) > ol.getLastSeenGameLoop();
    }
}








