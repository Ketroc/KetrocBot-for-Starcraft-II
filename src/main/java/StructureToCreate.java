import com.github.ocraft.s2client.bot.gateway.ActionInterface;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;

import java.util.*;

public class StructureToCreate { //TODO: add rally point
    private Unit scv;
    private Units structureType;
    private Unit rallyUnit; //what to rally to afterwards (typically a mineral patch)
    private Point2d position;
    private boolean isPositionImportant;
    private ObservationInterface obs;

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
    public StructureToCreate(ObservationInterface obs, Units structureType, Unit rallyUnit, Point2d position) {
        this(obs, null, structureType, rallyUnit, position, true);
    }
    public StructureToCreate(ObservationInterface obs, Units structureType, Point2d position) {
        this(obs, null, structureType, null, position, true);
    }
    public StructureToCreate(ObservationInterface obs, Units structureType, Unit rallyUnit) {
        this(obs, null, structureType, rallyUnit, null, false);
    }
    public StructureToCreate(ObservationInterface obs, Units structureType) {
        this(obs, null, structureType, null, null, false);
    }
    public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Point2d position) {
        this(obs, scv, structureType, null, position, true);
    }
    public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit) {
        this(obs, scv, structureType, rallyUnit, null, false);
    }
    public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType) {
        this(obs, scv, structureType, null, null, false);
    }
    public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit, Point2d position) {
        this(obs, scv, structureType, rallyUnit, position, true);
    }
    public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit, Point2d position, boolean isPositionImportant) {
        this.obs = obs;
        this.scv = scv;
        this.structureType = structureType;
        this.rallyUnit = rallyUnit;
        this.position = position;
        this.isPositionImportant = isPositionImportant;

        if (this.position == null) {
            //TODO: set random nearby and available location to build
        }
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



    //========= methods =========

    private boolean isMiningMinerals(UnitInPool scv) {

        Optional<Tag> scvTargetTag = scv.unit().getOrders().get(0).getTargetedUnitTag();
        if (!scvTargetTag.isPresent()) { //return false if scv has no target
            return false;
        }
        else { //return true if scv target is a mineral node
            List<Units> mineralPatchTypes = Arrays.asList(Units.NEUTRAL_MINERAL_FIELD,
                    Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750);
            return mineralPatchTypes.contains(obs.getUnit(scvTargetTag.get()).unit().getType());

        }
    }

    //sets this.scv and this.rallyUnit fields if null in constructor

    private void findNearestScv() {  //TODO: null handling
        List<UnitInPool> scvList;
        scvList = obs.getUnits(Alliance.SELF, scv -> {
            return scv.unit().getType() == Units.TERRAN_SCV && //is scv
                !scv.unit().getOrders().isEmpty() && //not idle
                scv.unit().getOrders().get(0).getAbility() != Abilities.HARVEST_RETURN && // is not returning minerals
                isMiningMinerals(scv); //is mining minerals
        });

        UnitInPool closestScv = scvList.get(0);
        double closestDistance = this.position.distance(closestScv.unit().getPosition().toPoint2d());
        scvList.remove(0);
        for (UnitInPool scv : scvList) {
            double curDistance = this.position.distance(scv.unit().getPosition().toPoint2d());
            if (curDistance < closestDistance) {
                closestScv = scv;
                closestDistance = curDistance;
            }
        }
        this.scv = closestScv.unit();

        if (closestScv.unit().getOrders().isEmpty()) {
            this.rallyUnit = findNearestMineralPatch(obs).get();
        }
        else {
            this.rallyUnit = obs.getUnit(
                    closestScv.unit().getOrders().get(0).getTargetedUnitTag().get()
            ).unit();
        }
    }

    //finds nearest mineral patch to the structure position
    private Optional<Unit> findNearestMineralPatch(ObservationInterface obs) {
        List<UnitInPool> units = obs.getUnits(Alliance.NEUTRAL);
        double distance = Double.MAX_VALUE;
        Unit target = null;
        for (UnitInPool unitInPool : units) {
            Unit unit = unitInPool.unit();
            if (unit.getType().equals(Units.NEUTRAL_MINERAL_FIELD)) {
                double d = unit.getPosition().toPoint2d().distance(this.position);
                if (d < distance) {
                    distance = d;
                    target = unit;
                }
            }
        }
        return Optional.ofNullable(target);
    }

    //return true if going to build
    public boolean buildStructure(ActionInterface action) {
        UnitTypeData structureData = obs.getUnitTypeData(false).get(this.structureType);
        Ability buildAction = structureData.getAbility().get();
        //if resources available and prerequisite structure done
        if (obs.getMinerals() >= structureData.getMineralCost().get() &&
                obs.getVespene() >= structureData.getVespeneCost().get() &&
                (!structureData.getTechRequirement().isPresent() ||
                countUnitType(structureData.getTechRequirement().get()) > 0)) { //TODO: it shouldn't count in progress structures
            if (this.scv == null) {
                findNearestScv();
            }
            action.unitCommand(this.scv, buildAction, this.position, false)
                    .unitCommand(this.scv, Abilities.SMART, this.rallyUnit, true);
            return true;
        }
        else {
            return false;
        }
    }

    private int countUnitType(UnitType unitType) {
        return obs.getUnits(Alliance.SELF,
                unitInPool -> unitInPool.unit().getType() == unitType && unitInPool.unit().getBuildProgress() == 1.0f).size();
    }

} //end StructureToCreate class
