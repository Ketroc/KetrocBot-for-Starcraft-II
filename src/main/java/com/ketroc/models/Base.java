package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.BasicUnitMicro;
import com.ketroc.micro.Liberator;
import com.ketroc.micro.MicroPriority;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Base {
    public long lastScoutedFrame;
    public boolean isEnemyBase;
    private boolean isDryedUp;
    private Point2d ccPos;
    private UnitInPool cc;
    private List<Gas> gases = new ArrayList<>();
    private List<MineralPatch> mineralPatches = new ArrayList<>();
    private Unit rallyNode; //mineral node this cc is rallied to
    private Point2d resourceMidPoint = null;
    private List<DefenseUnitPositions> turrets = new ArrayList<>();
    private List<DefenseUnitPositions> liberators = new ArrayList<>();
    private List<DefenseUnitPositions> tanks = new ArrayList<>();
    private static float libDistanceFromCC = -1;
    private boolean continueUnsieging;
    private boolean onMyBaseDeath;
    private boolean onNewBaseTaken;
    public long prevMuleSpamFrame;
    public int scvsAddedThisFrame;
    public long lastMorphFrame;

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


    public List<Unit> getMineralPatchUnits() {
        return mineralPatches.stream()
                .map(MineralPatch::getNode)
                .collect(Collectors.toList());
    }

    public List<MineralPatch> getMineralPatches() {
        return mineralPatches;
    }

    public void setMineralPatches(List<MineralPatch> mineralPatches) {
        this.mineralPatches = mineralPatches;
    }

    public UnitInPool getCc() {
        return cc;
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
            onNewBaseTaken = true;
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
//        if (turrets.isEmpty() && !isMyMainBase()) {
//            //middle turret
//            turrets.add(new DefenseUnitPositions(
//                    Position.moveClearExactly(resourceMidPoint, ccPos, 3.5f), null));
//
//            //extra side turrets
//            turrets.add(new DefenseUnitPositions(
//                    Position.moveClearExactly(Position.rotate(resourceMidPoint, ccPos, 100), ccPos, 3.5f), null));
//            turrets.add(new DefenseUnitPositions(
//                    Position.moveClearExactly(Position.rotate(resourceMidPoint, ccPos, -100), ccPos, 3.5f), null));
//        }
        return turrets;
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
                tanks.sort(Comparator.comparing(defPos -> defPos.getPos().distance(LocationConstants.pointOnMyRamp)));
            }
        }
        return tanks;
    }

    public void setTanks(List<DefenseUnitPositions> tanks) {
        this.tanks = tanks;
    }

    public int numScvsFromSoftSaturated() {
        if (cc == null) {
            return 0;
        }
        int numRepairingScvs = (int)UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, ccPos, 10).stream()
                .filter(scv -> UnitUtils.getOrder(scv.unit()) == Abilities.EFFECT_REPAIR)
                .count();
        return cc.unit().getIdealHarvesters().get() - (getNumMineralScvs() + numRepairingScvs + scvsAddedThisFrame);
    }

    public int numScvsFromHardSaturated() {
        if (cc == null) {
            return 0;
        }
        int numRepairingScvs = (int)UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, ccPos, 10).stream()
                .filter(scv -> UnitUtils.getOrder(scv.unit()) == Abilities.EFFECT_REPAIR)
                .count();
        return getScvsRequiredForHardSaturation() - (getNumMineralScvs() + numRepairingScvs + scvsAddedThisFrame);
    }

    public int getScvsRequiredForHardSaturation() {
        int numScvsRequired = 0;
        for (MineralPatch mineral : mineralPatches) {
            if (UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(mineral.getNode().getType())) {
                numScvsRequired += 2;
            }
            else {
                numScvsRequired += 3;
            }
        }
        return numScvsRequired;
    }

    public int getNumMineralScvs() {
        return mineralPatches.stream().mapToInt(mineralPatch -> mineralPatch.getScvs().size()).sum();
    }

    public List<UnitInPool> getMineralScvs() {
        return mineralPatches.stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .collect(Collectors.toList());
    }

    public List<UnitInPool> getGasScvs() {
        return gases.stream()
                .flatMap(gas -> gas.getScvs().stream())
                .collect(Collectors.toList());
    }

    public List<UnitInPool> getAllScvs() {
        List<UnitInPool> allScvs = getMineralScvs();
        allScvs.addAll(getGasScvs());
        return allScvs;
    }

    public void addMineralScv(Unit scv) {
        scvsAddedThisFrame++;
        mineralPatches.stream()
                .map(MineralPatch::getNode)
                .max(Comparator.comparing(mineral -> mineral.getMineralContents().orElse(0)))
                .ifPresent(mineral -> ActionHelper.giveScvCommand(scv, Abilities.SMART, mineral, false));
    }


    // ============ METHODS ==============

    public void onStep() {
        if (onMyBaseDeath) {
            onMyBaseDeath();
            onMyBaseDeath = false;
        }
        if (onNewBaseTaken && isReadyForMining()) {
            onNewBaseTaken();
            onNewBaseTaken = false;
        }
        if (continueUnsieging) {
            if (!InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, ccPos)) {
                unsiegeBase();
                continueUnsieging = false;
            }
        }
    }

    public void onStepEnd() {
        mineralPatches.forEach(mineralPatch -> {
            mineralPatch.getScvs().forEach(scv -> {
                //detour if scv can be 2-shot
                if (shouldFlee(scv)) {
                    DebugHelper.drawBox(mineralPatch.getByNode(), Color.RED, 0.2f);
                    DebugHelper.drawBox(mineralPatch.getByCC(), Color.RED, 0.2f);
                    new BasicUnitMicro(scv, mineralPatch.getNodePos(), MicroPriority.SURVIVAL).onStep();
                }
                else if (UnitUtils.isCarryingResources(scv.unit())) {
                    if (isReadyForMining()) {
                        mineralPatch.returnMicro(scv.unit());
                    }
                    else {
                        mineralPatch.distanceReturnMicro(scv.unit());
                    }
                }
                else {
                    if (isReadyForMining()) {
                        mineralPatch.harvestMicro(scv.unit());
                    }
                    else {
                        mineralPatch.distanceHarvestMicro(scv.unit());
                    }
                }
            });
        });

        gases.forEach(gas -> {
            gas.getScvs().forEach(scv -> {
                //detour if scv can be 2-shot
                if (shouldFlee(scv)) {
                    DebugHelper.drawBox(gas.getByNode(), Color.RED, 0.2f);
                    DebugHelper.drawBox(gas.getByCC(), Color.RED, 0.2f);
                    new BasicUnitMicro(scv, gas.getNodePos(), MicroPriority.SURVIVAL).onStep();
                }

                //fix scv if not mining wrong node
                else if (ActionIssued.getCurOrder(scv.unit()).stream()
                        .anyMatch(order -> order.ability == Abilities.HARVEST_GATHER &&
                                !gas.getRefinery().getTag().equals(order.targetTag))) {
                    ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_GATHER, gas.getRefinery(), false);
                }

                //mine normally if 3 scvs
                else if (gas.getScvs().size() >= 3 || gas.getRefinery().getType() == Units.TERRAN_REFINERY_RICH) {
                    if (!scv.unit().getActive().orElse(true)) {
                        ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_GATHER, gas.getRefinery(), false);
                    }
                }
                //speed return
                else if (UnitUtils.isCarryingResources(scv.unit())) {
                    gas.returnMicro(scv.unit());
                }
                //speed mine
                else {
                    gas.harvestMicro(scv.unit());
                }
            });
        });
    }


    private boolean shouldFlee(UnitInPool scv) {
        //flee to any danger if distance mining
        if (!isReadyForMining() && InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, scv.unit().getPosition().toPoint2d())) {
            return true;
        }
        else if (UnitUtils.canBeOneShot(scv.unit())) {
            List<UnitInPool> enemiesInAttackRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                    UnitUtils.getAttackRange(enemy.unit(), Weapon.TargetType.GROUND) + Strategy.KITING_BUFFER >
                            UnitUtils.getDistance(scv.unit(), enemy.unit()));

            //don't flee if near PF when enemy ground in range
            boolean enemyGroundAttackersInRange = enemiesInAttackRange.stream().anyMatch(enemy -> !enemy.unit().getFlying().orElse(true));
            boolean baseIsPF = getCc() != null && getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS;
            if (enemyGroundAttackersInRange && baseIsPF) {
                return false;
            }

            //don't flee if near missile turret when enemy air in range
            boolean enemyAirAttackersInRange = enemiesInAttackRange.stream().anyMatch(enemy -> enemy.unit().getFlying().orElse(false));
            boolean baseHasTurret = getTurrets().stream().anyMatch(turret -> turret.getUnit() != null && turret.getUnit().unit().getBuildProgress() == 1);
            if (enemyAirAttackersInRange && baseHasTurret) {
                return false;
            }

            return true;
        }

        return false;
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
        return (int)mineralPatches.stream()
                .map(MineralPatch::getNode)
                .filter(patch -> patch.getMineralContents().orElse(0) > 100).count();
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
                (UnitUtils.getOrder(u.unit()) != Abilities.ATTACK));
        return UnitUtils.toUnitList(idleLibs);
    }

    public List<Unit> getSiegedLibs() {
        return UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_LIBERATOR_AG, ccPos, 10));
    }

    public List<Unit> getLargeMinerals() {
        return mineralPatches.stream()
                .map(MineralPatch::getNode)
                .filter(node -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType())).collect(Collectors.toList());
    }

    public List<Point2d> initTankPositions() {
        return null; //TODO stub
    }

    public List<Unit> getOuterBigPatches() {
        List<Unit> minMaxNodes = new ArrayList<>(List.of(
                mineralPatches.stream()
                        .map(MineralPatch::getNode)
                        .max(Comparator.comparing(node -> node.getPosition().getX())).get(),
                mineralPatches.stream()
                        .map(MineralPatch::getNode)
                        .max(Comparator.comparing(node -> node.getPosition().getY())).get(),
                mineralPatches.stream()
                        .map(MineralPatch::getNode)
                        .min(Comparator.comparing(node -> node.getPosition().getX())).get(),
                mineralPatches.stream()
                        .map(MineralPatch::getNode)
                        .min(Comparator.comparing(node -> node.getPosition().getY())).get()
        ));
        minMaxNodes.removeIf(node -> !UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType()));
        if (minMaxNodes.size() != 2) {
            Print.print("found more than 2 outer patches");
            minMaxNodes.stream().forEach(unit -> Print.print(unit.getPosition().toPoint2d()));
        }
        return minMaxNodes;
    }

    public Unit getFullestMineralPatch() {
        return mineralPatches.stream()
                .map(MineralPatch::getNode)
                .max(Comparator.comparing(node -> node.getMineralContents().orElse(0)))
                .orElse(null);
    }

    public List<Unit> getInnerBigPatches() {
        //get list of big patches
        List<Unit> bigPatches = mineralPatches.stream()
                .map(MineralPatch::getNode)
                .filter(node -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType()))
                .collect(Collectors.toList());

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

    public UnitInPool getUpdatedUnit(Units unitType, UnitInPool unit, Point2d pos) {
        //check for new structure
        if (unit == null) {
            UnitInPool newUnit;
            if (UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN.contains(unitType)) {
                newUnit = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN, pos, 1).stream()
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
        if (!unit.isAlive()) {
            return null;
        }
        return unit;
    }

    public boolean isMyMainBase() {
        return this.equals(GameCache.baseList.get(0));
    }

    public void unsiegeBase() {
        //unsiege liberators and tanks
        freeUpLiberators();
        freeUpTanks();
    }

    public void onNewBaseTaken() {
        //make distance mining scvs available
        GameCache.baseList.stream()
                .filter(base -> !base.isMyBase())
                .flatMap(base -> base.mineralPatches.stream())
                .forEach(mineralPatch -> {
                    mineralPatch.getScvs().forEach(scv -> ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false));
                    mineralPatch.getScvs().clear();
                });

        //set rally
        Unit rallyNode = getFullestMineralPatch();
        if (rallyNode != null) {
            ActionHelper.unitCommand(cc.unit(), Abilities.RALLY_COMMAND_CENTER, rallyNode, false);
        }
    }

    public void onMyBaseDeath() {
        //send all scvs to another base's mineral patch
//        List<Unit> baseScvs = UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Set.of(Units.TERRAN_SCV, Units.TERRAN_MULE), ccPos, 7));
//        Unit mineralPatch = UnitUtils.getSafestMineralPatch();
//        List<Unit> scvsCarrying = baseScvs.stream().filter(unit -> UnitUtils.isCarryingResources(unit)).collect(Collectors.toList());//scvs carrying return cargo first
//        if (!scvsCarrying.isEmpty()) {
//            ActionHelper.unitCommand(scvsCarrying, Abilities.HARVEST_RETURN, false);
//            if (mineralPatch == null) {
//                ActionHelper.unitCommand(scvsCarrying, Abilities.STOP, true);
//            }
//            else {
//                ActionHelper.unitCommand(scvsCarrying, Abilities.SMART, mineralPatch, true);
//            }
//        }
//        List<Unit> scvsNotCarrying = baseScvs.stream().filter(unit -> !UnitUtils.isCarryingResources(unit)).collect(Collectors.toList());
//        if (!scvsNotCarrying.isEmpty()) {
//            if (mineralPatch == null) {
//                ActionHelper.unitCommand(scvsNotCarrying, Abilities.STOP, false);
//            }
//            else {
//                ActionHelper.unitCommand(scvsNotCarrying, Abilities.SMART, mineralPatch, false);
//            }
//        }
        mineralPatches.forEach(mineralPatch ->
                mineralPatch.getScvs().forEach(scv ->
                        ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false)));
        mineralPatches.forEach(mineralPatch -> mineralPatch.getScvs().clear());
        gases.forEach(gas ->
                gas.getScvs().forEach(scv ->
                        ActionHelper.unitCommand(scv.unit(), Abilities.STOP, false)));
        gases.forEach(gas -> gas.getScvs().clear());
        //TODO: delayed action for scv inside the refinery

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

        //TODO: cancel tanks and liberators (send on offense?  send back to main base?  need arrivalRange, and 2nd onCompletion option in micro classes)


    }

    //make this bases liberators aa mode and idle so they can be picked up for a new base TODO: time delay this
    private void freeUpLiberators() {
        for (DefenseUnitPositions libPos : getLiberators()) {
            if (libPos.getUnit() != null) {
                Unit baseLib = libPos.getUnit().unit();
                if (baseLib.getType() == Units.TERRAN_LIBERATOR_AG) {
                    ActionHelper.unitCommand(baseLib, Abilities.MORPH_LIBERATOR_AA_MODE, false);
                } else {
                    ActionHelper.unitCommand(baseLib, Abilities.STOP, false);
                }
                libPos.setUnit(null);
            }
        }
    }

    private void freeUpTanks() {
        for (DefenseUnitPositions tankPos : getTanks()) {
            if (tankPos.getUnit() != null) {
                UnitMicroList.remove(tankPos.getUnit().getTag());
                tankPos.setUnit(null);
            }
        }
    }

    public boolean isReadyForMining() {
        return isMyBase() && isComplete();
    }

    public List<UnitInPool> getAndReleaseAvailableScvs(int numScvs) {
        List<UnitInPool> baseScvs = WorkerManager.getAllScvs(ccPos, 9).stream()
                .filter(scv -> !scv.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) &&
                        !scv.unit().getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS) &&
                        !scv.unit().getBuffs().contains(Buffs.CARRY_HIGH_YIELD_MINERAL_FIELD_MINERALS))
                .limit(numScvs)
                .collect(Collectors.toList());
        baseScvs.forEach(scv -> releaseScv(scv.unit()));
        return baseScvs;
    }

    public void setTurretPositions() {
        //TODO: handle mineral-only base (just put 1 turret at resource midpoint??)

        //1 gas base
        if (getGases().size() == 1) {
            addTurretPosForEach1GasSide();
            addTurretPosFor0GasSide();
        }
        //if gases next to each other
        else if (getGases().get(0).getNodePos().distance(getGases().get(1).getNodePos()) < 9) {
            addTurretPosFor2GasSide();
            addTurretPosFor0GasSide();
        }
        else {
            addTurretPosForEach1GasSide();
        }
    }

    private void addTurretPosFor0GasSide() {
        Point2d farMineralPos = getMineralPatchUnits().stream()
                .max(Comparator.comparing(mineral -> UnitUtils.getDistance(mineral, getGases().get(0).getNodePos())))
                .get().getPosition().toPoint2d();
        Point2d ccTowardsMineral = Position.toWholePoint(Position.towards1dDistance(getCcPos(), farMineralPos, 3.5f));
        System.out.println(Math.abs(ccTowardsMineral.getX() - getCcPos().getX()));
        Point2d turretPos = Position.toWholePoint(
                Position.towards(ccTowardsMineral, getResourceMidPoint(), -1));
        if (!PlacementMap.canFit2x2(turretPos)) {
            turretPos = ccTowardsMineral;
        }
        DebugHelper.drawBox(turretPos, Color.GREEN, 1f);
        turrets.add(new DefenseUnitPositions(turretPos, null));

    }

    private void addTurretPosFor2GasSide() {
        Point2d gasMidPoint = Position.midPoint(getGases().get(0).getNodePos(), getGases().get(1).getNodePos());
        Point2d midPointTowardsCc = Position.towards(gasMidPoint, getCcPos(), 1f);
        Point2d turretPos = Position.toWholePoint(midPointTowardsCc);
        if (!PlacementMap.canFit2x2(turretPos)) {
            Point2d finalMidPointTowardsCc = midPointTowardsCc;
            turretPos = Position.getSpiralList(turretPos, 2).stream()
                    .filter(pos -> PlacementMap.canFit2x2(pos))
                    .min(Comparator.comparing(pos -> pos.distance(finalMidPointTowardsCc)))
                    .get();

        }
        DebugHelper.drawBox(turretPos, Color.GREEN, 1f);
        turrets.add(new DefenseUnitPositions(turretPos, null));
    }

    public Point2d inFrontPos() {
        return Position.towards(ccPos, getResourceMidPoint(), -4.5f);
    }

    private void addTurretPosForEach1GasSide() {
        for (Gas gas : getGases()) {
            //closest mineral to gas node
            Unit closestMineral = getMineralPatchUnits().stream().min(Comparator.comparing(mineral -> UnitUtils.getDistance(mineral, gas.getNodePos()))).get();

            Point2d gasTowardsCC = Position.toWholePoint(Position.towards1dDistance(gas.getNodePos(), getCcPos(), 2.5f));
            Point2d turretPos = (Math.abs(gasTowardsCC.getX() - gas.getNodePos().getX()) == 2.5f) ?
                    Position.towardsYAxis(gasTowardsCC, closestMineral.getPosition().toPoint2d(), 1f) :
                    Position.towardsXAxis(gasTowardsCC, closestMineral.getPosition().toPoint2d(), 1f);
            if (!PlacementMap.canFit2x2(turretPos)) {
                turretPos = gasTowardsCC;
            }
            DebugHelper.drawBox(turretPos, Color.GREEN, 1f);
            turrets.add(new DefenseUnitPositions(turretPos, null));
        }
    }


    // ======= STATIC METHODS ========

    public static void onGameStart() {
        GameCache.baseList.forEach(base -> base.setTurretPositions());
    }

    public static float getLibDistanceFromCC() {
        if (libDistanceFromCC == -1 || LocationConstants.opponentRace == Race.RANDOM) {
            libDistanceFromCC = (LocationConstants.opponentRace == Race.PROTOSS) ? 1f : 2.5f;
        }
        if (Liberator.castRange == 8) { //apply range upgrade TODO: handle range change onUpgradeComplete
            libDistanceFromCC += 3;
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

    public static int numAvailableBases() {
        return (int) GameCache.baseList.stream()
                .filter(base -> base.isUntakenBase() && !base.isDryedUp)
                .count();
    }

    public static Point2d getNextAvailableBase() {
        return GameCache.baseList.stream()
                .filter(base -> base.isUntakenBase() && !base.isDryedUp)
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
    }

    public static Base getBase(Unit cc) {
        return GameCache.baseList.stream()
                .filter(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 1)
                .findFirst()
                .orElse(null);
    }

    public static Base getBase(Point2d ccPos) {
        return GameCache.baseList.stream()
                .filter(base -> ccPos.distance(base.getCcPos()) < 1)
                .findFirst()
                .orElse(null);
    }

    public static void setBaseMorphTime(Unit cc) {
        GameCache.baseList.stream()
                .filter(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 1)
                .findFirst()
                .ifPresent(base -> base.lastMorphFrame = Time.nowFrames());
    }

    public static List<Unit> getAllMineralScvs() {
        return GameCache.baseList.stream()
                .flatMap(base -> base.getMineralPatches().stream())
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .map(UnitInPool::unit)
                .collect(Collectors.toList());
    }

    public static boolean assignScvToAMineralPatch(UnitInPool scv) {
        //pair up scvs on mineral patches
        MineralPatch mineralToMine = GameCache.baseList.stream()
                .filter(base -> base.isReadyForMining() && base.numScvsFromSoftSaturated() > 0)
                .min(Comparator.comparing(base -> UnitUtils.getDistance(scv.unit(), base.getCcPos())))
                .stream()
                .flatMap(base -> base.getMineralPatches().stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() < 2)
                .max(Comparator.comparing(mineralPatch -> mineralPatch.getNode().getMineralContents().orElse(0)))
                .orElse(null);
        if (mineralToMine != null) {
            mineralToMine.getScvs().add(scv);
            return true;
        }

        //TODO: add 3rd scv to small patches
//        MineralPatch smallMineralToMine = GameCache.baseList.stream()
//                .filter(base -> base.isReadyForMining() && base.numScvsFromHardSaturated() > 0)
//                .min(Comparator.comparing(base -> UnitUtils.getDistance(scv.unit(), base.getCcPos())))
//                .stream()
//                .flatMap(base -> base.getMineralPatches().stream())
//                .filter(mineral -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(mineral.getNode().getType()) &&
//                        mineral.getScvs().size() < 3)
//                .max(Comparator.comparing(mineralPatch -> mineralPatch.getNode().getMineralContents().orElse(0)))
//                .orElse(null);
//        if (smallMineralToMine != null) {
//            smallMineralToMine.getScvs().add(scv);
//            return true;
//        }

        //distance mine
        return distanceMineScv(scv); //TODO: change to 'return false;' when better distance mining method is used
    }

    public static boolean distanceMineScv(UnitInPool scv) {
        Base nextBase = GameCache.baseList.stream()
                .filter(base -> !base.isReadyForMining() && !base.isEnemyBase && !base.isDryedUp)
                .findFirst()
                .orElse(null);
        if (nextBase != null) {
            MineralPatch nextBaseMineral = nextBase.getMineralPatches().stream()
                    .filter(mineralPatch -> mineralPatch.getScvs().size() < 2)
                    .max(Comparator.comparing(mineralPatch -> mineralPatch.getNode().getMineralContents().orElse(0)))
                    //TODO: snapshots get ignored so replace above??
                    .orElse(null);
            if (nextBaseMineral != null) {
                nextBaseMineral.getScvs().add(scv);
                return true;
            }
        }
        return false;
    }

    public static void releaseScv(Unit scv) {
        GameCache.baseList.stream()
                .flatMap(base -> base.getMineralPatches().stream())
                .forEach(mineralPatch -> mineralPatch.getScvs().removeIf(u -> scv.getTag().equals(u.getTag())));
        GameCache.baseList.stream()
                .flatMap(base -> base.getGases().stream())
                .forEach(gas -> gas.getScvs().removeIf(u -> scv.getTag().equals(u.getTag())));
    }

    public static boolean isMining(UnitInPool scv) {
        return GameCache.baseList.stream()
                    .flatMap(base -> base.getMineralPatches().stream())
                    .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                    .anyMatch(u -> scv.getTag().equals(u.getTag())) ||
                GameCache.baseList.stream()
                        .flatMap(base -> base.getGases().stream())
                        .flatMap(gas -> gas.getScvs().stream())
                        .anyMatch(u -> scv.getTag().equals(u.getTag()));
    }

    public void scvReport() {
        mineralPatches.forEach(mineralPatch -> {
            System.out.print("\nMineral " + mineralPatch.getNode().getTag().getValue() + ": ");
            mineralPatch.getScvs().forEach(scv -> System.out.print(scv.getTag() + " "));
        });
    }

    public static List<Gas> getMyGases() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(gas -> gas.getRefinery() != null &&
                        gas.getRefinery().getBuildProgress() == 1 &&
                        gas.getRefinery().getVespeneContents().orElse(0) > 0)
                .collect(Collectors.toList());
    }

    public static List<MineralPatch> getMyMinerals() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .collect(Collectors.toList());
    }

    public static List<MineralPatch> getMyUnderSaturatedMinerals() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() < 2)
                .collect(Collectors.toList());
    }

    public static MineralPatch getClosestUnderSaturatedMineral(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() < 2)
                .min(Comparator.comparing(mineralPatch -> mineralPatch.getByNode().distance(pos)))
                .orElse(null);
    }

    public static Gas getClosestUnmaxedGas(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(gas -> gas.getRefinery() != null && gas.getRefinery().getBuildProgress() == 1f && gas.getScvs().size() < 3)
                .min(Comparator.comparing(gas -> gas.getByNode().distance(pos)))
                .orElse(null);
    }
    public static Gas getClosestUnderSaturatedGas(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(gas -> gas.getScvs().size() < WorkerManager.numScvsPerGas)
                .min(Comparator.comparing(gas -> gas.getByNode().distance(pos)))
                .orElse(null);
    }

    public static UnitInPool releaseClosestMineralScv(Point2d pos) {
        UnitInPool closestMineralScv = GameCache.baseList.stream()
                .flatMap(base -> base.mineralPatches.stream())
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), pos)))
                .orElse(null);
        if (closestMineralScv != null) {
            Base.releaseScv(closestMineralScv.unit());
        }
        return closestMineralScv;
    }

    public static List<MineralPatch> getDistanceMinedMineralPatches() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isEnemyBase && !base.isReadyForMining())
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> !mineralPatch.getScvs().isEmpty())
                .collect(Collectors.toList());
    }

    public static int numDistanceMiningScvs() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isEnemyBase && !base.isReadyForMining())
                .flatMap(base -> base.mineralPatches.stream())
                .mapToInt(base -> base.getScvs().size())
                .sum();
    }

    public static int numScvsFromMaxSaturation() {
        return numMineralScvsFromMaxSaturation() + numGasScvsFromMaxSaturation();
    }

    public static int numMineralScvsFromMaxSaturation() {
        return GameCache.baseList.stream()
                        .filter(Base::isReadyForMining)
                        .flatMap(base -> base.mineralPatches.stream())
                        .mapToInt(mineralPatch -> 2-mineralPatch.getScvs().size())
                        .sum();
    }

    public static int numGasScvsFromMaxSaturation() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .mapToInt(gas -> 3-gas.getScvs().size())
                .sum();
    }

    public static boolean isABasePos(Point2d pos) {
        return GameCache.baseList.stream()
                .anyMatch(base -> base.getCcPos().distance(pos) < 1);
    }
}