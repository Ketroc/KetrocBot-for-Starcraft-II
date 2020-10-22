package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.utils.InfluenceMaps;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

public class Base {
    public long lastScoutedFrame;
    public boolean isEnemyBase;
    private boolean isDryedUp;
    private Point2d ccPos;
    private UnitInPool cc;
    private List<Gas> gases = new ArrayList<>();
    private List<Unit> mineralPatches = new ArrayList<>();
    private Unit rallyNode; //mineral node this cc is rallied to
    private int extraScvs;
    private Point2d resourceMidPoint = null;
    private List<DefenseUnitPositions> turrets = new ArrayList<>();
    private List<DefenseUnitPositions> liberators = new ArrayList<>();
    private List<DefenseUnitPositions> tanks = new ArrayList<>();
    private static float libDistanceFromCC = -1;
    private boolean continueUnsieging;
    private boolean onMyBaseDeath;

    // ============= CONSTRUCTORS ============

    public Base(Point2d ccPos) {
        this.ccPos = ccPos;
        setResourceMidPoint();
    }

    // =========== GETTERS AND SETTERS =============

    public Point2d getResourceMidPoint() {
        return resourceMidPoint;
    }

    public void setResourceMidPoint() {
        List<UnitInPool> resourceNodes = Bot.OBS.getUnits(u ->
                (UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) || UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType())) &&
                        UnitUtils.getDistance(u.unit(), ccPos) < 10);
        resourceMidPoint = Position.towards(ccPos, Position.midPointUnitInPoolsWeighted(resourceNodes), 4.25f);
    }


    public List<Unit> getMineralPatches() {
        return mineralPatches;
    }

    public void setMineralPatches(List<Unit> mineralPatches) {
        this.mineralPatches = mineralPatches;
    }

    public Optional<UnitInPool> getCc() {
        return Optional.ofNullable(cc);
    }

    public void setCc(UnitInPool cc) {
        //on cc removal
        if (this.cc != null && cc == null) {
            continueUnsieging = true;
            onMyBaseDeath = true;
        }
        //on cc added
        else if (this.cc == null && cc != null) {
            continueUnsieging = false; //leave sieged units here
        }
        this.cc = cc;
    }

    public Point2d getCcPos() {
        return ccPos;
    }

    public void setCcPos(Point2d ccPos) {
        this.ccPos = ccPos;
    }

    public List<Gas> getGases() {
        return gases;
    }

    public void setGases(List<Gas> gases) {
        this.gases = gases;
    }

    public Unit getRallyNode() {
        return rallyNode;
    }

    public void setRallyNode(Unit rallyNode) {
        this.rallyNode = rallyNode;
    }

    public List<DefenseUnitPositions> getTurrets() {
        if (turrets.isEmpty() && !isMyMainBase()) {
            turrets.add(new DefenseUnitPositions(
                    Position.moveClearExactly(resourceMidPoint, ccPos, 3.5f), null));
            turrets.add(new DefenseUnitPositions(
                    Position.moveClearExactly(Position.rotate(resourceMidPoint, ccPos, 110), ccPos, 3.5f), null));
            turrets.add(new DefenseUnitPositions(
                    Position.moveClearExactly(Position.rotate(resourceMidPoint, ccPos, -110), ccPos, 3.5f), null));
        }
        return turrets;
    }

    public int getExtraScvs() {
        return extraScvs;
    }

    public void setExtraScvs(int extraScvs) {
        this.extraScvs = extraScvs;
    }

    public boolean isDryedUp() {
        return isDryedUp;
    }

    public void setDryedUp(boolean dryedUp) {
        if (dryedUp && !isDryedUp) {
            continueUnsieging = true;
        }
        isDryedUp = dryedUp;
    }

    public List<DefenseUnitPositions> getLiberators() {
        if (liberators.isEmpty()) {
            if (resourceMidPoint != null) {
                Point2d midPoint = Position.towards(ccPos, resourceMidPoint, getLibDistanceFromCC());
                liberators.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, 32.5), null));
                liberators.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, -32.5), null));
            }
        }
        return liberators;
    }

    public void setLiberators(List<DefenseUnitPositions> liberators) {
        this.liberators = liberators;
    }

    public List<DefenseUnitPositions> getTanks() {
        if (tanks.isEmpty()) {
            if (resourceMidPoint != null) {
                int angle = (LocationConstants.opponentRace == Race.TERRAN) ? 65 : 45;
                Point2d midPoint = Position.towards(ccPos, resourceMidPoint, 4.3f);
                tanks.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, angle), null));
                tanks.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, angle*-1), null));
            }
        }
        return tanks;
    }

    public void setTanks(List<DefenseUnitPositions> tanks) {
        this.tanks = tanks;
    }


    // ============ METHODS ==============

    public void onStep() {
        if (onMyBaseDeath) {
            onMyBaseDeath();
            onMyBaseDeath = false;
        }
        if (continueUnsieging) {
            if (!InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, ccPos)) {
                unsiegeBase();
                continueUnsieging = false;
            }
        }
    }

    public List<UnitInPool> getAvailableGeysers() {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u -> {
            return this.ccPos.distance(u.unit().getPosition().toPoint2d()) < 10.0 &&
                    UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType());
        });
    }

    public boolean isComplete() {
        return isComplete(1f);
    }

    public boolean isComplete(float percentageDone) {
        return cc != null &&
                cc.unit().getType() != Units.TERRAN_COMMAND_CENTER_FLYING &&
                cc.unit().getBuildProgress() >= percentageDone &&
                UnitUtils.getDistance(cc.unit(), ccPos) < 1;
    }

    public boolean isMyBase() {
        return cc != null && cc.unit().getAlliance() == Alliance.SELF;
    }

    public boolean isUntakenBase() {
        return cc == null &&
                !isEnemyBase &&
                StructureScv.scvBuildingList.stream().noneMatch(scv -> scv.structurePos.distance(ccPos) < 1);
    }

    public int numActiveRefineries() {
        return (int)gases.stream().filter(gas -> gas.getRefinery() != null && gas.getRefinery().getVespeneContents().orElse(0) > 80).count();
    }

    public int numActiveMineralPatches() {
        return (int)mineralPatches.stream().filter(patch -> patch.getMineralContents().orElse(0) > 100).count();
    }

    public List<UnitInPool> getAllTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.SIEGE_TANK_TYPE, ccPos, 10);
    }

    public List<UnitInPool> getUnsiegedTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SIEGE_TANK, ccPos, 10);
    }

    public List<UnitInPool> getSiegedTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SIEGE_TANK_SIEGED, ccPos, 10);
    }

    public List<UnitInPool> getAllLibs() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.LIBERATOR_TYPE, ccPos, 10);
    }

    public List<Unit> getUnsiegedLibs() {
        List<UnitInPool> idleLibs = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_LIBERATOR &&
                u.unit().getPosition().toPoint2d().distance(ccPos) < 4 &&
                (u.unit().getOrders().isEmpty() || u.unit().getOrders().get(0).getAbility() != Abilities.ATTACK));
        return UnitUtils.toUnitList(idleLibs);
    }

    public List<Unit> getSiegedLibs() {
        return UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_LIBERATOR_AG, ccPos, 10));
    }

    public List<Unit> getLargeMinerals() {
        return mineralPatches.stream().filter(node -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType())).collect(Collectors.toList());
    }

    public List<Point2d> initTankPositions() {
        return null; //TODO stub
    }

    public List<Unit> getOuterBigPatches() {
        List<Unit> minMaxNodes = new ArrayList<>(List.of(
                mineralPatches.stream().max(Comparator.comparing(node -> node.getPosition().getX())).get(),
                mineralPatches.stream().max(Comparator.comparing(node -> node.getPosition().getY())).get(),
                mineralPatches.stream().min(Comparator.comparing(node -> node.getPosition().getX())).get(),
                mineralPatches.stream().min(Comparator.comparing(node -> node.getPosition().getY())).get()
        ));
        minMaxNodes.removeIf(node -> !UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType()));
        if (minMaxNodes.size() != 2) {
            System.out.println("found more than 2 outer patches");
            minMaxNodes.stream().forEach(unit -> System.out.println(unit.getPosition().toPoint2d()));
        }
        return minMaxNodes;
    }

    public List<Unit> getInnerBigPatches() {
        //get list of big patches
        List<Unit> bigPatches = new ArrayList<>(mineralPatches);
        bigPatches.removeIf(node -> !UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType()));

        //find midpoint
        Point2d p = Point2d.of(0,0);
        for (Unit patch : bigPatches) {
            p = p.add(patch.getPosition().toPoint2d());
        }
        Point2d midPoint = p.div(bigPatches.size());

        //sort by distance to midpoint
        bigPatches.sort(Comparator.comparing(node -> UnitUtils.getDistance(node, midPoint)));

        //return first 2
        return bigPatches.subList(0, 2);
    }

    public UnitInPool getUpdatedUnit(Units unitType, Optional<UnitInPool> unit, Point2d pos) {
        //check for new structure
        if (unit.isEmpty()) {
            UnitInPool newUnit;
            if (UnitUtils.COMMAND_CENTER_TYPE.contains(unitType)) {
                newUnit = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.COMMAND_CENTER_TYPE, pos, 1).stream()
                        .filter(cc -> !cc.unit().getFlying().orElse(true)) //ignore flying CCs
                        .findFirst().orElse(null);
            }
            else {
                newUnit = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, unitType, pos, 1).stream()
                        .findFirst().orElse(null);
            }
            return newUnit;
        }

        //check for dead structure
        if (!unit.get().isAlive()) {
            return null;
        }
        return unit.get();
    }

    public boolean isMyMainBase() {
        return this.equals(GameCache.baseList.get(0));
    }

    public void unsiegeBase() {
        //unsiege liberators and tanks
        freeUpLiberators();
        freeUpTanks();
    }

    public void onMyBaseDeath() {
        //send all scvs to another base's mineral patch
        List<Unit> baseScvs = UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Set.of(Units.TERRAN_SCV, Units.TERRAN_MULE), ccPos, 7));
        Unit mineralPatch = UnitUtils.getSafestMineralPatch();
        List<Unit> scvsCarrying = baseScvs.stream().filter(unit -> UnitUtils.isCarryingResources(unit)).collect(Collectors.toList());//scvs carrying return cargo first
        if (!scvsCarrying.isEmpty()) {
            Bot.ACTION.unitCommand(scvsCarrying, Abilities.HARVEST_RETURN, false);
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(scvsCarrying, Abilities.STOP, true);
            }
            else {
                Bot.ACTION.unitCommand(scvsCarrying, Abilities.SMART, mineralPatch, true);
            }
        }
        List<Unit> scvsNotCarrying = baseScvs.stream().filter(unit -> !UnitUtils.isCarryingResources(unit)).collect(Collectors.toList());
        if (!scvsNotCarrying.isEmpty()) {
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(scvsNotCarrying, Abilities.STOP, false);
            }
            else {
                Bot.ACTION.unitCommand(scvsNotCarrying, Abilities.SMART, mineralPatch, false);
            }
        }

        //cancel turrets and refineries at this base
        for (int i=0; i<StructureScv.scvBuildingList.size(); i++) {
            StructureScv scv = StructureScv.scvBuildingList.get(i);
            if ((scv.structureType == Units.TERRAN_MISSILE_TURRET || scv.structureType == Units.TERRAN_REFINERY) &&
                    scv.structurePos.distance(ccPos) < 10) {
                scv.cancelProduction();
                StructureScv.remove(scv);
                i--;
            }
        }

        //cancel queued up turrets for this base
        KetrocBot.purchaseQueue.removeIf(
                p -> p instanceof PurchaseStructure &&
                        ((PurchaseStructure) p).getStructureType() == Units.TERRAN_MISSILE_TURRET &&
                        ((PurchaseStructure) p).getPosition().distance(ccPos) < 10);


    }

    //make this bases liberators aa mode and idle so they can be picked up for a new base TODO: time delay this
    private void freeUpLiberators() {
        for (DefenseUnitPositions libPos : getLiberators()) {
            if (libPos.getUnit().isPresent()) {
                Unit baseLib = libPos.getUnit().get().unit();
                if (baseLib.getType() == Units.TERRAN_LIBERATOR_AG) {
                    Bot.ACTION.unitCommand(baseLib, Abilities.MORPH_LIBERATOR_AA_MODE, false);
                } else {
                    Bot.ACTION.unitCommand(baseLib, Abilities.STOP, false);
                }
                libPos.setUnit(null);
            }
        }
    }

    //make this bases tanks unsiege and idle so they can be picked up for a new base TODO: time delay this
    private void freeUpTanks() {
        for (DefenseUnitPositions tankPos : getTanks()) {
            if (tankPos.getUnit().isPresent()) {
                Unit tank = tankPos.getUnit().get().unit();
                if (tank.getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                    Bot.ACTION.unitCommand(tank, Abilities.MORPH_UNSIEGE, false);
                } else {
                    Bot.ACTION.unitCommand(tank, Abilities.STOP, false);
                }
                tankPos.setUnit(null);
            }
        }
    }

    // ======= STATIC METHODS ========

    public static float getLibDistanceFromCC() {
        if (libDistanceFromCC == -1 || LocationConstants.opponentRace == Race.RANDOM) {
            libDistanceFromCC = (LocationConstants.opponentRace == Race.PROTOSS) ? 1f : 2.5f;
        }
        return libDistanceFromCC;
    }

    public static int totalMineralPatchesForMyBases() {
        return GameCache.baseList.stream()
                .filter(base -> base.isMyBase())
                .mapToInt(Base::numActiveMineralPatches)
                .sum();
    }

    public static int totalActiveRefineriesForMyBases() {
        return GameCache.baseList.stream()
                .filter(base -> base.isMyBase())
                .mapToInt(Base::numActiveRefineries)
                .sum();
    }

    public static int totalScvsRequiredForMyBases() {
        return (totalActiveRefineriesForMyBases() * 3) + (totalMineralPatchesForMyBases() * 2);
    }

    public static int numMyBases() {
        return (int) GameCache.baseList.stream().filter(base -> base.isMyBase()).count();
    }

}