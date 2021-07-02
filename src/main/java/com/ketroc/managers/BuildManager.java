package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.Purchase;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.purchases.PurchaseStructureMorph;
import com.ketroc.purchases.PurchaseUpgrade;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.CannonRushDefense;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BuildManager {
    public static int MIN_BANSHEES = 1;
    public static final List<Abilities> BUILD_ACTIONS = Arrays.asList(
            Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER, Abilities.BUILD_STARPORT, Abilities.BUILD_SUPPLY_DEPOT,
            Abilities.BUILD_ARMORY, Abilities.BUILD_BARRACKS, Abilities.BUILD_BUNKER, Abilities.BUILD_ENGINEERING_BAY,
            Abilities.BUILD_FACTORY, Abilities.BUILD_FUSION_CORE, Abilities.BUILD_GHOST_ACADEMY, Abilities.BUILD_MISSILE_TURRET,
            Abilities.BUILD_SENSOR_TOWER
    );
    private static boolean isMuleSpamming;
    public static List<Abilities> openingStarportUnits = new ArrayList<>();

    public static void onStep() {
        //build armories etc
        build2ndLayerOfTech();

        //cancel structure logic
        cancelStructureLogic();

        //build depot logic
        buildDepotLogic();

        //build missile turrets logic
        buildTurretLogic();

        //keep CCs active (make scvs, morph ccs, call mules)
        ccActivityLogic();

        //spam mules on opponent
        spamMulesOnEnemyBase();

        //turn low health expansion command centers into macro OCs
        saveDyingCCs();

        //prioritize factory production when doing tank viking strat, and viking/raven count is fine
        if (Strategy.gamePlan == GamePlan.TANK_VIKING || Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING) {
            int numTanks = UnitMicroList.getUnitSubList(TankOffense.class).size();
            boolean prioritizeStarportProduction = !openingStarportUnits.isEmpty() ||
                    UnitUtils.getNumFriendlyUnits(Units.TERRAN_VIKING_FIGHTER, true) <
                            (int)(ArmyManager.calcNumVikingsNeeded() * 1.2) + 6 ||
                    UnitUtils.getNumFriendlyUnits(Units.TERRAN_RAVEN, true) < 1;
            if (prioritizeStarportProduction) {
                //build starport units
                buildStarportUnitsLogic();

                //build factory units
                if (numTanks < 4) {
                    buildFactoryUnitsLogic();
                }
            }
            else {
                //build factory units
                if (numTanks < 10) {
                    buildFactoryUnitsLogic();
                }

                //build starport units
                buildStarportUnitsLogic();
            }
        }
        else { //otherwise prioritize starport production
            //build starport units
           buildStarportUnitsLogic();

            //build factory units
            if (BunkerContain.proxyBunkerLevel != 2) {
                if (Strategy.DO_DEFENSIVE_TANKS || Strategy.DO_USE_CYCLONES || Strategy.DO_OFFENSIVE_TANKS) {
                    buildFactoryUnitsLogic();
                } else if (!Cost.isGasBroke() && !UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_FACTORY).isEmpty()) {
                    liftFactory();
                }
            }
        }

        //build barracks units
        //TODO: below is hacky test code for marine rush
        if (Strategy.MARINE_ALLIN) {
            GameCache.barracksList.stream()
                    .filter(rax -> !rax.unit().getActive().orElse(true))
                    .forEach(rax -> {
                        int marineCount = UnitUtils.getMarineCount();
                        if (marineCount < Strategy.NUM_MARINES && Bot.OBS.getMinerals() >= 50) {
                            if (Bot.OBS.getMinerals() >= 50 && Bot.OBS.getMinerals() >= 50) { //replaced cuz marines priority over structures UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                                ActionHelper.unitCommand(rax.unit(), Abilities.TRAIN_MARINE, false);
                                Cost.updateBank(Units.TERRAN_MARINE);
                            }
                        }
                    });
        }
        else if (BunkerContain.proxyBunkerLevel == 0) {
            buildBarracksUnitsLogic();
        }

        //build starport logic
        buildStarportLogic();

        //build factory logic
        buildFactoryLogic();

        //build command center logic
        if (!Strategy.EXPAND_SLOWLY || Time.nowFrames() > Time.toFrames("5:00")) {
            buildCCLogic();
        }

        //no-gas left (marines&hellbats)
        noGasProduction();
    }

    private static void spamMulesOnEnemyBase() {
        //exit since mule spam replaced with troll muling
        if (MuleMessages.doTrollMule && GameCache.mineralBank > 100) {
            return;
        }
        List<Unit> ocList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
        int ocCount = (int)ocList.stream().filter(oc -> oc.getEnergy().get() >= 50f).count(); //OCs with mule energy
        int numMulesAvailable = ocList.stream()
                .mapToInt(oc -> oc.getEnergy().orElse(0f).intValue() / 50)
                .sum();

        //if ocs has energy pooled up and scan is needed to mule enemy
        if (isMuleSpamming || numMulesAvailable > 30 || ocList.stream().anyMatch(oc -> oc.getEnergy().orElse(0f) >= 199)) {
            isMuleSpamming = true;
            for (Base base : GameCache.baseList) {
                if (isBaseReadyForMuleSpam(base)) {
                    for (Unit mineralNode : base.getMineralPatchUnits()) {
                        ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineralNode, false);
                        ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineralNode, false);
                        numMulesAvailable -= 2;
                        ocCount -= 2;
                        if (numMulesAvailable <= 0) {
                            isMuleSpamming = false;
                            return;
                        }
                        if (ocCount <= 0) {
                            return;
                        }
                    }
                    base.prevMuleSpamFrame = Time.nowFrames();
                }
            }
            if (numMulesAvailable > 4) {
                if (!scanNextBase()) {
                    isMuleSpamming = muleMyBases(ocList, ocCount, numMulesAvailable);
                }
            }
            else {
                isMuleSpamming = false;
            }
        }
    }

    private static boolean muleMyBases(List<Unit> ocList, int ocCount, int numMulesAvailable) {
        for (int i=GameCache.baseList.size()-1; i >= 0; i--) {
            Base base = GameCache.baseList.get(i);
            if (base.isReadyForMining() && base.prevMuleSpamFrame + Time.toFrames(64) < Time.nowFrames()) {
                for (Unit mineral : base.getMineralPatchUnits()) {
                    base.prevMuleSpamFrame = Time.nowFrames();
                    ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineral, false);
                    ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, mineral, false);
                    ocCount -= 2;
                    numMulesAvailable -= 2;
                    if (numMulesAvailable <= 0) {
                        return false;
                    }
                    if (ocCount <= 0) {
                        return true;
                    }
                }
            }
        }
        MuleMessages.doTrollMule = true;
        return true;
    }

    //checks if base is enemy owned, has minerals, is being scanned, and has no mules
    private static boolean isBaseReadyForMuleSpam(Base base) {
        return !base.isMyBase() &&
                !base.getMineralPatchUnits().isEmpty() &&
                isMineralsVisible(base.getMineralPatchUnits()) &&
                base.prevMuleSpamFrame + Time.toFrames(10) < Time.nowFrames();
    }

    private static boolean scanNextBase() {
        //scan was cast on previous frame
        if (Time.nowFrames() <= ArmyManager.prevScanFrame + 24) {
            return true;
        }
        Base nextBase = GameCache.baseList.stream()
                .filter(base -> !base.isMyBase() &&
                        base.prevMuleSpamFrame + Time.toFrames(30) < Time.nowFrames() &&
                        !base.getMineralPatchUnits().isEmpty() &&
                        !isMineralsVisible(base.getMineralPatchUnits()))
                .findFirst()
                .orElse(null);
        if (nextBase == null) {
            return false;
        }
        UnitUtils.scan(nextBase.getCcPos());
        return true;
    }

    private static void noGasProduction() {
        if (Cost.isGasBroke() && !Strategy.MARINE_ALLIN && Bot.OBS.getGameLoop() > Time.toFrames("10:00")) {
            //land factory
            UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_FACTORY_FLYING).stream()
                    .filter(factory -> ActionIssued.getCurOrder(factory).isEmpty()) //if idle
                    .findFirst()
                    .ifPresent(factory -> {
                        landFactory(factory);
                    });

            //produce marines & hellbats
            GameCache.barracksList.stream()
                    .filter(barracks -> ActionIssued.getCurOrder(barracks.unit()).isEmpty()) //if idle
                    .forEach(barracks -> ActionHelper.unitCommand(barracks.unit(), Abilities.TRAIN_MARINE, false));
            UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_FACTORY).stream()
                    .filter(factory -> ActionIssued.getCurOrder(factory).isEmpty()) //if idle
                    .forEach(factory -> ActionHelper.unitCommand(factory, Abilities.TRAIN_HELLBAT, false));
        }

    }

    private static void landFactory(Unit factory) {
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && !base.isMyMainBase()) {
                Point2d landingPos = getFactoryLandingPos(base.getCcPos());
                if (landingPos != null) {
                    ActionHelper.unitCommand(factory, Abilities.LAND_FACTORY, landingPos, false);
                    return;
                }
            }
        }
    }

    private static Point2d getFactoryLandingPos(Point2d expansionPos) {
        List<Point2d> landingPosList = Position.getSpiralList(
                Position.toWholePoint(
                        Position.towards(expansionPos, LocationConstants.enemyMainBaseMidPos, 8)
                )
                , 4).stream()
                .sorted(Comparator.comparing(landPos -> landPos.distance(expansionPos)))
                .collect(Collectors.toList());

        List<QueryBuildingPlacement> queryList = landingPosList.stream()
                .map(p -> QueryBuildingPlacement.placeBuilding().useAbility(Abilities.LAND_FACTORY).on(p).build())
                .collect(Collectors.toList());

        List<Boolean> placementList = Bot.QUERY.placement(queryList);
        if (placementList.contains(true)) {
            return landingPosList.get(placementList.indexOf(true));
        }

        return null;
    }

    private static void build2ndLayerOfTech() {
        //build after 4th base started TODO: get armories earlier and smarter
        if (!Strategy.techBuilt && (ArmyManager.doOffense || Base.numMyBases() >= 4)) {
            List<Unit> engBayList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY);
            if (!engBayList.isEmpty()) {
                KetrocBot.purchaseQueue.add(
                        new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(engBayList.get(0).getTag())));
            }
            if (!UpgradeManager.airAttackUpgrades.isEmpty()) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            }
            if (!UpgradeManager.mechArmorUpgrades.isEmpty()) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            }
            Strategy.techBuilt = true;
        }
    }

    private static void cancelStructureLogic() {
        for (Unit structure : GameCache.inProductionList) {
            if (structure.getBuildProgress() < 1.0f) {
                if (UnitUtils.getHealthPercentage(structure) < 8) {
                    ActionHelper.unitCommand(structure, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                }
            }
        }
    }

    private static void buildDepotLogic() {
        if (GameCache.mineralBank > 100 && checkIfDepotNeeded() && !LocationConstants.extraDepots.isEmpty()) {
            KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }
    }

    //build 1 turret at each base except main base
    private static void buildTurretLogic() {
        if (Strategy.NO_TURRETS && !Switches.enemyHasCloakThreat) {
            return;
        }
        if (Switches.enemyCanProduceAir || Switches.enemyHasCloakThreat) { // || Time.nowFrames() > Time.toFrames("3:30")) {
            GameCache.baseList.stream()
                    .filter(base -> base.isMyBase() && base.isComplete())
                    .flatMap(base -> base.getTurrets().stream())
                    .filter(turret -> turret.getUnit() == null &&
                            !Purchase.isStructureQueued(Units.TERRAN_MISSILE_TURRET, turret.getPos()) &&
                            !StructureScv.isAlreadyInProductionAt(Units.TERRAN_MISSILE_TURRET, turret.getPos()))
                    .forEach(turret -> KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turret.getPos())));
        }
    }

    private static void buildAntiDropTurrets() {
        if (Strategy.DO_ANTIDROP_TURRETS && !LocationConstants.MAP.contains("Golden Wall")) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, GameCache.baseList.get(3).getTurrets().get(0).getPos()));
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, GameCache.baseList.get(2).getTurrets().get(0).getPos()));
        }
    }

    private static void ccActivityLogic() {
        for (Unit cc : GameCache.ccList) {
            if (cc.getBuildProgress() == 1.0f && !cc.getActive().get()) {
                switch ((Units) cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition().toPoint2d())) {
                            if (UnitUtils.getNumFriendlyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) >= Strategy.MAX_OCS) {
                                Point2d expansionBasePos = getNextAvailableExpansionPosition();
                                if (expansionBasePos != null) {
                                    floatCCForExpansion(cc, expansionBasePos);
                                }
                                else {
                                    //send to a random enemy base
                                    expansionBasePos = UnitUtils.getRandomUnownedBasePos();
                                    if (expansionBasePos != null) {
                                        if (UnitMicroList.getUnitSubList(StructureFloaterExpansionCC.class).size() < 10) {
                                            floatCCForPfHarass(cc, expansionBasePos);
                                        }
                                        else if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                            KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                        }
                                    }
                                }
                            }
                            //float CC from danger if it isn't a base CC
                            else if (InfluenceMaps.getGroundThreatToStructure(cc) * 2 > InfluenceMaps.getAirThreatToStructure(cc) &&
                                    GameCache.baseList.stream().noneMatch(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 2)) {
                                UnitMicroList.add(new StructureFloater(cc));
                            }
                            else if (!PurchaseStructureMorph.isTechRequired(Abilities.MORPH_ORBITAL_COMMAND)) {
                                //TODO: handle logic of Strategy.PRIORITIZE_EXPANDING here
                                //if not main cc, and if needed for expansion
                                if (UnitUtils.getDistance(cc, LocationConstants.baseLocations.get(0)) > 1 &&
                                        !Base.isABasePos(cc.getPosition().toPoint2d()) &&
                                        isCcNeededForExpansion()) {
                                    Point2d nextFreeBasePos = getNextAvailableExpansionPosition();
                                    if (nextFreeBasePos == null) { //do nothing, waits for expansion to free up TODO: make OC or wait??
                                        break;
                                    }
                                    floatCCForExpansion(cc, nextFreeBasePos);
                                }
                                else if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                        else { //if base that will become a PF TODO: use same logic as OC
                            if (!PurchaseStructureMorph.isTechRequired(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                if (!Purchase.isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                                    break; //don't queue scv
                                }
                            }
                        }
                        //build scv
                        if (Bot.OBS.getMinerals() >= 50 &&
                                UnitUtils.getNumScvs(true) < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                            ActionHelper.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        //float OC from danger if it isn't my main base OC
                        if (InfluenceMaps.getGroundThreatToStructure(cc) * 2 > InfluenceMaps.getAirThreatToStructure(cc) &&
                                GameCache.baseList.stream().noneMatch(base -> UnitUtils.getDistance(cc, base.getCcPos()) < 2)) {
                            UnitMicroList.add(new StructureFloater(cc));
                        }
                        else if (cc.getEnergy().get() >= 50) {
                            //scan enemy main at 4:30
                            if (LocationConstants.opponentRace == Race.PROTOSS && !Switches.scoutScanComplete && Time.nowFrames() > Time.toFrames("4:30")) {
                                ActionHelper.unitCommand(cc, Abilities.EFFECT_SCAN,
                                        Position.towards(LocationConstants.enemyMainBaseMidPos, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), 3), false);
                                Switches.scoutScanComplete = true;
                            }
                            else if (GameCache.mineralBank < 3000 &&
                                    !Switches.hasCastOCSpellThisFrame &&
                                    UnitUtils.numScansAvailable() > Switches.numScansToSave) {
                                //calldown mule
                                boolean didMule = false;
                                for (int i = GameCache.baseList.size() - 1; i >= 0; i--) {
                                    Base base = GameCache.baseList.get(i);
                                    if (base.isReadyForMining()) {
                                        int numMules = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MULE, base.getCcPos(), 10).size();
                                        if (numMules < base.getMineralPatchUnits().size()) {
                                            Unit mineralToMule;
                                            if (i == 2 && LocationConstants.MAP.contains("Golden Wall")) { //special case so mules don't get trapped
                                                mineralToMule = base.getMineralPatches().stream()
                                                        .map(MineralPatch::getNode)
                                                        .min(Comparator.comparing(unit -> UnitUtils.getDistance(unit, base.getCcPos())))
                                                        .orElse(null);
                                            }
                                            else { //mine the largest patch
                                                mineralToMule = base.getMineralPatches().stream()
                                                        .map(mineralPatch -> mineralPatch.getNode())
                                                        .max(Comparator.comparing(mineral -> mineral.getMineralContents().orElse(0)))
                                                        .orElse(null);
                                            }
                                            if (mineralToMule != null) {
                                                ActionHelper.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, mineralToMule, false);
                                                didMule = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                //if no base minerals, then distance mule closest mineral patch
                                if (!didMule) {
                                    Bot.OBS.getUnits(Alliance.NEUTRAL, node -> UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) &&
                                            node.unit().getDisplayType() == DisplayType.VISIBLE)
                                            .stream()
                                            .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), cc)))
                                            .map(UnitInPool::unit)
                                            .ifPresent(nearestMineral -> {
                                                ActionHelper.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, nearestMineral, false);
                                            });
                                }
                                Switches.hasCastOCSpellThisFrame = true;
                            }
                        }
                        //no break
                    case TERRAN_PLANETARY_FORTRESS:
                        //build scv
                        if (UnitUtils.getNumScvs(true) < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                            ActionHelper.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                }
            }
        }
    }

    private static void floatCCForPfHarass(Unit cc, Point2d basePos) {
        floatCCToBase(cc, basePos, true);
    }

    private static void floatCCForExpansion(Unit cc, Point2d basePos) {
        floatCCToBase(cc, basePos, false);
    }

    private static void floatCCToBase(Unit cc, Point2d basePos, boolean isEnemyBase) {
        UnitMicroList.add(new StructureFloaterExpansionCC(cc, basePos));
        LocationConstants.MACRO_OCS.add(cc.getPosition().toPoint2d());

        //setCC in baseList
        if (!isEnemyBase) {
            GameCache.baseList.stream()
                    .filter(base -> base.getCcPos().distance(basePos) < 1)
                    .findFirst()
                    .ifPresent(base -> base.setCc(Bot.OBS.getUnit(cc.getTag())));
        }

        //remove OC morph from purchase queue
        PurchaseStructureMorph.remove(cc.getTag());
    }

    private static boolean isCcNeededForExpansion() {
        //if safe and oversaturated
        return !UnitUtils.isWallUnderAttack() &&
                CannonRushDefense.isSafe &&
                Base.totalScvsRequiredForMyBases() < Math.min(Strategy.maxScvs, UnitUtils.getNumScvs(true) + 5);
    }

    private static void saveDyingCCs() {
        //skip if already maxed on macro OCs
        if (LocationConstants.MACRO_OCS.isEmpty()) {
            return;
        }
        //loop through bases looking for a dying cc
        for (Base base : GameCache.baseList) {
            if (!base.isMyBase()) {
                continue;
            }
            Unit cc = base.getCc().unit();

            //if complete CC or incomplete PF, low health, and ground attacking enemy nearby
            if (cc.getType() == Units.TERRAN_COMMAND_CENTER && cc.getBuildProgress() == 1.0f && UnitUtils.getHealthPercentage(cc) < Strategy.floatBaseAt
                    && !Bot.OBS.getUnits(Alliance.ENEMY, u -> UnitUtils.getDistance(u.unit(), cc) <= 10 && UnitUtils.canAttackGround(u.unit())).isEmpty()) {
                if (ActionIssued.getCurOrder(cc).isEmpty() && !LocationConstants.MACRO_OCS.isEmpty()) {
                    FlyingCC.addFlyingCC(cc, LocationConstants.MACRO_OCS.remove(0), true);

                    //remove cc from base
                    base.setCc(null);

                    //cancel PF morph in purchase queue
                    for (int i = 0; i < KetrocBot.purchaseQueue.size(); i++) {
                        Purchase p = KetrocBot.purchaseQueue.get(i);
                        if (p instanceof PurchaseStructureMorph) {
                            if (((PurchaseStructureMorph) p).getStructure().getTag().equals(cc.getTag())) {
                                KetrocBot.purchaseQueue.remove(i);
                                break;
                            }
                        }
                    }
                }
                //cancel PF upgrade
                else if (UnitUtils.getOrder(cc) == Abilities.MORPH_PLANETARY_FORTRESS) {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_MORPH_PLANETARY_FORTRESS, false);
                }
                //cancel scv production
                else {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_LAST, false);
                }
            }
        }
//        //send flying CCs to macro OC location
//        List<Unit> flyingCCs = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, Collections.emptyList());
//        for (Unit cc : flyingCCs) {
//            //if not on the way to land already
//            if (ActionIssued.getCurOrder(cc).isEmpty()) {
//                ActionHelper.unitCommand(cc, Abilities.LAND, LocationConstants.MACRO_OCS.remove(LocationConstants.MACRO_OCS.size()-1), false);
//            }
//            //Bot.onUnitDestroyed() re-adds this position to MACRO_OCS if the flying cc dies
//        }
    }

    private static void buildBarracksUnitsLogic() {
        //no idle barracks
        if (!UnitUtils.isAnyBarracksIdle()) {
            return;
        }
        //save minerals for factory/starport production
        if (GameCache.gasBank > 75 && (UnitUtils.isAnyFactoryIdle() || UnitUtils.isAnyStarportIdle())) {
            return;
        }

        Unit barracks = GameCache.barracksList.stream()
                .filter(u -> !u.unit().getActive().get())
                .findFirst().get().unit();

        // make marines if wall under attack
        if (UnitUtils.isWallUnderAttack() || ArmyManager.isEnemyInMain()) {
            if (UnitUtils.canAfford(Units.TERRAN_MARINE, true)) {
                ActionHelper.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                Cost.updateBank(Units.TERRAN_MARINE);
            }
            return;
        }

        // early safety marines
        else if (UnitUtils.getNumFriendlyUnits(Units.TERRAN_PLANETARY_FORTRESS, false) < 2) {
            if (UnitUtils.getMarineCount() < Strategy.NUM_MARINES && Bot.OBS.getMinerals() >= 50) {
                if (UnitUtils.canAfford(Units.TERRAN_MARINE, true)) {
                    ActionHelper.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                    Cost.updateBank(Units.TERRAN_MARINE);
                }
            }
        }
    }

    private static void buildFactoryUnitsLogic() {
        if (!GameCache.factoryList.isEmpty()) {
            for (UnitInPool factoryUIP : GameCache.factoryList) {
                Unit factory = factoryUIP.unit();
                if (factory.getActive().get()) {
                    continue;
                }
                if (factory.getAddOnTag().isPresent()) {
                    //cyclone strategy (build constantly)
                    if (Strategy.DO_USE_CYCLONES) {
                        if (UnitUtils.canAfford(Units.TERRAN_CYCLONE)) {
                            ActionHelper.unitCommand(factory, Abilities.TRAIN_CYCLONE, false);
                            Cost.updateBank(Units.TERRAN_CYCLONE);
                        }
                        return;
                    }

                    //offensive tank strategy (build constantly)
                    if (Strategy.DO_OFFENSIVE_TANKS) {
                        if (UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                            ActionHelper.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                            Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                        }
                        return;
                    }

                    //defensive tank strategy (build 2 per base)
                    int numTanks = UnitUtils.getNumFriendlyUnits(UnitUtils.SIEGE_TANK_TYPE, true);
                    //if tank needed for PF
                    if (numTanks < Math.min(Strategy.MAX_TANKS, Strategy.NUM_TANKS_PER_EXPANSION * (Base.numMyBases() - 1))) {
                        UnitInPool tankOnOffense = UnitMicroList.unitMicroList.stream()
                                .filter(u -> u instanceof TankOffense)
                                .findFirst()
                                .map(u -> u.unit)
                                .orElse(null);
                        //take an offensive tank
                        if (tankOnOffense != null) {
                            UnitMicroList.remove(tankOnOffense.getTag());
                        }
                        //build a new tank
                        else if (UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                            ActionHelper.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                            Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                        }
                    }
                } else if (!Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    ActionHelper.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }
    }

    public static void liftFactory() {
        UnitInPool factory = GameCache.factoryList.get(0);
        if (factory.unit().getBuildProgress() == 1f) {
            if (factory.unit().getActive().orElse(true)) {
                ActionHelper.unitCommand(factory.unit(), Abilities.CANCEL_LAST, false);
            }
            else {
                Point2d behindMainBase = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), 10);
                if (BunkerContain.proxyBunkerLevel == 2) {
                    BunkerContain.onFactoryLift();
                }
                ActionHelper.unitCommand(factory.unit(), Abilities.LIFT, false);
                DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.MOVE, factory, behindMainBase));

                //add factory positions to available starport positions
                Point2d factoryPos = factory.unit().getPosition().toPoint2d();
                if (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, factoryPos)) { //if not proxied
                    if (factory.unit().getAddOnTag().isPresent()) {
                        LocationConstants.STARPORTS.add(0, factoryPos);
                    }
                    else {
                        LocationConstants.STARPORTS.add(factoryPos);
                    }
                }
                LocationConstants.STARPORTS.addAll(LocationConstants.FACTORIES);
                LocationConstants.FACTORIES.clear();
            }
        }
    }

    private static void buildStarportUnitsLogic() {
        for (UnitInPool starport : GameCache.starportList) {
            if (!starport.unit().getActive().get()) {
                Abilities unitToProduce = (Strategy.gamePlan == GamePlan.TANK_VIKING || Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING) ?
                        tankVikingDecideStarportUnit() :
                        decideStarportUnit();
                Units unitType = Bot.abilityToUnitType.get(unitToProduce);
                //get add-on if required
                if (starport.unit().getAddOnTag().isEmpty() && !Purchase.isAddOnQueued(starport.unit()) &&
                        (Bot.OBS.getFoodUsed() >= 198 ||
                        ((unitToProduce == Abilities.TRAIN_RAVEN ||
                                unitToProduce == Abilities.TRAIN_BANSHEE ||
                                unitToProduce == Abilities.TRAIN_BATTLECRUISER)))) {
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starport));
                }
                else if (UnitUtils.canAfford(unitType)) {
                    ActionHelper.unitCommand(starport.unit(), unitToProduce, false);
                    if (!openingStarportUnits.isEmpty()) {
                        openingStarportUnits.remove(0);
                    }
                    Cost.updateBank(unitType);
                }
            }
        }
    }

    private static boolean isCloakInProduction() {
        return UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .anyMatch(techLab -> UnitUtils.getOrder(techLab) == Abilities.RESEARCH_BANSHEE_CLOAKING_FIELD);
    }

    public static Abilities tankVikingDecideStarportUnit() { //never max out without a raven
        //first build hardcoded starport units
        if (!openingStarportUnits.isEmpty()) {
            return openingStarportUnits.get(0);
        }

        int numRavens = UnitUtils.getNumFriendlyUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.getNumFriendlyUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int vikingsRequired = (int)(ArmyManager.calcNumVikingsNeeded() * 1.2) + 6;

        //never max out without a raven
        if (Bot.OBS.getFoodUsed() >= 196 && numRavens == 0) {
            return Abilities.TRAIN_RAVEN;
        }

        //when enemy does banshee harass, open viking-raven-viking
        if (Strategy.ENEMY_DOES_BANSHEE_HARASS) {
            if (numVikings < 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
            else if (numRavens < 1) {
                return Abilities.TRAIN_RAVEN;
            }
            else if (numVikings == 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
        }

        //maintain 1+ ravens if enemy can produce cloaked/burrowed attackers
        if (numRavens == 0 && Switches.enemyHasCloakThreat) {
            return Abilities.TRAIN_RAVEN;
        }

        //maintain 1+ ravens if an expansion needs clearing
        if (numRavens == 0 && !ExpansionClearing.expoClearList.isEmpty()) {
            return Abilities.TRAIN_RAVEN;
        }

        //get 1 raven if an enemy kill squad requires one
        if (AirUnitKillSquad.enemyAirTargets.stream()
                .anyMatch(killSquad -> killSquad.isRavenRequired() && killSquad.getRaven() == null)) {
            return Abilities.TRAIN_RAVEN;
        }

        //get required vikings
        if (numVikings < vikingsRequired) {
            return Abilities.TRAIN_VIKING_FIGHTER;
        }

        //otherwise raven
        return Strategy.DEFAULT_STARPORT_UNIT;
    }



        public static Abilities decideStarportUnit() {
        //first build hardcoded starport units
        if (!openingStarportUnits.isEmpty()) {
            return openingStarportUnits.get(0);
        }

        int numBanshees = UnitUtils.getNumFriendlyUnits(Units.TERRAN_BANSHEE, true);
        int numRavens = UnitUtils.getNumFriendlyUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.getNumFriendlyUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int numLiberators = UnitUtils.getNumFriendlyUnits(UnitUtils.LIBERATOR_TYPE, true) +
                Ignored.numOfType(UnitUtils.LIBERATOR_TYPE);
        int vikingsRequired = ArmyManager.calcNumVikingsNeeded();
        int vikingsByRatio = (int) (numBanshees * Strategy.VIKING_BANSHEE_RATIO);
        if (vikingsByRatio > vikingsRequired) { //build vikings 1:1 with banshees until 1.5x vikings required
            vikingsRequired = Math.min((int) (vikingsRequired * 1.8), vikingsByRatio);
        }
        int ravensRequired = (LocationConstants.opponentRace == Race.ZERG) ? 4 : 1;

        //start with main army banshee in TvT bunker contain
        if (BunkerContain.proxyBunkerLevel == 2 && numBanshees == 0) {
            return Abilities.TRAIN_BANSHEE;
        }

        //never max out without a raven
        if (Bot.OBS.getFoodUsed() >= 196 && numRavens == 0) {
            return Abilities.TRAIN_RAVEN;
        }

        //when enemy does banshee harass, maintain viking-raven-viking
        if (Strategy.ENEMY_DOES_BANSHEE_HARASS) {
            if (numVikings < 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
            else if (numRavens < 1) {
                return Abilities.TRAIN_RAVEN;
            }
            else if (numVikings == 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
        }

        //maintain 1+ ravens if enemy can produce cloaked/burrowed attackers
        if (numRavens == 0 && Switches.enemyHasCloakThreat) {
            return Abilities.TRAIN_RAVEN;
        }
//
//        //maintain 1+ ravens if using cyclones
//        if (numRavens == 0 && Strategy.DO_USE_CYCLONES) {
//            return Abilities.TRAIN_RAVEN;
//        }

        //maintain 1+ ravens if an expansion needs clearing
        if (numRavens == 0 && !ExpansionClearing.expoClearList.isEmpty()) {
            return Abilities.TRAIN_RAVEN;
        }

        //get 1 raven if an enemy kill squad requires one
        if (AirUnitKillSquad.enemyAirTargets.stream()
                .anyMatch(killSquad -> killSquad.isRavenRequired() && killSquad.getRaven() == null)) {
            return Abilities.TRAIN_RAVEN;
        }

        //get required vikings
        if (numVikings < vikingsRequired) {
            return Abilities.TRAIN_VIKING_FIGHTER;
        }

        //maintain a banshee count of 1 (2 vs zerg with mass ravens)
        if (numBanshees < MIN_BANSHEES) {
            return Abilities.TRAIN_BANSHEE;
        }

        if (LocationConstants.opponentRace == Race.ZERG) {
            //maintain a raven count of 1 vs zerg
            if (numRavens < 1) {
                return Abilities.TRAIN_RAVEN;
            }

            //maintain a viking count of 1 vs zerg
            if (numVikings < 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
        }

        //get defensive liberators for each expansion up to 6
        if (Strategy.DO_INCLUDE_LIBS &&
                numLiberators < Math.min(Strategy.MAX_LIBS, Strategy.NUM_LIBS_PER_EXPANSION * (Base.numMyBases() - 1)) &&
                !freeUpOffensiveLib()) {
            return Abilities.TRAIN_LIBERATOR;
        }
        //get 1 raven for observers
        if (numRavens == 0 && numBanshees > 0 && numVikings > 0 && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
            return Abilities.TRAIN_RAVEN;
        }
        //TvZ, get raven after first banshee, then a 2nd after 15 air units.
        if (LocationConstants.opponentRace == Race.ZERG) {
            if (numBanshees + numVikings > 15 && numRavens < ravensRequired) {
                return Abilities.TRAIN_RAVEN;
            }
        }
        //TvT/TvP, get a raven after 15 banshees+vikings
        else if (numRavens < ravensRequired && numBanshees + numVikings >= 15) {
            return Abilities.TRAIN_RAVEN;
        }

        //otherwise banshee
        return Strategy.DEFAULT_STARPORT_UNIT;
    }

    private static boolean freeUpOffensiveLib() {
        UnitInPool libOnOffense = UnitMicroList.unitMicroList.stream()
                .filter(u -> u instanceof LibOffense)
                .findFirst()
                .map(u -> u.unit)
                .orElse(null);
        //free up an offensive lib
        if (libOnOffense != null) {
            UnitMicroList.remove(libOnOffense.getTag());
            return true;
        }
        return false;
    }

    private static void buildCCLogic() {
        if (GameCache.mineralBank > 500 && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) &&
                (Base.numMyBases() < LocationConstants.baseLocations.size() - Strategy.NUM_DONT_EXPAND ||
                        !LocationConstants.MACRO_OCS.isEmpty() ||
                        !Placement.possibleCcPosList.isEmpty())) {
            if (GameCache.mineralBank > GameCache.gasBank ||
                    Base.numAvailableBases() > 0 ||
                    UnitUtils.getNumFriendlyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) < Strategy.MAX_OCS) {
                addCCToPurchaseQueue();
            }
        }
    }

    private static void buildFactoryLogic() {
        if (UnitUtils.getNumFriendlyUnits(UnitUtils.FACTORY_TYPE, true) < 2 &&
                (Strategy.gamePlan == GamePlan.TANK_VIKING ||
                        Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING ||
                        Strategy.gamePlan == GamePlan.RAVEN_CYCLONE) &&
                UnitUtils.canAfford(Units.TERRAN_FACTORY) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_FACTORY) &&
                !LocationConstants.FACTORIES.isEmpty() &&
                (!Strategy.ENEMY_DOES_BANSHEE_HARASS || !Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_RAVEN).isEmpty())) {
            if (isAllProductionStructuresBusy()) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
            }
        }
    }

    private static void buildStarportLogic() {
        if (!LocationConstants.STARPORTS.isEmpty() &&
                UnitUtils.canAfford(Units.TERRAN_STARPORT) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_STARPORT)) {
            if (Bot.OBS.getFoodUsed() > 197 ||
                    (UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_STARPORT) < 3 &&
                            isAllStarportsActive())) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
            }
        }
    }

    private static boolean isAllProductionStructuresBusy() {
        return isAllStarportsActive() && isAllFactoriesActive();
    }

    private static boolean isAllFactoriesActive() {
        return GameCache.factoryList.stream()
                .noneMatch(u -> u.unit().getType() == Units.TERRAN_FACTORY &&
                        (u.unit().getOrders().isEmpty() ||
                                u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB ||
                                u.unit().getOrders().get(0).getProgress().orElse(0f) > 0.7f));
    }

    private static boolean isAllStarportsActive() {
        return GameCache.starportList.stream()
                .noneMatch(u -> u.unit().getOrders().isEmpty() ||
                        u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB ||
                        u.unit().getOrders().get(0).getProgress().orElse(0f) > 0.7f);
    }

    private static void addCCToPurchaseQueue() {
        if (Strategy.BUILD_EXPANDS_IN_MAIN) {
            if (!purchaseMacroCC()) {
                if (!purchaseExpansionCC()) {
                    purchaseExtraCC();
                }
            }
        }
        else if (Strategy.PRIORITIZE_EXPANDING) {
            if (!purchaseExpansionCC()) {
                if (!purchaseMacroCC()) {
                    purchaseExtraCC();
                }
            }
        }
        else {
            int scvsForMaxSaturation = Base.totalScvsRequiredForMyBases();
            int numScvs = UnitUtils.getNumScvs(true);
            if (UnitUtils.isWallUnderAttack() || !CannonRushDefense.isSafe) {
                purchaseMacroCC();
            } else if (Math.min(numScvs + 25, Strategy.maxScvs) <= scvsForMaxSaturation) {
                if (!purchaseMacroCC()) {
                    if (!purchaseExpansionCC()) {
                        purchaseExtraCC();
                    }
                }
            } else {
                if (!purchaseExpansionCC()) {
                    if (!purchaseMacroCC()) {
                        purchaseExtraCC();
                    }
                }
            }
        }
    }

    private static void purchaseExtraCC() {
        if (GameCache.mineralBank > 2000 && enemyHasMineralPatches()) {
            Point2d ccPos = Placement.getNextExtraCCPos();
            if (ccPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, ccPos));
            }
        }
    }

    private static boolean enemyHasMineralPatches() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isMyBase())
                .anyMatch(base -> !base.isMyBase() &&
                        base.lastScoutedFrame + Time.toFrames("5:00") > Time.nowFrames() &&
                        !base.getMineralPatchUnits().isEmpty());
    }

    private static boolean purchaseExpansionCC() {
        //if an expansion position is available, build expansion CC
        Point2d expansionPos = getNextAvailableExpansionPosition();
        if (expansionPos != null) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, expansionPos));
        }
        return expansionPos != null;
    }

    private static Point2d getNextAvailableExpansionPosition() {
        List<Base> expansionOptions = GameCache.baseList.subList(0, GameCache.baseList.size() - getNumEnemyBasesIgnored()).stream()
                .filter(base -> base.isUntakenBase() &&
                        !base.isDryedUp() &&
                        InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, base.getCcPos()) == 0)
                .collect(Collectors.toList());

        for (Base base : expansionOptions) {
            if (Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos())) {
                return base.getCcPos();
            }
            else if (!ExpansionClearing.isVisiblyBlockedByUnit(base.getCcPos())) { //UnitUtils.isExpansionCreepBlocked(base.getCcPos())
                ExpansionClearing.add(base.getCcPos());
            }
        }
        return null;
    }

    public static int getNumEnemyBasesIgnored() {
        return (LocationConstants.MACRO_OCS.isEmpty()) ? 2 : 5; //try to expand deeper on enemy side when macro OCs are complete
    }

    public static boolean purchaseMacroCC() {
        if (LocationConstants.MACRO_OCS.isEmpty()) {
            return false;
        }

        Point2d ccPos;
        Point2d nextAvailableBase = Base.getNextAvailableBase();
        if (nextAvailableBase == null) {
            ccPos = LocationConstants.MACRO_OCS.remove(0);
        }
        else {
            ccPos = LocationConstants.MACRO_OCS.stream()
                    .min(Comparator.comparing(p -> p.distance(nextAvailableBase)))
                    .get();
            LocationConstants.MACRO_OCS.remove(ccPos);
        }
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, ccPos));
        return true;
    }

    public static boolean ccToBeOC(Point2d ccPos) {
        return LocationConstants.baseLocations
                .subList(Strategy.NUM_BASES_TO_OC, LocationConstants.baseLocations.size()) //ignore OC base locations
                .stream()
                .noneMatch(p -> ccPos.distance(p) < 1);
    }

    private static boolean checkIfDepotNeeded() {
        if (Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) { //if depot already in queue
            return false;
        }
        int curSupply = Bot.OBS.getFoodUsed();
        int supplyCap = Bot.OBS.getFoodCap();
        if (supplyCap == 200) { // max supply available
            return false;
        }

        if (supplyCap - curSupply + supplyInProduction() >= supplyPerProductionCycle()) { //if not nearing supply block
            return false;
        }

        return true;
    }

    //total supply to be produced during the time it takes to make a supply depot
    private static int supplyPerProductionCycle() {
        return (int)(Math.min(Strategy.maxScvs - UnitUtils.getNumScvs(true), Base.numMyBases()) * 2.34 + //scvs (2 cuz 1 supply * 1/2 build time of depot)
                GameCache.starportList.size() * 2.34 +
                GameCache.factoryList.stream()
                        .filter(factory -> factory.unit().getType() == Units.TERRAN_FACTORY)
                        .count() * 3.34);
    }

    private static int supplyInProduction() {
        //include supply of every depot in production
        int supply = 8 * (int) StructureScv.scvBuildingList.stream().filter(scv -> scv.buildAbility == Abilities.BUILD_SUPPLY_DEPOT).count();

        //include supply of CCs that are 60%+ done production
        supply += StructureScv.scvBuildingList.stream()
                .filter(structureScv -> structureScv.structureType == Units.TERRAN_COMMAND_CENTER)
                .filter(structureScv -> structureScv.getStructureUnit() != null &&
                        structureScv.getStructureUnit().unit().getBuildProgress() > 0.6)
                .count() * 14;

        return supply;
    }

    public static int numStructuresQueued(Units structureType) {
        int count = 0;
        for (Purchase p : KetrocBot.purchaseQueue) {
            if (p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType().equals(structureType)) {
                count++;
            }
        }
        return count;
    }

    public static List<Point2d> calculateTurretPositions(Point2d ccPos) {//pick position away from enemy main base like a knight move (3.5x1.5)
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getX();
        float yEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 1.5f;
        float yMove = 1.5f;
        float xTurret1;
        float yTurret1;
        float xTurret2;
        float yTurret2;

        if (Math.abs(xDistance) > Math.abs(yDistance)) { //move 3.5x1.5
            yMove = 3.5f;
        } else { //move 1x3
            xMove = 3.5f;
        }
        xTurret1 = xCC + xMove;
        xTurret2 = xCC - xMove;

        if (xDistance * yDistance > 0) {
            yTurret1 = yCC - yMove;
            yTurret2 = yCC + yMove;
        } else {
            yTurret1 = yCC + yMove;
            yTurret2 = yCC - yMove;
        }
        return List.of(Point2d.of(xTurret1, yTurret1), Point2d.of(xTurret2, yTurret2));
    }

    public static boolean isPlaceable(Point2d pos, Abilities buildAction) { //TODO: not perfect.  sometimes return false positive
        Point2d gridPos = Position.toNearestHalfPoint(pos);

        //if creep is there
        if (Bot.OBS.hasCreep(pos)) {
            return false;
        }
        float distance = UnitUtils.getStructureRadius(Bot.abilityToUnitType.get(buildAction)) * 0.8f;

        //if enemy ground unit/structure there
        return Bot.OBS.getUnits(Alliance.ENEMY,
                enemy -> UnitUtils.getDistance(enemy.unit(), gridPos) < distance &&
                        !enemy.unit().getFlying().orElse(false)).isEmpty();  //default false to handle structure snapshots
    }

    private static boolean isMineralsVisible(List<Unit> mineralPatches) {
        return mineralPatches.stream().allMatch(patch -> patch.getDisplayType() == DisplayType.VISIBLE);
    }
}

