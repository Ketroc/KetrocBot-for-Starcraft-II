package com.ketroc.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.managers.WorkerManager;
import com.ketroc.models.Base;
import com.ketroc.models.Cost;
import com.ketroc.models.Gas;
import com.ketroc.models.StructureScv;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.WorkerRushDefense3;
import com.ketroc.utils.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class PurchaseStructure implements Purchase { //TODO: add rally point
    private Unit scv;  //okay to not be unitInPool as it's only set the same frame the build command is given
    private Units structureType;
    private Cost cost;
    private UnitTypeData structureData;
    private Unit rallyUnit; //what to rally to afterwards (typically a mineral patch) TODO: change to unitinpool
    private Point2d position;
    private boolean isPositionImportant;
    private Point2d rallyPosition;
    public static final Predicate<Point2d> natBunkerPosFilter = pos -> {
        Point2d natPos = GameCache.baseList.get(1).getCcPos();
        double distance = pos.distance(natPos);
        return PosConstants.natWallDepots.stream().noneMatch(p -> p.distance(natPos) + 2 < distance) &&
                PosConstants.natWall3x3s.stream().noneMatch(p -> p.distance(natPos) + 2 < distance);
    };
    public static final Function<Point2d, Double> natBunkerSortFunction = pos -> 100-pos.distance(GameCache.baseList.get(1).getCcPos());


    public static final Map<Units, Abilities> structureToActionMap;
    static {
        Hashtable<Units, Abilities> tmp = new Hashtable<Units, Abilities>();
        tmp.put(Units.TERRAN_BARRACKS, Abilities.BUILD_BARRACKS);
        tmp.put(Units.TERRAN_COMMAND_CENTER, Abilities.BUILD_COMMAND_CENTER);
        tmp.put(Units.TERRAN_SUPPLY_DEPOT, Abilities.BUILD_SUPPLY_DEPOT);
        tmp.put(Units.TERRAN_BUNKER, Abilities.BUILD_BUNKER);
        tmp.put(Units.TERRAN_ARMORY, Abilities.BUILD_ARMORY);
        tmp.put(Units.TERRAN_ENGINEERING_BAY, Abilities.BUILD_ENGINEERING_BAY);
        tmp.put(Units.TERRAN_FACTORY, Abilities.BUILD_FACTORY);
        tmp.put(Units.TERRAN_FUSION_CORE, Abilities.BUILD_FUSION_CORE);
        tmp.put(Units.TERRAN_GHOST_ACADEMY, Abilities.BUILD_GHOST_ACADEMY);
        tmp.put(Units.TERRAN_MISSILE_TURRET, Abilities.BUILD_MISSILE_TURRET);
        tmp.put(Units.TERRAN_REFINERY, Abilities.BUILD_REFINERY);
        tmp.put(Units.TERRAN_SENSOR_TOWER, Abilities.BUILD_SENSOR_TOWER);
        tmp.put(Units.TERRAN_STARPORT, Abilities.BUILD_STARPORT);
        structureToActionMap = Collections.unmodifiableMap(tmp);
    }

    //===== Constructors =====

    public PurchaseStructure(Units structureType, Unit rallyUnit, Point2d position) {
        this(null, structureType, rallyUnit, position, true);
    }
    public PurchaseStructure(Units structureType, Point2d position) {
        this(null, structureType, null, position, true);
    }
    public PurchaseStructure(Units structureType, Unit rallyUnit) {
        this(null, structureType, rallyUnit, null, false);
    }
    public PurchaseStructure(Units structureType) {
        this(null, structureType, null, null, false);
    }
    public PurchaseStructure(Unit scv, Units structureType, Point2d position) {
        this(scv, structureType, null, position, true);
    }
    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit) {
        this(scv, structureType, rallyUnit, null, false);
    }
    public PurchaseStructure(Unit scv, Units structureType) {
        this(scv, structureType, null, null, false);
    }
    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit, Point2d position) {
        this(scv, structureType, rallyUnit, position, true);
    }
    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit, Point2d position, boolean isPositionImportant) {
        this.scv = scv;
        this.structureType = structureType;
        this.rallyUnit = rallyUnit;
        this.position = position;
        this.isPositionImportant = isPositionImportant;
        structureData = Bot.OBS.getUnitTypeData(false).get(this.structureType);
        setCost();
        Print.print("Added to queue: " + this.structureType);
    }


    //===== Getters/Setters =====
    public Unit getScv() {
        return scv;
    }

    public void setScv(Unit scv) {
        this.scv = scv;
    }

    public Units getStructureType() {
        return structureType;
    }

    public void setStructureType(Units structureType) {
        this.structureType = structureType;
    }

    public Unit getRallyUnit() {
        return rallyUnit;
    }

    public void setRallyUnit(Units rallyUnit) {
        rallyUnit = rallyUnit;
    }

    public Point2d getPosition() {
        return position;
    }

    public void setPosition(Point2d position) {
        this.position = position;
    }

    public boolean isPositionImportant() {
        return isPositionImportant;
    }

    public void setPositionImportant(boolean positionImportant) {
        isPositionImportant = positionImportant;
    }

    public void setRallyUnit(Unit rallyUnit) {
        this.rallyUnit = rallyUnit;
    }

    @Override
    public Cost getCost() {
        return cost;
    }

    //========= methods =========
    //return true if going to build
    @Override
    public PurchaseResult build() {

        if (structureType == Units.INVALID) {
            return PurchaseResult.WAITING;
        }

        //if resources available
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        if (structureData.getAbility().get() == Abilities.BUILD_REFINERY) {
            return buildRefinery();
        }
        else if (!isTechRequired(structureType)) {
            return buildOther();
        }
        else {
            if (!Purchase.isBuildOrderComplete() || isTechAlmostReady(structureType, 0.8f)) { //save resources for structure if tech is almost done
                Cost.updateBank(cost);
            }
            return PurchaseResult.WAITING;
        }
    }

    public PurchaseResult buildOther() {
        //no position was given.  Find one.
        if (this.position == null) {
            if (!selectStructurePosition()) { //if no position can be set, just remove this structure from the queue
                Print.print("cancelled " + structureType + " because no position set");
                return PurchaseResult.CANCEL;
            }
        }

        //extra check that structure isn't already in position
        if (!UnitUtils.getUnitsNearbyOfType(Alliance.SELF, structureType, position, 1).isEmpty()) { //TODO: won't handle lowered depots, PFs, OCs
            return PurchaseResult.SUCCESS;
        }

        //cancel starport purchases if any existing starport is idle
        if (structureType == Units.TERRAN_STARPORT && Bot.OBS.getFoodUsed() <= 197 &&
                GameCache.starportList.stream().anyMatch(u -> ActionIssued.getCurOrder(u).isEmpty())) {
            makePositionAvailableAgain(position);
            return PurchaseResult.CANCEL;
        }

        //position unplaceable
        Abilities buildAction = (Abilities)structureData.getAbility().get();
        if (!Bot.QUERY.placement(buildAction, position)) { //if clear of creep and enemy ground units/structures
            //if structure blocks location
            if ((!Bot.OBS.getUnits(u -> UnitUtils.getDistance(u.unit(), position) < UnitUtils.getStructureRadius(structureType) &&
                    !UnitUtils.canMove(u.unit())).isEmpty()) ||
                    UnitUtils.isExpansionCreepBlocked(position)) {
                if (position.equals(PosConstants.BUNKER_NATURAL)) {
                    position = findNearbyBunkerPos();
                    if (position == null) {
                        return PurchaseResult.CANCEL;
                    }
                    else {
                        PosConstants.BUNKER_NATURAL = position;
                    }
                }
                else {
                    makePositionAvailableAgain(position);
                    return PurchaseResult.CANCEL;
                }
            }
            else { //if movable enemy unit is blocking location
                return PurchaseResult.WAITING;
            }
        }

        //if structure position unsafe, wait
        if (InfluenceMaps.getThreatToStructure(structureType, position) > 1) {
            return PurchaseResult.WAITING;
        }

        //select an scv if none was provided
        if (scv != null && Bot.OBS.getUnit(scv.getTag()) == null) {
            scv = null;
        }
        if (scv == null) {
            if (BunkerContain.proxyBunkerLevel > 0 && Time.nowFrames() < Time.toFrames("5:00") && PosConstants.baseLocations.get(0).distance(position) > 50) {
                scv = BunkerContain.getClosestAvailableRepairScvs(position);
            }
            if (scv == null) {
                UnitInPool availableScv = WorkerManager.getScvEmptyHands(this.position);
                if (availableScv == null) {
                    Print.print("cancelled " + structureType + " because no scv available");
                    makePositionAvailableAgain(position);
                    return PurchaseResult.CANCEL;
                }
                scv = availableScv.unit();
            }
        }
        Print.print("frame#" + Time.nowFrames() + ": sending action " + buildAction + " at pos: " + position.toString());
        ActionHelper.unitCommand(this.scv, buildAction, this.position, false);
        StructureScv.add(new StructureScv(Bot.OBS.getUnit(scv.getTag()), buildAction, position));
        Cost.updateBank(structureType);
        return PurchaseResult.SUCCESS;
    }

    private Point2d findNearbyBunkerPos() {
        List<Point2d> allWallPos = new ArrayList<>(PosConstants.natWallDepots);
        allWallPos.addAll(PosConstants.natWall3x3s);
        return Position.findNearestPlacement(Abilities.BUILD_BUNKER, Position.midPoint(allWallPos), 5, natBunkerPosFilter, natBunkerSortFunction);
    }

    public PurchaseResult buildRefinery() {
        if (WorkerRushDefense3.isWorkerRushed) { //no building gas during worker rush
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }

        Abilities buildAction = (Abilities)structureData.getAbility().get();
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && base.isComplete(0.55f)) {
                for (Gas gas : base.getGases()) {
                    //if geyser is available and isn't empty
                    if (gas.getRefinery() == null &&
                            gas.getNode().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY &&
                            Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.GAS_STRUCTURE_TYPES.contains(enemy.unit().getType()) &&
                                    UnitUtils.getDistance(enemy.unit(), gas.getNodePos()) < 1).isEmpty()) {
                        //if scv isn't already on the way to build at this geyser
                        if (StructureScv.scvBuildingList.stream()
                                .noneMatch(scv -> scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getNodePos()) < 1)) {
                            this.position = gas.getNodePos();
                            UnitInPool scv = WorkerManager.getScvEmptyHands(this.position);
                            if (scv == null) {
                                return PurchaseResult.WAITING;
                            }
                            this.scv = scv.unit();
                            gas.setNode(getGeyserUnitAtPosition(gas.getNodePos()));
                            Print.print("frame#" + Time.nowFrames() + ": sending action " + Abilities.BUILD_REFINERY);
                            System.out.println("GameCache.mineralBank = " + GameCache.mineralBank);
                            ActionHelper.unitCommand(this.scv, Abilities.BUILD_REFINERY, gas.getNode(), false);
                            StructureScv.add(new StructureScv(scv, buildAction, Bot.OBS.getUnit(gas.getNode().getTag())));
                            Cost.updateBank(Units.TERRAN_REFINERY);
                            return PurchaseResult.SUCCESS;
                        }
                    }
                }
            }
        }
        return PurchaseResult.WAITING;
    }


    public static boolean isTechRequired(Units unitType) {
        Units techStructureNeeded = (Units)Bot.OBS.getUnitTypeData(false).get(unitType).getTechRequirement().orElse(null);
        if (techStructureNeeded == null) {
            return false;
        }
        Set<Units> techStructureUnitsSet = UnitUtils.getUnitTypeSet(techStructureNeeded);
        if (UnitUtils.numMyUnits(techStructureUnitsSet, false) == 0) {
            if (!Purchase.isStructureQueued(techStructureNeeded) &&
                    UnitUtils.numMyUnits(techStructureUnitsSet, true) == 0) {
                if (techStructureNeeded == Units.TERRAN_FACTORY) {
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY));
                }
                else {
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(techStructureNeeded));
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isTechAlmostReady(Units unitType, float minBuildProgress) {
        Units techStructureNeeded = (Units)Bot.OBS.getUnitTypeData(false).get(unitType).getTechRequirement().orElse(null);
        if (techStructureNeeded == null) {
            return false;
        }
        return StructureScv.scvBuildingList.stream()
                .anyMatch(structureScv -> structureScv.structureType == techStructureNeeded &&
                        structureScv.getStructureUnit() != null &&
                        structureScv.getStructureUnit().unit().getBuildProgress() > minBuildProgress);
    }

    private void selectARallyUnit() {
        if (this.rallyUnit == null) {
            if (ActionIssued.getCurOrder(this.scv).isEmpty()) { //send to main base mineral patch
                this.rallyUnit = GameCache.defaultRallyNode;
            } else { //back to same mineral patch it's mining now
                this.rallyUnit = Bot.OBS.getUnit(ActionIssued.getCurOrder(this.scv).get().targetTag).unit();
            }
        }
    }

    private void makePositionAvailableAgain(Point2d pos) {
        switch (structureType) {
            case TERRAN_SUPPLY_DEPOT:
                if (UnitUtils.isWallingStructure(pos)) {
                    PosConstants.extraDepots.add(0, pos);
                }
                else {
                    PosConstants.extraDepots.add(pos);
                }
                break;
            case TERRAN_BARRACKS:
            case TERRAN_FACTORY:
            case TERRAN_STARPORT:
                PosConstants._3x3AddonPosList.add(pos);
                break;
            case TERRAN_COMMAND_CENTER:
                //ignore expansion CCs
                if (PosConstants.baseLocations.contains(pos)) {
                    return;
                }
                PosConstants.addMacroOcPos(pos);
                break;
            case TERRAN_ENGINEERING_BAY: case TERRAN_ARMORY:
                if (UnitUtils.isWallingStructure(pos)) {
                    PosConstants._3x3Structures.add(0, pos);
                }
                else {
                    PosConstants._3x3Structures.add(pos);
                }
                break;
        }
    }

    private boolean selectStructurePosition() {
        switch (structureType) {
            case TERRAN_SUPPLY_DEPOT:
                if (!PosConstants.extraDepots.isEmpty()) {
                    //1st try finding safe depot pos in fog of war. if none, then any safe depot pos
                    position = PosConstants.extraDepots.stream()
                            .filter(p -> isLocationSafeAndAvailable(p, Abilities.BUILD_SUPPLY_DEPOT))
                            //after initial build order, priority is to grant vision of my main base
                            .filter(depotPos -> Purchase.isBuildOrderComplete() ||
                                    !UnitUtils.isWallComplete() ||
                                    (Bot.OBS.getVisibility(depotPos) != Visibility.VISIBLE &&
                                            UnitUtils.isInMyMain(depotPos)))
                            .findFirst()
                            .orElse(
                                    PosConstants.extraDepots.stream()
                                            .filter(p -> isLocationSafeAndAvailable(p, Abilities.BUILD_SUPPLY_DEPOT))
                                            .findFirst()
                                            .orElse(null)
                            );
                    if (position != null) {
                        PosConstants.extraDepots.remove(position);
                        return true;
                    }
                }
                return false;
            case TERRAN_COMMAND_CENTER:
                for (Base base : GameCache.baseList) {
                    if (base.isUntakenBase() && base.isReachable() &&
                            isLocationSafeAndAvailable(base.getCcPos(), Abilities.BUILD_COMMAND_CENTER)) {
                        position = base.getCcPos(); //TODO: check for minerals/gas at base
                        return true;
                    }
                }
                return false;
            case TERRAN_BARRACKS:
                switch (Strategy.gamePlan) {
                    case GHOST_HELLBAT:
                        //first rax near wall for ghost marauder build
                        if (!Purchase.isBuildOrderComplete() &&
                                Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BARRACKS).isEmpty()) {
                            position = PosConstants._3x3AddonPosList.stream()
                                    .filter(raxPos -> UnitUtils.isInMyMain(raxPos))
                                    .min(Comparator.comparing(p -> p.distance(PosConstants.WALL_2x2)))
                                    .orElse(null);
                            if (position != null) {
                                PosConstants._3x3AddonPosList.remove(position);
                                return true;
                            }
                            return false;
                        }
                    case MARINE_RUSH: case MECH_ALL_IN:
                        return set3x3AddOnPos();
                    default:
                        return set3x3Pos();
                }
            case TERRAN_FACTORY:
            case TERRAN_STARPORT:
                return set3x3AddOnPos();
            case TERRAN_ENGINEERING_BAY: case TERRAN_ARMORY: case TERRAN_GHOST_ACADEMY: case TERRAN_FUSION_CORE:
                return set3x3Pos();
            case TERRAN_BUNKER:
                position = calcBunkerPos();
                return position != null;
            default:
                return false;
        }
    }

    private Point2d calcBunkerPos() {
        //fill nat wall
        if (Strategy.DO_WALL_NAT) {
            Point2d wallPos = PosConstants.natWall3x3s.stream()
                    .filter(p -> !PurchaseStructure.containsPos(p))
                    .filter(p -> Bot.QUERY.placement(Abilities.BUILD_BUNKER, p))
                    .findFirst()
                    .orElse(null);
            if (wallPos != null) {
                return wallPos;
            }
        }

        //use default nat pos
        if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, PosConstants.BUNKER_NATURAL)) {
            return PosConstants.BUNKER_NATURAL;
        }

        //find a nearby placeable pos near default nat pos
        return findNearbyBunkerPos();
    }

    private boolean set3x3AddOnPos() {
        if (!PosConstants._3x3AddonPosList.isEmpty()) {
            List<Point2d> freeTechLabPos = UnitUtils.getPosByAvailableTechLab();
            if (!freeTechLabPos.isEmpty()) {
                position = freeTechLabPos.get(0);
            }
            position = PosConstants._3x3AddonPosList.stream()
                    .filter(p -> isLocationSafeAndAvailable(p, Abilities.BUILD_STARPORT))
                    .filter(_3x3AddonPos -> !Purchase.isBuildOrderComplete() ||
                            (Bot.OBS.getVisibility(_3x3AddonPos) != Visibility.VISIBLE && UnitUtils.isInMyMain(_3x3AddonPos))) //after initial build order, priority is to grant vision of my main base
                    .findFirst()
                    .orElse(
                            PosConstants._3x3AddonPosList.stream()
                                    .filter(p -> isLocationSafeAndAvailable(p, Abilities.BUILD_STARPORT))
                                    .findFirst()
                                    .orElse(null)
                    );
            if (position != null) {
                PosConstants._3x3AddonPosList.remove(position);
                return true;
            }
        }
        return false;
    }

    private boolean set3x3Pos() {
        position = PosConstants._3x3Structures.stream()
                .filter(p -> isLocationSafeAndAvailable(p, Bot.OBS.getUnitTypeData(false).get(structureType).getAbility().get()))
                .filter(starportPos -> KetrocBot.purchaseQueue.size() > 1 || !UnitUtils.isWallComplete() || Bot.OBS.getVisibility(starportPos) != Visibility.VISIBLE) //after initial build order, priority is to grant vision of my main base
                .findFirst()
                .orElse(
                        PosConstants._3x3Structures.stream()
                                .filter(p -> isLocationSafeAndAvailable(p, Bot.OBS.getUnitTypeData(false).get(structureType).getAbility().get()))
                                .findFirst()
                                .orElse(null)
                );
        if (position != null) {
            PosConstants._3x3Structures.remove(position);
            return true;
        }
        else if (!PosConstants._3x3AddonPosList.isEmpty()) { //use starport position if none remain
            position = PosConstants._3x3AddonPosList.remove(PosConstants._3x3AddonPosList.size()-1);
            return true;
        }
        return false;
    }

    private boolean isLocationSafeAndAvailable(Point2d p, Ability buildAbility) {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, p) == 0 &&
                Bot.QUERY.placement(buildAbility, p);
    }

    public static int countUnitType(Units unitType) {
        int numUnitType = UnitUtils.numMyUnits(unitType, false);
        switch (unitType) {
            case TERRAN_STARPORT:
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_STARPORT_FLYING, false);
                break;
            case TERRAN_FACTORY:
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_FACTORY_FLYING, false);
                break;
            case TERRAN_BARRACKS:
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_BARRACKS_FLYING, false);
                break;
            case TERRAN_SUPPLY_DEPOT:
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_SUPPLY_DEPOT_LOWERED, false);
                break;
            case TERRAN_COMMAND_CENTER:
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_ORBITAL_COMMAND, false);
                numUnitType += UnitUtils.numMyUnits(Units.TERRAN_PLANETARY_FORTRESS, false);
                break;
        }
        return numUnitType;
    }

    public static Unit getGeyserUnitAtPosition(Point2d location) { //TODO: null handle
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType()) && u.unit().getPosition().toPoint2d().distance(location) < 1)
                .get(0).unit();
    }

    public static Optional<PurchaseStructure> getPurchase(Units structureType) {
        return KetrocBot.purchaseQueue.stream()
                .filter(purchase -> purchase instanceof PurchaseStructure &&
                        ((PurchaseStructure) purchase).structureType == structureType)
                .findFirst()
                .map(purchase -> (PurchaseStructure)purchase);
    }

    private static boolean containsPos(Point2d pos) {
        return KetrocBot.purchaseQueue.stream()
                .anyMatch(purchase -> purchase instanceof PurchaseStructure &&
                        ((PurchaseStructure) purchase).position != null &&
                        ((PurchaseStructure) purchase).position.distance(pos) < 1);
    }

    @Override
    public void setCost() {
        cost = Cost.getUnitCost(structureType);
    }

    @Override
    public boolean canAfford() {
        return GameCache.mineralBank >= cost.minerals && (cost.gas == 0 || GameCache.gasBank >= cost.gas);
    }

    @Override
    public String getType() {
        return structureType.toString();
    }

}
