package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.bots.KetrocBot;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.micro.StructureFloater;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OverlordHunter {
    public static OverlordHunter overlordHunter;
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

    public OverlordHunter(UnitInPool overlord, UnitInPool barracks) {
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
                unloadBunkers();
            }
        }
        else {
            barracksLanding();
        }
    }

    private void unloadBunkers() {
        UnitUtils.getNatBunkers().forEach(bunker -> {
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
                UnitUtils.getDistance(barracks.unit(), getOverlordPos()) < 0.1f &&
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
                UnitMicroList.add(new StructureFloater(barracks, getOverlordPos(), false));
                PosConstants._3x3Structures.add(0, Position.toHalfPoint(barracks.unit().getPosition().toPoint2d()));
            }
            return;
        }

        //move towards overlord
        barracksSpotter.targetPos = getOverlordPos();
    }

    public Point2d getOverlordPos() {
        return !UnitUtils.isInFogOfWar(overlord)
                ? overlord.unit().getPosition().toPoint2d()
                : UnitUtils.getLeadPos(overlord.unit(), 8f);
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
            Point2d landingPos;
            if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT || PosConstants._3x3Structures.isEmpty()) {
                landingPos = PosConstants._3x3AddonPosList.remove(0);
            }
            else {
                landingPos = get3x3Pos();
                PosConstants._3x3Structures.remove(landingPos);
            }
            UnitMicroList.add(new StructureFloater(barracks, landingPos, true));
        }
    }

    private Point2d get3x3Pos() {
        Point2d position = PosConstants._3x3Structures.stream()
                .filter(p -> isLocationSafeAndAvailable(p, Bot.OBS.getUnitTypeData(false).get(Units.TERRAN_BARRACKS).getAbility().get()))
                .filter(structurePos -> KetrocBot.purchaseQueue.size() > 1 || !UnitUtils.isWallComplete() || Bot.OBS.getVisibility(structurePos) != Visibility.VISIBLE) //after initial build order, priority is to grant vision of my main base
                .findFirst()
                .orElse(
                        PosConstants._3x3Structures.stream()
                                .filter(p -> isLocationSafeAndAvailable(p, Bot.OBS.getUnitTypeData(false).get(Units.TERRAN_BARRACKS).getAbility().get()))
                                .findFirst()
                                .orElse(null)
                );
        if (position != null) {
            PosConstants._3x3Structures.remove(position);
        }
        else if (!PosConstants._3x3AddonPosList.isEmpty()) { //use starport position if none remain
            position = PosConstants._3x3AddonPosList.remove(PosConstants._3x3AddonPosList.size()-1);
        }
        return position;
    }

    private boolean isLocationSafeAndAvailable(Point2d p, Ability buildAbility) {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, p) == 0 &&
                Bot.QUERY.placement(buildAbility, p);
    }

    private boolean isBarracksLanded() {
        return barracks.unit().getType() == Units.TERRAN_BARRACKS ||
                UnitUtils.getOrder(barracks.unit()) == Abilities.LAND;
    }

    //ready when natural has PF or bunker or when 2nd marine is building
    public static boolean isReadyToHunt() {
        return ((GameCache.baseList.get(1).isMyBase() &&
                        GameCache.baseList.get(1).getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS) ||
                        (UnitUtils.getNatBunkers().stream()
                                .anyMatch(bunker -> bunker.unit().getBuildProgress() == 1)) ||
                        UnitUtils.numMyUnits(Units.TERRAN_MARINE, true) >= 2) &&
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
                        .ifPresent(barracks -> overlordHunter = new OverlordHunter(ol, barracks));
            });
        }

        //run overlord hunting
        if (overlordHunter != null) {
            overlordHunter.onStep();
        }
    }

    //get closest overlord last seen near my main/nat/3rd
    private static Optional<UnitInPool> getClosestOverlord() {
        return UnitUtils.getEnemyUnitsOfType(Units.ZERG_OVERLORD).stream()
                .filter(ol -> !OverlordHunter.isOverlordLost(ol))
                .filter(ol -> UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(0).getCcPos()) < 20 ||
                        UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(1).getCcPos()) < 20 ||
                        UnitUtils.getDistance(ol.unit(), GameCache.baseList.get(2).getCcPos()) < 20)
                .filter(ol -> UnitUtils.isReachableToAttack(ol.unit(), 5.1f))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), PosConstants.BUNKER_NATURAL)));
    }

    private static boolean isOverlordLost(UnitInPool ol) {
        return prevCheckedOverlords.getOrDefault(ol.getTag(), 0L) > ol.getLastSeenGameLoop();
    }
}








