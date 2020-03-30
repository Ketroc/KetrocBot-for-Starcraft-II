package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.Gas;

import java.util.*;

public class PurchaseStructure implements Purchase { //TODO: add rally point
    private Unit scv;  //okay to not be unitInPool as it's only set the same frame the build command is given
    private Units structureType;
    private Cost cost;
    private UnitTypeData structureData;
    private Unit rallyUnit; //what to rally to afterwards (typically a mineral patch) TODO: change to unitinpool
    private Point2d position;
    private boolean isPositionImportant;
    private Point2d rallyPosition;


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
        System.out.println("Added to queue: " + this.structureType);
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
        //if resources available and prerequisite structure done
        if (!canAfford()) {
            Cost.updateBank(cost);
            return PurchaseResult.WAITING;
        }
        if (!structureData.getTechRequirement().isPresent() || countUnitType((Units)structureData.getTechRequirement().get()) > 0) {
            if (structureData.getAbility().get() == Abilities.BUILD_REFINERY) { //TODO: restructure this as refineries never have a tech requirement??
                return buildRefinery();
            }
            else {
                return buildOther();
            }
        }
        //if tech requirement doesn't exist TODO: add tech to purchase queue here???
        return PurchaseResult.WAITING;
    }

    public PurchaseResult buildOther() {
        if (this.position == null) {  //no position was given.  Find one.
            //TODO: set random nearby and available location to build
            if (!selectStructurePosition()) { //if no position can be set, just remove this structure from the queue
                System.out.println("cancelled " + structureType + " because no position set");
                return PurchaseResult.CANCEL;
            }
        }
        Ability buildAction = structureData.getAbility().get();
        if (this.scv == null) { //select an scv if none was provided
            List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(this.position);
            if (availableScvs.isEmpty()) {
                System.out.println("cancelled " + structureType + " because no scv available");
                makePositionAvailableAgain(position);
                return PurchaseResult.CANCEL;
            }
            this.scv = availableScvs.
                    get(0).unit();
        }
        if (this.rallyUnit == null) { //select a rally point for the scv if none is provided
            //selectARallyUnit();
        }
        System.out.println("sending action @" + Bot.OBS.getGameLoop() + buildAction);
        Bot.ACTION.unitCommand(this.scv, buildAction, this.position, false);
                //.unitCommand(this.scv, Abilities.SMART, this.rallyUnit, true);
        Cost.updateBank(structureType);
        return PurchaseResult.SUCCESS;
    }

    public PurchaseResult buildRefinery() {
        Ability buildAction = structureData.getAbility().get();
        for (Base base : GameState.baseList) {
            for (Gas gas : base.getGases()) {
                if (gas.getRefinery() == null && gas.getGeyser().unit().getVespeneContents().orElse(0) > Strategy.MIN_GAS_FOR_REFINERY) { //if geyser is available and isn't empty
                    this.position = gas.getLocation().toPoint2d();
                    List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(this.position);
                    if (availableScvs.isEmpty()) {
                        return PurchaseResult.WAITING;
                    }
                    this.scv = availableScvs.get(0).unit();
                    gas.setGeyser(getGeyserUnitAtPosition(gas.getLocation()));
                    System.out.println("sending action @" + Bot.OBS.getGameLoop() + Abilities.BUILD_REFINERY);
                    Bot.ACTION.unitCommand(this.scv, Abilities.BUILD_REFINERY, gas.getGeyser().unit(), false);
                    Cost.updateBank(Units.TERRAN_REFINERY);
                    return PurchaseResult.SUCCESS;
                }
            }
        }
        //if no gas geysers left on my bases
        return PurchaseResult.CANCEL;
    }

    private void selectARallyUnit() {
        if (this.rallyUnit == null) {
            if (this.scv.getOrders().isEmpty()) { //send to main base mineral patch
                this.rallyUnit = GameState.mineralNodeRally;
            } else { //back to same mineral patch it's mining now
                this.rallyUnit = Bot.OBS.getUnit(
                        this.scv.getOrders().get(0).getTargetedUnitTag().get()
                ).unit();
            }
        }
    }

    private void makePositionAvailableAgain(Point2d pos) {
        switch (structureType) {
            case TERRAN_SUPPLY_DEPOT:
                LocationConstants.extraDepots.add(pos);
                break;
            case TERRAN_STARPORT:
                LocationConstants.STARPORTS.add(pos);
                break;
            case TERRAN_COMMAND_CENTER:
                //ignore expansion CCs
                for (Point ccPos : LocationConstants.myExpansionLocations) {
                    if (ccPos.toPoint2d().distance(pos) < 1) {
                        return;
                    }
                }
                LocationConstants.MACRO_OCS.add(pos);
                break;
        }
    }


    private boolean selectStructurePosition() {
        switch (structureType) {
            case TERRAN_SUPPLY_DEPOT:
                if (!LocationConstants.extraDepots.isEmpty()) { //TODO: add more hardcoded positions or create a position
                    position = LocationConstants.extraDepots.remove(0);
                    return true;
                }
                return false;
            case TERRAN_COMMAND_CENTER:
                for (Point ccPos : LocationConstants.myExpansionLocations.subList(0, LocationConstants.myExpansionLocations.size() - Strategy.NUM_DONT_EXPAND)) {
                    if (!UnitUtils.isUnitTypesNearby(List.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_ORBITAL_COMMAND), ccPos.toPoint2d(), 1)) { //this includes enemy
                        position = ccPos.toPoint2d(); //TODO: check for minerals/gas at base
                        return true;
                    }
                }
                return false;
            case TERRAN_STARPORT:
                if (!LocationConstants.STARPORTS.isEmpty()) { //TODO: add more hardcoded positions or create a position
                    position = LocationConstants.STARPORTS.remove(0);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    public static int countUnitType(Units unitType) {
        int numUnitType = GameState.allFriendliesMap.getOrDefault(unitType, Collections.emptyList()).size();
        switch (unitType) {
            case TERRAN_STARPORT:
                numUnitType += GameState.allFriendliesMap.getOrDefault(Units.TERRAN_STARPORT_FLYING, Collections.emptyList()).size();
                break;
            case TERRAN_FACTORY:
                numUnitType += GameState.allFriendliesMap.getOrDefault(Units.TERRAN_FACTORY_FLYING, Collections.emptyList()).size();
                break;
            case TERRAN_BARRACKS:
                numUnitType += GameState.allFriendliesMap.getOrDefault(Units.TERRAN_BARRACKS_FLYING, Collections.emptyList()).size();
                break;
            case TERRAN_SUPPLY_DEPOT:
                numUnitType += GameState.allFriendliesMap.getOrDefault(Units.TERRAN_SUPPLY_DEPOT_LOWERED, Collections.emptyList()).size();
                break;
        }
        return numUnitType;
    }

    public static UnitInPool getGeyserUnitAtPosition(Point location) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u -> Base.GAS_GEYSER_TYPE.contains(u.unit().getType()) && u.unit().getPosition().distance(location) < 1)
                .get(0);
    }

    @Override
    public void setCost() {
        cost = Cost.getUnitCost(structureType);
    }

    @Override
    public boolean canAfford() {
        return GameState.mineralBank >= cost.minerals && GameState.gasBank >= cost.gas;
    }

    @Override
    public String getType() {
        return structureType.toString();
    }
} //end PurchaseStructure class
