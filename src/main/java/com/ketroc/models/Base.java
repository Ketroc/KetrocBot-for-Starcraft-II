package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Octagon;
import com.ketroc.geometry.Position;
import com.ketroc.geometry.Rectangle;
import com.ketroc.managers.ArmyManager;
import com.ketroc.managers.WorkerManager;
import com.ketroc.micro.*;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Base {
    public long lastScoutedFrame;
    public boolean isEnemyBase;
    public boolean isDriedUp;
    private boolean isPocketBase;
    private Point2d ccPos;
    private UnitInPool cc;
    private List<Gas> gases = new ArrayList<>();
    private List<MineralPatch> mineralPatches = new ArrayList<>();
    private Unit rallyNode; //mineral node this cc is rallied to
    private Point2d resourceMidPoint = null;
    private List<DefenseUnitPositions> inMineralLinePositions = new ArrayList<>();
    private List<DefenseUnitPositions> inFrontPositions = new ArrayList<>();
    private List<DefenseUnitPositions> liberatorPositions = new ArrayList<>();
    private static float libDistanceFromCC = -1;
    private boolean continueUnsieging;
    private boolean onMyBaseDeath;
    private boolean onBaseAcquired;
    public long prevMuleSpamFrame;
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
            onBaseAcquired = true;
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

    public List<DefenseUnitPositions> getInMineralLinePositions() {
        return inMineralLinePositions;
    }

    public List<DefenseUnitPositions> getInFrontPositions() {
        return inFrontPositions;
    }

    public boolean requiresDefense() {
        return isMyBase() && !isMyMainBase() && !isDriedUp && !isPocketBase();
    }

    public void setIsDriedUp() {
        //once dried up, it will remain dried up
        if (isDriedUp) {
            return;
        }

        //check if gas and minerals are all empty TODO: another method to check if it's worth expanding to with the remaining resources
        isDriedUp = getMineralPatchUnits().isEmpty() && gases.stream().allMatch(Gas::isDriedUp);
        if (isDriedUp) {
            continueUnsieging = true;
        }
    }

    public List<DefenseUnitPositions> getLiberatorPositions() {
        if (liberatorPositions.isEmpty()) {
            if (resourceMidPoint != null) {
                Point2d midPoint = isMyNatBase() ?
                        Position.towards(ccPos, PosConstants.BUNKER_NATURAL, -getLibDistanceFromCC()) :
                        Position.towards(ccPos, resourceMidPoint, getLibDistanceFromCC());
                liberatorPositions.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, 32.5), null));
                liberatorPositions.add(new DefenseUnitPositions(Position.rotate(midPoint, ccPos, -32.5), null));
            }
        }
        return liberatorPositions;
    }

    public void setLiberatorPositions(List<DefenseUnitPositions> liberatorPositions) {
        this.liberatorPositions = liberatorPositions;
    }

    public int numMineralScvs() {
        return numMineralScvs(scv -> true);
    }

    public int numScvRepairers() {
        return (int) UnitMicroList.getUnitSubList(ScvRepairBay.class).stream()
                .filter(scvRepairBay -> UnitUtils.getDistance(scvRepairBay.unit.unit(), ccPos) < 9)
                .count();
    }

    public int numMineralScvs(Predicate<UnitInPool> scvFilter) {
        return (int) mineralPatches.stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .filter(scvFilter)
                .count();
    }

    public int numGasScvs() {
        return numGasScvs(scv -> true);
    }

    public int numGasScvs(Predicate<UnitInPool> scvFilter) {
        return (int) gases.stream()
                .flatMap(gas -> gas.getScvs().stream())
                .filter(scvFilter)
                .count();
    }

    public int numAvailableGasScvs() {
        return numAvailableGasScvs(scv -> true);
    }

    public int numAvailableGasScvs(Predicate<UnitInPool> scvFilter) {
        return (int) gases.stream()
                .flatMap(gas -> gas.getAvailableScvs().stream())
                .filter(scvFilter)
                .count();
    }

    public List<UnitInPool> getMineralScvs() {
        return mineralPatches.stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .collect(Collectors.toList());
    }

    public List<UnitInPool> getAvailableMineralScvs() {
        return mineralPatches.stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .filter(u -> !UnitUtils.isCarryingResources(u.unit()))
                .collect(Collectors.toList());
    }

    public List<UnitInPool> getGasScvs() {
        return gases.stream()
                .flatMap(gas -> gas.getScvs().stream())
                .collect(Collectors.toList());
    }

    public boolean isPocketBase() {
        return isPocketBase;
    }

    public void setPocketBase(boolean pocketBase) {
        isPocketBase = pocketBase;
    }

    // ============ METHODS ==============

    public void onStep() {
        setIsDriedUp();
        if (onMyBaseDeath) {
            onMyBaseDeath();
            onMyBaseDeath = false;

        }
        if (onBaseAcquired && isReadyForMining()) {
            onBaseAcquired();
            onBaseAcquired = false;
        }
        if (continueUnsieging) {
            if (!InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, ccPos)) {
                unsiegeBase();
                continueUnsieging = false;
            }
        }
        if (isMyBase() && !isMyMainBase()) {
            Set<Unit> repairBayTargets = getRepairBayTargets();
            if (!repairBayTargets.isEmpty()) {
                Point2d repairBayPos = inFrontPos();
                int numRepairScvs = (int) UnitMicroList.getUnitSubList(ScvRepairBay.class).stream()
                        .filter(repairBayScv -> repairBayScv.repairBayPos.equals(repairBayPos))
                        .count();
                List<UnitInPool> availableMineralScvs = getAvailableMineralScvs();
                while (numRepairScvs < 4 && !availableMineralScvs.isEmpty()) {
                    UnitInPool newRepairScv = availableMineralScvs.remove(0);
                    UnitMicroList.add(new ScvRepairBay(newRepairScv, repairBayPos));
                    numRepairScvs++;
                }
            }
        }
    }

    public void onStepEnd() {
        for (MineralPatch mineralPatch : mineralPatches) {
            mineralPatch.getScvs().forEach(scv -> {
                //detour if scv can be 2-shot
                if (shouldFlee(scv)) {
//                    DebugHelper.drawBox(mineralPatch.getByNodePos(), Color.RED, 0.2f);
//                    DebugHelper.drawBox(mineralPatch.getByCCPos(), Color.RED, 0.2f);
                    if (mineralPatch.isSpeedMineScv(scv.getTag())) {
                        mineralPatch.endMuleSpeedMine();
                    }
                    new BasicUnitMicro(scv, mineralPatch.getNodePos(), MicroPriority.SURVIVAL).onStep();
                    return;
                }

                // scv is busy helping speed-mine the mule
                if (mineralPatch.isSpeedMineScv(scv.getTag())) {
                    return;
                }

                //don't give scv command if it is getting auto-pushed out of the way of a new structure
                if (scv.unit().getOrders().size() >= 3) {
                    return;
                }

                //mine normally if 3 scvs (exclude distance mining)
                if (cc != null &&
                        mineralPatch.getScvs().size() >= 3 &&
                        mineralPatch.getScvs().stream().allMatch(mineralScv -> UnitUtils.getDistance(mineralScv.unit(), mineralPatch.getByNodePos()) < 5)) {
                    Optional<ActionIssued> curOrder = ActionIssued.getCurOrder(scv);
                    if (curOrder.isEmpty() ||
                            (curOrder.get().ability == Abilities.HARVEST_GATHER &&
                                    !mineralPatch.getNode().getTag().equals(curOrder.get().targetTag))) {
                        ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_GATHER, mineralPatch.getNode(), false);
                    }
                    return;
                }

                if (UnitUtils.isCarryingResources(scv.unit())) { //return micro
                    if (isReadyForMining()) {
                        mineralPatch.returnMicro(scv.unit());
                    }
                    else {
                        mineralPatch.distanceReturnMicro(scv.unit());
                    }
                }
                else { //harvest micro
                    if (isReadyForMining()) {
                        mineralPatch.harvestMicro(scv.unit());
                    }
                    else {
                        mineralPatch.distanceHarvestMicro(scv.unit());
                    }
                }
            });
            mineralPatch.onStep();
        }

        for (Gas gas : gases) {
            gas.getScvs().forEach(scv -> {
                //detour if scv can be 2-shot
                if (shouldFlee(scv)) {
                    //DebugHelper.drawBox(gas.getByNodePos(), Color.RED, 0.2f);
                    //DebugHelper.drawBox(gas.getByCCPos(), Color.RED, 0.2f);
                    new BasicUnitMicro(scv, gas.getNodePos(), MicroPriority.SURVIVAL).onStep();
                }

                //fix scv if mining wrong node
                else if (ActionIssued.getCurOrder(scv).stream()
                        .anyMatch(order -> order.ability == Abilities.HARVEST_GATHER &&
                                !gas.getRefinery().getTag().equals(order.targetTag))) {
                    ActionHelper.unitCommand(scv.unit(), Abilities.HARVEST_GATHER, gas.getRefinery(), false);
                }

                //mine normally if 3 scvs
                else if (gas.getScvs().size() >= 3 || gas.getRefinery().getType() == Units.TERRAN_REFINERY_RICH) {
                    if (UnitUtils.getOrder(scv.unit()) == null) {
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
        }
    }


    private boolean shouldFlee(UnitInPool scv) {
        //flee to any danger if distance mining
        if (!isReadyForMining() && InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundPlusBuffer, scv.unit().getPosition().toPoint2d())) {
            return true;
        }
        else if (UnitUtils.myUnitWithin2ShotThreat(scv.unit())) {
            List<UnitInPool> enemiesInAttackRange = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                    UnitUtils.getAttackRange(enemy.unit(), Weapon.TargetType.GROUND) + Strategy.KITING_BUFFER >
                            UnitUtils.getDistance(scv.unit(), enemy.unit()));

            //don't flee if near PF when enemy ground in range
            boolean isEnemyGroundAttackersInRange = enemiesInAttackRange.stream().anyMatch(enemy -> !enemy.unit().getFlying().orElse(true));
            boolean isBasePF = getCc() != null && getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS;
            boolean hasShadeThreat = AdeptShadeTracker.activeShades.stream()
                    .anyMatch(shade -> shade.doConsiderThreat() && UnitUtils.getDistance(shade.getShadeUip().unit(), scv.unit()) < 7);
            if (isBasePF && (isEnemyGroundAttackersInRange || hasShadeThreat)) {
                return false;
            }

            //don't flee if near missile turret when enemy air in range
            boolean enemyAirAttackersInRange = enemiesInAttackRange.stream().anyMatch(enemy -> enemy.unit().getFlying().orElse(false));
            boolean baseHasTurret = getInMineralLinePositions().stream().anyMatch(turret -> turret.getUnit() != null && turret.getUnit().unit().getBuildProgress() == 1);
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

    public boolean isUnderAttack() {
        return isUnderAttack(15);
    }

    public boolean isUnderAttack(float range) {
        return isMyBase() &&
                ArmyManager.attackUnit != null &&
                UnitUtils.getDistance(ArmyManager.attackUnit, ccPos) < range;
    }

    public float enemySupplyNearBase(float rangeCheck) {
        return (float)Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.getDistance(enemy.unit(), ccPos) < rangeCheck)
                        .stream()
                        .mapToDouble(enemy -> UnitUtils.getSupplyCost(enemy.unit().getType()))
                        .sum();
    }

    public Set<Unit> getRepairBayTargets() {
        return UnitUtils.getRepairBayTargets(inFrontPos());
    }

    public boolean isMyBase() {
        return cc != null && cc.unit().getAlliance() == Alliance.SELF;
    }

    public boolean isUntakenBase() {
        return cc == null &&
                !isEnemyBase &&
                StructureScv.scvBuildingList.stream().noneMatch(scv -> scv.structurePos.distance(ccPos) < 1);
    }

    public boolean isReachable() {
        boolean unreachableBase = Bot.OBS.getVisibility(ccPos) != Visibility.VISIBLE &&
                Bot.QUERY.pathingDistance(GameCache.baseList.get(1).resourceMidPoint, ccPos) == 0;
        if (unreachableBase) {
            isEnemyBase = true;
        }
        return !unreachableBase;
    }

    public boolean isReachable(Unit scv) {
        boolean unreachableBase = Bot.QUERY.pathingDistance(scv, ccPos) == 0;
        if (unreachableBase) {
            isEnemyBase = true;
        }
        return unreachableBase;
    }

    public int numActiveRefineries() {
        return (int) gases.stream().filter(gas -> gas.getRefinery() != null && gas.getRefinery().getVespeneContents().orElse(0) > 80).count();
    }

    public int numActiveMineralPatches() {
        return (int) mineralPatches.stream()
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
        Point2d p = Point2d.of(0, 0);
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
            } else {
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

    public boolean isMyNatBase() {
        return this.equals(GameCache.baseList.get(1));
    }

    public void unsiegeBase() {
        //unsiege liberators and tanks
        freeUpLiberators();
        freeUpTanks();
    }

    public void onBaseAcquired() {
        //free up all the extra distance miners who were on this base
        mineralPatches.forEach(mineral -> {
            for (int i = 2; i < mineral.getScvs().size(); i++) {
                UnitUtils.returnAndStopScv(mineral.getScvs().get(i));
                mineral.getScvs().remove(i--);
            }
        });

        //set rally
        Unit rallyNode = getFullestMineralPatch();
        if (rallyNode != null) {
            ActionHelper.unitCommand(cc.unit(), Abilities.RALLY_COMMAND_CENTER, rallyNode, false);
        }
    }

    public void onMyBaseDeath() {
        mineralPatches.forEach(mineralPatch -> mineralPatch.getScvs().forEach(scv -> UnitUtils.returnAndStopScv(scv)));
        mineralPatches.forEach(mineralPatch -> mineralPatch.getScvs().clear());
        gases.forEach(gas -> gas.getScvs().forEach(scv -> UnitUtils.returnAndStopScv(scv)));
        gases.forEach(gas -> gas.getScvs().clear());
        //TODO: delayed action for scv inside the refinery

        //cancel turrets and refineries at this base
        for (int i = 0; i < StructureScv.scvBuildingList.size(); i++) {
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
        for (DefenseUnitPositions libPos : getLiberatorPositions()) {
            if (libPos.getUnit() != null) {
                Unit baseLib = libPos.getUnit().unit();
                if (baseLib.getType() == Units.TERRAN_LIBERATOR_AG) {
                    ActionHelper.unitCommand(baseLib, Abilities.MORPH_LIBERATOR_AA_MODE, false);
                } else {
                    ActionHelper.unitCommand(baseLib, Abilities.STOP, false);
                }
                libPos.setUnit(null, this);
            }
        }
    }

    private void freeUpTanks() {
        for (DefenseUnitPositions tankPos : getInMineralLinePositions()) {
            if (tankPos.getUnit() != null) {
                UnitMicroList.remove(tankPos.getUnit().getTag());
                tankPos.setUnit(null, this);
            }
        }
    }

    public void visualizeMiningLayout() {
        getMineralPatches().forEach(m -> m.visualMiningLayout());
        getGases().forEach(g -> g.visualMiningLayout());
        getInMineralLinePositions().forEach(turrets -> new Rectangle(turrets.getPos(), 1.4f).draw(Color.YELLOW));
        new Octagon(ccPos).draw(Color.YELLOW);
    }

    public boolean isReadyForMining() {
        return isMyBase() && isComplete();
    }

    public List<UnitInPool> getAndReleaseAvailableScvs(int numScvs) {
        List<UnitInPool> baseScvs = new ArrayList<>();
        for (int i = 0; i < numScvs; i++) {
            UnitInPool scv = getAndReleaseScv();
            if (scv == null) {
                return baseScvs;
            }
            baseScvs.add(scv);
        }
        return baseScvs;
    }

    public UnitInPool getAndReleaseScv() {
        UnitInPool scv = getScv();
        if (scv != null) {
            Base.releaseScv(scv.unit());
        }
        return scv;
    }

    public UnitInPool getScv() {
        //1. try get oversaturated mineral scv
        //2. try get oversaturated gas scv
        //3. try get scv from smallest patch
        return getScvFromOversaturatedMineral().orElse(
                getScvFromOversaturatedGas().orElse(
                        getScvFromSmallestPatch().orElse(null)));
    }

    public Optional<UnitInPool> getDistanceMiningScv(Point2d targetPos) {
        return getDistanceMiningScv(targetPos, scv -> true);
    }

    public Optional<UnitInPool> getDistanceMiningScv(Point2d targetPos, Predicate<UnitInPool> scvFilter) {
        if (!isUntakenBase()) {
            return Optional.empty();
        }

        return getMineralPatches().stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .filter(scvFilter)
                .min(Comparator.comparing(scv -> UnitUtils.getDistance(scv.unit(), targetPos) +
                        (UnitUtils.isCarryingResources(scv.unit()) ? 100 : 0)));
    }

    public boolean hasDistanceMiningScv() {
        return hasDistanceMiningScv(scv -> true);
    }

    public boolean hasDistanceMiningScv(Predicate<UnitInPool> scvFilter) {
        return isUntakenBase() &&
                getMineralPatches().stream().anyMatch(mineralPatch -> !mineralPatch.getScvs().isEmpty() &&
                        mineralPatch.getScvs().stream().anyMatch(scvFilter));
    }

    public Optional<UnitInPool> getScvFromOversaturatedMineral() {
        return getScvFromOversaturatedMineral(scv -> true);
    }

    public Optional<UnitInPool> getScvFromOversaturatedMineral(Predicate<UnitInPool> scvFilter) {
        return getMineralPatches().stream()
                .filter(mineralPatch -> mineralPatch.getScvs().size() > 2)
                .min(Comparator.comparing(mineralPatch -> mineralPatch.getNode().getMineralContents().orElse(0)))
                .stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .filter(scvFilter)
                .min(Comparator.comparing(scv -> UnitUtils.isCarryingResources(scv.unit()) ? 10 : 0));
    }

    public boolean hasOverSaturatedMineral() {
        return hasOverSaturatedMineral(scv -> true);
    }

    public boolean hasOverSaturatedMineral(Predicate<UnitInPool> scvFilter) {
        return getMineralPatches().stream().anyMatch(mineralPatch -> mineralPatch.getScvs().size() > 2 &&
                mineralPatch.getScvs().stream().anyMatch(scvFilter));
    }

    public Optional<UnitInPool> getScvFromOversaturatedGas() {
        return getScvFromOversaturatedGas(scv -> true);
    }

    public Optional<UnitInPool> getScvFromOversaturatedGas(Predicate<UnitInPool> scvFilter) {
        return getGases().stream()
                .filter(gas -> gas.getRefinery() != null &&
                        gas.getScvs().size() >
                                (gas.getRefinery().getType() == Units.TERRAN_REFINERY_RICH ? 3 : WorkerManager.numScvsPerGas))
                .min(Comparator.comparing(gas -> gas.getNode().getVespeneContents().orElse(0)))
                .map(gas -> gas.getAndReleaseScv(scvFilter));
    }

    public Optional<UnitInPool> getScvFromGas() {
        return getScvFromGas(scv -> true);
    }

    public Optional<UnitInPool> getScvFromGas(Predicate<UnitInPool> scvFilter) {
        return getGases().stream()
                .filter(gas -> gas.getRefinery() != null && !gas.getAvailableScvs().isEmpty())
                .min(Comparator.comparing(gas -> gas.getNode().getVespeneContents().orElse(0)))
                .map(gas -> gas.getAndReleaseScv(scvFilter));
    }

    public boolean hasOverSaturatedGas() {
        return hasOverSaturatedGas(scv -> true);
    }

    public boolean hasOverSaturatedGas(Predicate<UnitInPool> scvFilter) {
        return getGases().stream().anyMatch(gas -> gas.getRefinery() != null &&
                gas.getScvs().size() >
                        (gas.getRefinery().getType() == Units.TERRAN_REFINERY_RICH ? 3 : WorkerManager.numScvsPerGas) &&
                gas.getScvs().stream().anyMatch(scvFilter));
    }

    public Optional<UnitInPool> getScvFromSmallestPatch() {
        return getScvFromSmallestPatch(scv -> true);
    }

    public Optional<UnitInPool> getScvFromSmallestPatch(Predicate<UnitInPool> scvFilter) {
        return getMineralPatches().stream()
                .filter(mineralPatch -> !mineralPatch.getScvs().isEmpty() && !mineralPatch.hasNewMule())
                .min(Comparator.comparing(mineralPatch -> mineralPatch.getNode().getMineralContents().orElse(0)))
                .stream()
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .filter(scvFilter)
                .min(Comparator.comparing(scv -> UnitUtils.isCarryingResources(scv.unit()) ? 10 : 0));
    }

    public void setTurretInMineralLinePositions() {
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
        } else {
            addTurretPosForEach1GasSide();
        }
    }

    public void setTurretInFrontPositions() {
        //no front turrets in main base
        if (isMyMainBase()) {
            return;
        }

        //add 2 positions on all 4 sides of the CC
        List<Point2d> inFrontPosList = new ArrayList<>();
        inFrontPosList.add(ccPos.add(3.5f, 1.5f));
        inFrontPosList.add(ccPos.add(3.5f, -0.5f));

        inFrontPosList.add(ccPos.add(-1.5f, 3.5f));
        inFrontPosList.add(ccPos.add(0.5f, 3.5f));

        inFrontPosList.add(ccPos.add(-3.5f, -1.5f));
        inFrontPosList.add(ccPos.add(-3.5f, 0.5f));

        inFrontPosList.add(ccPos.add(1.5f, -3.5f));
        inFrontPosList.add(ccPos.add(-0.5f, -3.5f));

        //sort by furthest from resourceMidPoint
        Point2d enemySpawnPos = PosConstants.baseLocations.get(PosConstants.baseLocations.size() - 1);
        inFrontPosList = inFrontPosList.stream()
                .filter(p -> p.distance(resourceMidPoint) > 5f)
                .filter(p -> inMineralLinePositions.stream().noneMatch(defPos -> defPos.getPos().distance(p) < 2))
                .filter(p -> gases.stream().noneMatch(gas -> gas.getNodePos().distance(p) < 4.5f) ||
                        inMineralLinePositions.stream().noneMatch(defPos -> defPos.getPos().distance(p) < 2.5f))
                .sorted(Comparator.comparing(p -> p.distance(enemySpawnPos) - p.distance(resourceMidPoint)))
                .collect(Collectors.toList());

        //furthest pos
        Point2d pos1 = inFrontPosList.remove(0);
        Point2d finalPos = pos1;
        inFrontPosList.sort(Comparator.comparing(p -> p.distance(enemySpawnPos) - p.distance(resourceMidPoint) + (p.distance(finalPos) < 2.5f ? 0.5f : 0)));
        Point2d pos2 = inFrontPosList.remove(0);
        for (Point2d p : inFrontPosList) {
            if (inMineralLinePositions.stream().anyMatch(defPos -> defPos.getPos().distance(p) < 2.5f)) {
                continue;
            }
            if (p.distance(pos1) < 2.5f && p.distance(pos2) > pos1.distance(pos2)) {
                pos1 = p;
            } else if (p.distance(pos2) < 2.5f && p.distance(pos1) > pos2.distance(pos1)) {
                pos2 = p;
            }
        }

        inFrontPositions.add(new DefenseUnitPositions(pos1, null));
        inFrontPositions.add(new DefenseUnitPositions(pos2, null));
    }

    private void addTurretPosFor0GasSide() {
        //get gas angle
        float gasAngle = Position.getAngle(ccPos, getGases().get(0).getNodePos());

        //get mineralpatch with biggest difference from gas angle TODO: adjust this to have the least interruption of speed mining
        MineralPatch farMineralPatch = getMineralPatches().stream()
                .max(Comparator.comparing(mineral -> Position.getAngleDifference(gasAngle, Position.getAngle(ccPos, mineral.getNodePos()))))
                .get();

        Point2d farPatchMiningMidpoint = Position.towards(farMineralPatch.getByNodePos(), farMineralPatch.getByCCPos(), 2f);
        Point2d turretPos = Position.toWholePoint(
                Position.rotateTowards(farPatchMiningMidpoint, ccPos, getResourceMidPoint(), -25));
        turretPos = Position.moveClear(turretPos, ccPos, 3.5f);
        if (!PlacementMap.canFit2x2(turretPos)) {
            PlacementMap.canFit2x2(turretPos);
            turretPos = farPatchMiningMidpoint;
        }
        inMineralLinePositions.add(new DefenseUnitPositions(turretPos, null));

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

        //hardcoded exception (calculated pos traps scvs)
        if (PosConstants.MAP.contains("Oxide")) {
            if (turretPos.distance(Point2d.of(65, 76)) < 1) {
                turretPos = turretPos.add(0, 1);
            } else if (turretPos.distance(Point2d.of(127, 128)) < 1) {
                turretPos = turretPos.sub(0, 1);
            }
        }

        //DebugHelper.drawBox(turretPos, Color.GREEN, 1f);
        inMineralLinePositions.add(new DefenseUnitPositions(turretPos, null));
    }

    public Point2d inFrontPos() {
        return Position.towards(ccPos, getResourceMidPoint(), -5.5f);
    }

    private void addTurretPosForEach1GasSide() {
        for (Gas gas : getGases()) {
            //closest mineral to gas node
            MineralPatch closestMineral = getMineralPatches().stream()
                    .min(Comparator.comparing(mineral -> UnitUtils.getDistance(mineral.getNode(), gas.getNodePos())))
                    .get();

            Point2d gasMiningPos = Position.towards(gas.getByNodePos(), gas.getByCCPos(), 0.3f);
            Point2d mineralMiningPos = Position.towards(closestMineral.getByNodePos(), closestMineral.getByCCPos(), 0.3f);
            Point2d turretPos = Position.toWholePoint(Position.midPoint(gasMiningPos, mineralMiningPos));
            inMineralLinePositions.add(new DefenseUnitPositions(turretPos, null));
        }
    }

    public boolean isNatBaseAndHasBunker() {
        return ccPos.equals(GameCache.baseList.get(1).ccPos) && !UnitUtils.getNatBunkers().isEmpty();
    }


    // ======= STATIC METHODS ========

    public static void onGameStart() {
        GameCache.baseList.forEach(base -> {
            base.setTurretInMineralLinePositions();
            base.getInMineralLinePositions().stream().forEach(tur -> DebugHelper.drawBox(tur.getPos(), Color.YELLOW, 1));
            base.setTurretInFrontPositions();
            base.getInFrontPositions().stream().forEach(tur -> DebugHelper.drawBox(tur.getPos(), Color.PURPLE, 1));
        });
    }

    public static float getLibDistanceFromCC() {
        if (libDistanceFromCC == -1 || PosConstants.opponentRace == Race.RANDOM) {
            libDistanceFromCC = (PosConstants.opponentRace == Race.PROTOSS) ? 1f : 2.5f;
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

    public static int scvsReqForMyBases() {
        return (totalActiveRefineriesForMyBases() * 3) + (totalMineralPatchesForMyBases() * 2);
    }

    public static int numMyBases() {
        return (int) GameCache.baseList.stream().filter(base -> base.isMyBase()).count();
    }

    public static int numBasesToDefend() {
        return (int) GameCache.baseList.stream().filter(Base::requiresDefense).count();
    }

    public static int numEnemyBases() {
        return (int) GameCache.baseList.stream().filter(base -> base.isEnemyBase).count();
    }

    public static int numAvailableBases() {
        return (int) GameCache.baseList.stream()
                .filter(base -> base.isUntakenBase() && !base.isDriedUp)
                .count();
    }

    public static Point2d getNextAvailableBasePos() {
        return GameCache.baseList.stream()
                .filter(base -> base.isUntakenBase() && !base.isDriedUp)
                .findFirst()
                .map(Base::getCcPos)
                .orElse(null);
    }

    public static Base getNextBase() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isDriedUp && !base.isMyBase())
                .findFirst()
                .orElse(null);
    }
    public static Point2d getNextBasePos() {
        Base nextBase = getNextBase();
        return nextBase == null ? null : nextBase.getCcPos();
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

    public static boolean distanceMineScv(UnitInPool scv) {
        Base nextBase = GameCache.baseList.stream()
                .filter(base -> !base.isReadyForMining() && !base.isEnemyBase && !base.isDriedUp &&
                        base.numMineralScvs() < base.mineralPatches.size() * 5)
                .findFirst()
                .orElse(null);
        if (nextBase != null) {
            MineralPatch nextBaseMineral = nextBase.getMineralPatches().stream()
                    .filter(mineralPatch -> mineralPatch.getScvs().size() < 5)
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
        return isMiningMinerals(scv) || isMiningGas(scv);
    }

    public static boolean isMiningMinerals(UnitInPool scv) {
        return GameCache.baseList.stream()
                .flatMap(base -> base.getMineralPatches().stream())
                .flatMap(mineralPatch -> mineralPatch.getScvs().stream())
                .anyMatch(u -> scv.getTag().equals(u.getTag()));
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return GameCache.baseList.stream()
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

    public static MineralPatch getBestUnderSaturatedMineral(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() < 2)
                .min(Comparator.comparing(mineralPatch ->
                        mineralPatch.getByNodePos().distance(pos) -
                        (mineralPatch.isClosePatch() ? 500 : 0) -
                        mineralPatch.getNode().getMineralContents().orElse(500) / 2)
                )
                .orElse(null);
    }

    public static MineralPatch getClosestSaturatedSmallMineral(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() == 2 &&
                        UnitUtils.MINERAL_NODE_TYPE_SMALL.contains(mineralPatch.getNode().getType()))
                .min(Comparator.comparing(mineralPatch -> mineralPatch.getByNodePos().distance(pos)))
                .orElse(null);
    }

    public static Gas getClosestUnmaxedGas(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(gas -> gas.getRefinery() != null &&
                        gas.getRefinery().getBuildProgress() == 1f &&
                        gas.getRefinery().getVespeneContents().orElse(0) > 0 &&
                        gas.getScvs().size() < 3)
                .min(Comparator.comparing(gas -> gas.getByNodePos().distance(pos) + (gas.getScvs().size() >= 2 ? 1000 : 0)))
                .orElse(null);
    }

    public static Gas getClosestUnderSaturatedGas(Point2d pos) {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(gas -> gas.getRefinery() != null &&
                        gas.getRefinery().getBuildProgress() == 1f &&
                        gas.getRefinery().getVespeneContents().orElse(0) > 0 &&
                        gas.getScvs().size() < WorkerManager.numScvsPerGas)
                .min(Comparator.comparing(gas -> gas.getByNodePos().distance(pos)))
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

    public static List<MineralPatch> getOversaturatedMineralPatches() {
        return GameCache.baseList.stream()
                .filter(base -> base.isReadyForMining())
                .flatMap(base -> base.mineralPatches.stream())
                .filter(mineralPatch -> mineralPatch.getScvs().size() > 2)
                .collect(Collectors.toList());
    }

    public static int numDistanceMiningScvs() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isEnemyBase && !base.isReadyForMining())
                .flatMap(base -> base.mineralPatches.stream())
                .mapToInt(base -> base.getScvs().size())
                .sum();
    }

    public static int numMineralScvsFromSoftSaturation() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.mineralPatches.stream())
                .mapToInt(mineralPatch -> Math.max(0, 2 - mineralPatch.getScvs().size()))
                .sum();
    }

    public static int numGasScvsFromMaxSaturation() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .mapToInt(gas -> 3 - gas.getScvs().size())
                .sum();
    }

    public static int numGasScvsFromDesiredSaturation() {
        return GameCache.baseList.stream()
                .filter(Base::isReadyForMining)
                .flatMap(base -> base.gases.stream())
                .filter(Gas::isReadyForMining)
                .mapToInt(gas -> Math.max(0, WorkerManager.numScvsPerGas - gas.getScvs().size()))
                .sum();
    }

    public static boolean isABasePos(Point2d pos) {
        return GameCache.baseList.stream()
                .anyMatch(base -> base.getCcPos().distance(pos) < 1);
    }

    public static boolean isPocketBase(Point2d pos) {
        return GameCache.baseList.stream()
                .anyMatch(base -> base.isPocketBase() && base.getCcPos().distance(pos) < 1);
    }

    public static List<Base> getMyBases() {
        return GameCache.baseList.stream().filter(base -> base.isMyBase()).collect(Collectors.toList());
    }

    public static boolean nearOneOfMyBases(Unit unit, float distance) {
        return GameCache.baseList.stream()
                .anyMatch(base -> base.isMyBase() && UnitUtils.getDistance(unit, base.ccPos) < distance);
    }


    public static Optional<Base> getAvailablePocketBase() {
        return GameCache.baseList.stream()
                .filter(Base::isPocketBase)
                .filter(base -> base.getCcPos().distance(PosConstants.mainBaseMidPos) < 50)
                .filter(base -> !base.isMyBase())
                .findFirst();
    }
}