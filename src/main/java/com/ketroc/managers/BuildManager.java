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
import com.ketroc.gamestate.GameCache;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.geometry.Position;
import com.ketroc.launchers.KetrocLauncher;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.*;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.CannonRushDefense;
import com.ketroc.utils.*;

import java.util.*;
import java.util.stream.Collectors;

public class BuildManager {
    public static final List<Abilities> BUILD_ACTIONS = Arrays.asList(
            Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER, Abilities.BUILD_STARPORT, Abilities.BUILD_SUPPLY_DEPOT,
            Abilities.BUILD_ARMORY, Abilities.BUILD_BARRACKS, Abilities.BUILD_BUNKER, Abilities.BUILD_ENGINEERING_BAY,
            Abilities.BUILD_FACTORY, Abilities.BUILD_FUSION_CORE, Abilities.BUILD_GHOST_ACADEMY, Abilities.BUILD_MISSILE_TURRET,
            Abilities.BUILD_SENSOR_TOWER
    );
    private static boolean isMuleSpamming;
    public static List<Units> openingStarportUnits = new ArrayList<>();
    public static List<Units> openingFactoryUnits = new ArrayList<>();


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

        if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
            trainStarportUnits();
            trainFactoryUnits();
            trainBarracksUnits();
            buildNuke();
            buildFactoryLogic();
            buildStarportLogicForGhostMarauder();
            buildBarracksLogic();
            if (!Strategy.EXPAND_SLOWLY || Purchase.isBuildOrderComplete()) {
                buildCCLogic();
            }
            return;
        }

        if (Strategy.gamePlan == GamePlan.BC_RUSH) {
            trainStarportUnits_BcRush();
            trainFactoryUnits_BcRush();
            trainBarracksUnits_BcRush();
            //buildExtraBunkers(); //TODO make marines smart enough to get into unmaxed bunkers

            //if starports are all building BCs, add 3rd starport and get yamato
            int numStarports = UnitUtils.numMyUnits(UnitUtils.STARPORT_TYPE, true);
            if (numStarports == 2 &&
                    UnitUtils.canAfford(Units.TERRAN_STARPORT) &&
                    !PurchaseStructure.isTechRequired(Units.TERRAN_STARPORT) &&
                    isAllProductionStructuresActive(Units.TERRAN_STARPORT)) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.YAMATO_CANNON));
            }

            buildBarracksLogic_BcRush();

            return;
        }

        if (Strategy.gamePlan == GamePlan.MECH_ALL_IN) {
            trainFactoryUnitsMechAllIn();
            trainBarracksUnitsMechAllIn();
            return;
        }

        //prioritize factory production when doing tank viking strat, and viking/raven count is fine
        if (Strategy.gamePlan == GamePlan.TANK_VIKING ||
                Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING ||
                (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_STRONG && PosConstants.opponentRace == Race.TERRAN)) {
            int numTanks = UnitMicroList.numOfUnitClass(TankOffense.class);
            if (numTanks > 2 && doPrioritizeStarportUnits()) {
                //build starport units
                buildStarportUnitsLogic();

                //build factory units
                if (numTanks < 4) {
                    buildFactoryUnitsLogic();
                }
            }
            else {
                //build factory units
                if (numTanks < 12) {
                    buildFactoryUnitsLogic();
                }

                //build starport units
                buildStarportUnitsLogic();
            }
        }
        else {
            //build factory units
            if (Strategy.DO_DEFENSIVE_TANKS || Strategy.DO_USE_CYCLONES || Strategy.DO_OFFENSIVE_TANKS) {
                buildFactoryUnitsLogic();
            }
            else if (!UnitUtils.isOutOfGas() && !UnitUtils.myUnitsOfType(Units.TERRAN_FACTORY).isEmpty()) {
                UnitUtils.myUnitsOfType(Units.TERRAN_FACTORY).forEach(factory -> liftFactory(factory));
            }

            //build starport units
            buildStarportUnitsLogic();
        }

        //build barracks units
        //TODO: below is hacky test code for marine rush
        if (Strategy.MARINE_ALLIN) {
            GameCache.barracksList.stream()
                    .filter(rax -> UnitUtils.canStructureProduce(rax.unit()))
                    .forEach(rax -> {
                        int marineCount = UnitUtils.numMyUnits(Units.TERRAN_MARINE, true);
                        if (marineCount < Strategy.MAX_MARINES && Bot.OBS.getMinerals() >= 50) {
                            if (Bot.OBS.getMinerals() >= 50 && Bot.OBS.getMinerals() >= 50) { //replaced cuz marines priority over structures UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                                ActionHelper.unitCommand(rax.unit(), Abilities.TRAIN_MARINE, false);
                                Cost.updateBank(Units.TERRAN_MARINE);
                            }
                        }
                    });
        } else if (BunkerContain.proxyBunkerLevel == 0) {
            buildBarracksUnitsLogic();
        }

        //build factory logic
//        if (!doPrioritizeStarportUnits()) { TODO: factory production only in build order atm
//            buildFactoryLogic();
//        }

        //build starport logic
        if (Purchase.isBuildOrderComplete()) {
            buildStarportLogic();
        }

        //build command center logic
        if (!Strategy.EXPAND_SLOWLY || Purchase.isBuildOrderComplete()) {
            buildCCLogic();
        }

        //no-gas left (marines&hellbats)
        noGasProduction();
    }

    //TODO: adjust marine production code to prioritize hellions
    private static void trainFactoryUnits_BcRush() {
        if (GameCache.factoryList.isEmpty()) {
            return;
        }

        Unit factory = GameCache.factoryList.get(0).unit();

        //build reactor after 1st 2 BCs have started
        if (!UnitUtils.isReactored(factory)) {
            if (UnitUtils.numMyUnits(Units.TERRAN_BATTLECRUISER, true) >= 2 &&
                    !PurchaseStructureMorph.contains(factory)) {
                KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_REACTOR_FACTORY));
            }
            return;
        }

        //build hellions when there is a mineral float
        if (UnitUtils.canStructureProduce(factory) &&
                UnitUtils.canAfford(Units.TERRAN_HELLION, true) &&
                GameCache.mineralBank/4 > GameCache.gasBank/3) {
            ActionHelper.unitCommand(factory, Abilities.TRAIN_HELLION, false);
        }
    }

    private static void buildBarracksLogic_BcRush() {
        if (Purchase.isBuildOrderComplete() &&
                GameCache.mineralBank/4.5f > GameCache.gasBank/3 &&
                GameCache.mineralBank > 500 &&
                isAllProductionStructuresActive(Units.TERRAN_BARRACKS) &&
                isAllProductionStructuresActive(Units.TERRAN_FACTORY) &&
                UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_BARRACKS) < 3 &&
                UnitUtils.numMyUnits(Units.TERRAN_BARRACKS, true) < 10) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
        }
    }

    private static void buildExtraBunkers() {
        //if floating minerals and all current bunkers are full
        if (GameCache.mineralBank/4 > GameCache.gasBank/3 &&
                UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_BUNKER) == 0 &&
                UnitUtils.myUnitsOfType(Units.TERRAN_BUNKER).stream().allMatch(bunker -> bunker.getCargoSpaceTaken().orElse(0) == 4)) {
            Point2d newBunkerPos = findNewBunkerPos();
            if (newBunkerPos != null) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, newBunkerPos));
            }
        }
    }

    private static Point2d findNewBunkerPos() {
        List<Point2d> extraBunkerPosList = getExtraBunkerPosList();
        if (!extraBunkerPosList.isEmpty()) {
            return extraBunkerPosList.get(0);
        }
        return null;
    }

    private static List<Point2d> getExtraBunkerPosList() {
        List<Point2d> extraBunkerPosList = new ArrayList<>();
        Point2d startPos = PosConstants.BUNKER_NATURAL;
        for (int x = 0; x <= 9; x += 3) {
            for (int y = 0; y <= 9; y += 3) {
                if (PlacementMap.canFit3x3(startPos.add(x, y))) {
                    extraBunkerPosList.add(startPos.add(x, y));
                }
                if (PlacementMap.canFit3x3(startPos.add(-x, y))) {
                    extraBunkerPosList.add(startPos.add(-x, y));
                }
                if (PlacementMap.canFit3x3(startPos.add(x, -y))) {
                    extraBunkerPosList.add(startPos.add(x, -y));
                }
                if (PlacementMap.canFit3x3(startPos.add(-x, -y))) {
                    extraBunkerPosList.add(startPos.add(-x, -y));
                }
            }
        }
        Collections.sort(extraBunkerPosList, Comparator.comparing(p -> startPos.distance(p)));
        return extraBunkerPosList;
    }

    private static boolean doPrioritizeStarportUnits() {
        return !GameCache.starportList.isEmpty() &&
                ((PosConstants.opponentRace == Race.TERRAN &&
                        UnitUtils.numMyUnits(Units.TERRAN_VIKING_FIGHTER, true) < numVikingsInTvT()) ||
                (GameCache.starportList.stream().anyMatch(u -> u.unit().getAddOnTag().isPresent()) &&
                        UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true) < 1));
    }

    private static int numVikingsInTvT() {
        int numVikingsNeeded = ArmyManager.calcNumVikingsNeeded();
        numVikingsNeeded += Math.min(6, (int)(numVikingsNeeded*0.5)); //get up to 6 more than needed
        numVikingsNeeded = Math.max(5, numVikingsNeeded); //maintain at least 5 vikings
        return Math.max(numVikingsNeeded, UnitMicroList.numOfUnitClass(TankOffense.class)*2); //2 per tank minimum
    }

    private static void spamMulesOnEnemyBase() {
        //exit since mule spam replaced with troll muling
        if (MannerMule.doTrollMule) {
            return;
        }
        List<Unit> ocList = UnitUtils.myUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
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
        MannerMule.doTrollMule = true;
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
        if (UnitUtils.isOutOfGas()) {
            Chat.tag("NO_GAS");
            //land factory
            UnitUtils.myUnitsOfType(Units.TERRAN_FACTORY_FLYING).stream()
                    .filter(factory -> ActionIssued.getCurOrder(factory).isEmpty()) //if idle
                    .findFirst() //one factory only each step to ensure same position isn't given to multiple factories
                    .ifPresent(factory -> landFactories(factory));

            //produce marines & hellbats
            GameCache.barracksList.stream()
                    .filter(barracks -> ActionIssued.getCurOrder(barracks).isEmpty()) //if idle
                    .forEach(barracks -> ActionHelper.unitCommand(barracks.unit(), Abilities.TRAIN_MARINE, false));
            UnitUtils.myUnitsOfType(Units.TERRAN_FACTORY).stream()
                    .filter(factory -> ActionIssued.getCurOrder(factory).isEmpty()) //if idle
                    .forEach(factory -> ActionHelper.unitCommand(factory, Abilities.TRAIN_HELLBAT, false));
        }

    }

    private static void landFactories(Unit factory) {
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && !base.isMyMainBase()) {
                Point2d landingPos = getFactoryLandingPos(base.getResourceMidPoint());
                if (landingPos != null) {
                    ActionHelper.unitCommand(factory, Abilities.LAND_FACTORY, landingPos, false);
                    return;
                }
            }
        }
    }

    private static Point2d getFactoryLandingPos(Point2d landingPos) {
        List<Point2d> landingPosList = Position.getSpiralList(Position.toWholePoint(landingPos),4)
                .stream()
                .sorted(Comparator.comparing(landPos -> landPos.distance(landingPos)))
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
        if (Strategy.techBuilt) {
            return;
        }

        if (Strategy.gamePlan == GamePlan.MECH_ALL_IN) {
            if (Switches.enemyCanProduceAir) {
                Strategy.techBuilt = true;
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY, PosConstants._3x3Structures.remove(0)));
            }
            return;
        }

        if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
            if (ArmyManager.doOffense) {
                Strategy.techBuilt = true;
                if (UnitUtils.numMyUnits(Units.TERRAN_ENGINEERING_BAY, true) == 0) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
                KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.PERSONAL_CLOAKING));
                if (PosConstants.opponentRace == Race.PROTOSS) {
                    KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.ENHANCED_SHOCKWAVES));
                }
            }
            return;
        }

        //build after 4th base started TODO: get armories earlier and smarter
        if ((ArmyManager.doOffense && BunkerContain.proxyBunkerLevel == 0) || Base.numMyBases() >= 4) {
            Strategy.techBuilt = true;
            int numArmories = UnitUtils.numMyUnits(Units.TERRAN_ARMORY, true);
            if (!UpgradeManager.airAttackUpgrades.isEmpty() && numArmories-- < 1) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            }
            if (!UpgradeManager.mechArmorUpgrades.isEmpty() && numArmories-- < 1) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            }
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
        if (GameCache.mineralBank > 100 &&
                Purchase.isBuildOrderComplete() &&
                checkIfDepotNeeded() &&
                !PosConstants.extraDepots.isEmpty()) {
            KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }
    }

    //build 1 turret at each base except main base
    private static void buildTurretLogic() {
        if (Strategy.NO_TURRETS) { // && !Switches.enemyHasCloakThreat) {
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
        if (Strategy.DO_ANTIDROP_TURRETS && !PosConstants.MAP.contains("Golden Wall")) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, GameCache.baseList.get(3).getTurrets().get(0).getPos()));
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, GameCache.baseList.get(2).getTurrets().get(0).getPos()));
        }
    }

    private static void ccActivityLogic() {
        for (Unit cc : GameCache.ccList) {
            if (cc.getBuildProgress() == 1.0f && UnitUtils.canStructureProduce(cc)) {
                switch ((Units) cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition().toPoint2d())) {
                            if (UnitUtils.numMyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) >= Strategy.MAX_OCS) {
                                Point2d expansionBasePos = getNextAvailableExpansionPosition();
                                if (expansionBasePos != null) {
                                    floatCCForExpansion(cc, expansionBasePos);
                                }
                                else {
                                    //send to a random enemy base
                                    expansionBasePos = UnitUtils.getRandomUnownedBasePos();
                                    if (expansionBasePos != null) {
                                        if (GameCache.gasBank > 1500 && UnitMicroList.numOfUnitClass(StructureFloaterExpansionCC.class)
                                                < Base.numEnemyBases() * 2) {
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
                                if (UnitUtils.getDistance(cc, PosConstants.baseLocations.get(0)) > 1 &&
                                        !Base.isABasePos(cc.getPosition().toPoint2d()) &&
                                        isCcNeededForExpansion()) {
                                    Point2d nextFreeBasePos = getNextAvailableExpansionPosition();
                                    if (nextFreeBasePos == null) { //do nothing, waits for expansion to free up TODO: make OC or wait??
                                        if (!Purchase.isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                            KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                        }
                                    }
                                    else {
                                        floatCCForExpansion(cc, nextFreeBasePos);
                                    }
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
                                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                                    break; //don't queue scv
                                }
                            }
                        }
                        //build scv
                        if (Bot.OBS.getMinerals() >= 50 &&
                                UnitUtils.numScvs(true) < Math.min(Base.scvsReqForMyBases() + 10, Strategy.maxScvs)) {
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
                            if (PosConstants.opponentRace == Race.PROTOSS &&
                                    Strategy.gamePlan != GamePlan.MARINE_RUSH &&
                                    Strategy.gamePlan != GamePlan.SCV_RUSH &&
                                    !Switches.scoutScanComplete && Time.nowFrames() > Time.toFrames("4:30")) {
                                ActionHelper.unitCommand(cc, Abilities.EFFECT_SCAN,
                                        Position.towards(PosConstants.enemyMainBaseMidPos, PosConstants.baseLocations.get(PosConstants.baseLocations.size() - 1), 3), false);
                                Switches.scoutScanComplete = true;
                            }
                            else if (!MannerMule.doTrollMule &&
                                    GameCache.mineralBank < 3000 &&
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
                                            if (i == 2 && PosConstants.MAP.contains("Golden Wall")) { //special case so mules don't get trapped
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
                        if (UnitUtils.numScvs(true) < Math.min(Base.scvsReqForMyBases() + 10, Strategy.maxScvs)) {
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
        PosConstants.MACRO_OCS.add(cc.getPosition().toPoint2d());

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
        return !UnitUtils.isWallUnderAttack() && CannonRushDefense.isSafe && (
                        WorkerManager.numScvsPerGas == 3 ||
                        Base.scvsReqForMyBases() < Math.min(Strategy.maxScvs, UnitUtils.numScvs(true) + 5)
                );
    }

    private static void saveDyingCCs() {
        //skip if already maxed on macro OCs
        if (PosConstants.MACRO_OCS.isEmpty()) {
            return;
        }

        //loop through bases looking for a dying cc
        for (Base base : GameCache.baseList) {
            if (!base.isMyBase()) {
                continue;
            }
            Unit cc = base.getCc().unit();

            //if complete CC or incomplete PF, low health, and ground-attacking enemy nearby
            if ((cc.getType() == Units.TERRAN_COMMAND_CENTER || cc.getType() == Units.TERRAN_ORBITAL_COMMAND) &&
                    cc.getBuildProgress() == 1.0f &&
                    UnitUtils.getHealthPercentage(cc) < Strategy.floatBaseAt &&
                    !Bot.OBS.getUnits(Alliance.ENEMY, u -> UnitUtils.getDistance(u.unit(), cc) <= 10 &&
                            UnitUtils.canAttackGround(u.unit())).isEmpty()) {
                if (ActionIssued.getCurOrder(base.getCc()).isEmpty()) {
                    FlyingCC.addFlyingCC(cc, PosConstants.MACRO_OCS.remove(0), true);

                    //remove cc from base
                    base.setCc(null);

                    //cancel PF morph in purchase queue
                    PurchaseStructureMorph.remove(cc.getTag());
                }
                //cancel PF upgrade
                else if (UnitUtils.getOrder(cc) == Abilities.MORPH_PLANETARY_FORTRESS) {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_MORPH_PLANETARY_FORTRESS, false);
                }
                //cancel PF upgrade
                else if (UnitUtils.getOrder(cc) == Abilities.MORPH_ORBITAL_COMMAND) {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_MORPH_ORBITAL, false);
                }
                //cancel scv production
                else {
                    ActionHelper.unitCommand(cc, Abilities.CANCEL_LAST, false);
                }
            }
        }
//        //send flying CCs to macro OC location
//        List<Unit> flyingCCs = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, new ArrayList<>());
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

        //stop making marines when I have 2+ factories/starports, and bunker doesn't need filling
        if (GameCache.factoryList.size() + GameCache.starportList.size() >= 2 &&
                (UnitUtils.getNatBunker().isEmpty() ||
                        UnitUtils.numMyUnits(Units.TERRAN_MARINE, true) >= Strategy.MAX_MARINES)) {
            return;
        }

        //save minerals for factory/starport production
        if (GameCache.gasBank > 75 && (UnitUtils.isAnyFactoryIdle() || UnitUtils.isAnyStarportIdle())) {
            return;
        }

        Unit barracks = GameCache.barracksList.stream()
                .filter(u -> UnitUtils.canStructureProduce(u.unit()))
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
        else if (Base.numMyBases() < 3 ||
                Strategy.gamePlan == GamePlan.TANK_VIKING ||
                Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING) {
            if (UnitUtils.canAfford(Units.TERRAN_MARINE, true) &&
                    UnitUtils.numMyUnits(Units.TERRAN_MARINE, true) < Strategy.MAX_MARINES &&
                    Bot.OBS.getFoodUsed() < 180) {
                ActionHelper.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                Cost.updateBank(Units.TERRAN_MARINE);
            }
        }
    }

    private static void buildFactoryUnitsLogic() {
        for (UnitInPool factoryUip : GameCache.factoryList) {
            Unit factory = factoryUip.unit();
            if (!UnitUtils.canStructureProduce(factory) ||
                    PurchaseUnit.contains(factoryUip)) { // || UnitUtils.getDistance(factory, LocationConstants.proxyBarracksPos) < 1
                continue;
            }

            Units unitToProduce = decideFactoryUnit();

            //get add-on if required, or if factory is idle (eg when supply blocked)
            if (factory.getAddOnTag().isEmpty() && (unitToProduce == null || UnitUtils.isTechLabRequired(unitToProduce))) {
                if (!Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                }
            }

            //purchase factory unit
            else if (unitToProduce != null && UnitUtils.canAfford(unitToProduce)) {
                ActionHelper.unitCommand(factory, Bot.OBS.getUnitTypeData(false).get(unitToProduce).getAbility().get(), false);
                Cost.updateBank(unitToProduce);
                openingFactoryUnits.remove(unitToProduce);
            }
        }
    }

    public static Units decideFactoryUnit() {
        if (Strategy.MASS_MINE_OPENER) {
            return Units.TERRAN_WIDOWMINE;
        }

        //first build hardcoded factory units
        if (!openingFactoryUnits.isEmpty()) {
            return openingFactoryUnits.get(0);
        }

        //1 hellion for every 4 zerglings / 1 adept
        if (isHellionsNeeded()) {
            return Units.TERRAN_HELLION;
        }

        //cyclone strategy (build constantly)
        if (Strategy.DO_USE_CYCLONES) {
            if (Bot.OBS.getUpgrades().contains(Upgrades.DRILL_CLAWS) &&
                    UnitUtils.canAfford(Units.TERRAN_WIDOWMINE) &&
                    UnitUtils.numMyUnits(UnitUtils.WIDOW_MINE_TYPE, true) < 3) {
                return Units.TERRAN_WIDOWMINE;
            }
            int numTanks = UnitMicroList.getUnitSubList(TankOffense.class).size();
            int numCyclones = UnitUtils.numMyUnits(Units.TERRAN_CYCLONE, true);
            if (numCyclones + numTanks < 12 && UnitUtils.canAfford(Units.TERRAN_CYCLONE)) {
                if (numTanks >= numCyclones) {
                    return Units.TERRAN_CYCLONE;
                }
                else if (UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                    return Units.TERRAN_SIEGE_TANK;
                }
            }
        }

        //offensive tank strategy (build constantly)
        if (Strategy.DO_OFFENSIVE_TANKS) {
            if (UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                return Units.TERRAN_SIEGE_TANK;
            }
        }

        //defensive tank strategy (build 2 per base)
        if (Strategy.DO_DEFENSIVE_TANKS) {
            int numTanks = Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.SIEGE_TANK_TYPE.contains(u.unit().getType())).size();
            //if tank needed for PF
            if (numTanks < Math.min(Strategy.MAX_TANKS, Strategy.NUM_TANKS_PER_EXPANSION * (Base.numMyBases() - 1))) {
                return Units.TERRAN_SIEGE_TANK;
            }
        }

        //build hellion if too gas starved for other factory units TODO: switch to mines
        if (GameCache.gasBank < 75 &&
                (PosConstants.opponentRace != Race.TERRAN || Base.numMyBases() >= 3) &&
                UnitUtils.canAfford(Units.TERRAN_HELLION) &&
                !UnitUtils.isExpansionNeeded()) {
            return Units.TERRAN_HELLION;
        }

        return null;
    }

    private static void produceTank(Unit factory) {
        ActionHelper.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
    }

    private static void produceCyclone(Unit factory) {
        ActionHelper.unitCommand(factory, Abilities.TRAIN_CYCLONE, false);
        Cost.updateBank(Units.TERRAN_CYCLONE);
    }

    private static void produceHellion(Unit factory) {
        ActionHelper.unitCommand(factory, Abilities.TRAIN_HELLION, false);
        Cost.updateBank(Units.TERRAN_HELLION);
    }

    private static boolean isHellionsNeeded() {
        switch (PosConstants.opponentRace) {
            case ZERG:
                return UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) <
                        UnitUtils.getEnemyUnitsOfType(Units.ZERG_ZERGLING).size() / 4 + 2;
            case PROTOSS:
                return !Strategy.DO_DEFENSIVE_TANKS && UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) <
                        UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_ADEPT).size();
            default:
                return false;
        }
    }

    public static void liftFactory(Unit factory) {
        //wait for factory to finish TODO: cancel if 2nd+ factory
        if (factory.getBuildProgress() != 1f) {
            return;
        }

        //cancel add-on
        if (UnitUtils.getOrder(factory) != null) {
            if (!factory.getOrders().isEmpty()) {
                ActionHelper.unitCommand(factory, Abilities.CANCEL_LAST, false);
            }
            return;
        }

        Point2d behindMainBase = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), 10);
        ActionHelper.unitCommand(factory, Abilities.LIFT, false);
        if (BunkerContain.proxyBunkerLevel == 2) {
            BunkerContain.onFactoryLift();
            if (!PosConstants._3x3AddonPosList.isEmpty()) {
                DelayedAction.delayedActions.add(
                        new DelayedAction(1, Abilities.LAND,
                                Bot.OBS.getUnit(factory.getTag()), PosConstants._3x3AddonPosList.remove(0)));
            }
        }
        else {
            DelayedAction.delayedActions.add(new DelayedAction(
                    1, Abilities.MOVE, Bot.OBS.getUnit(factory.getTag()), behindMainBase));
            //add factory positions to available starport positions
            Point2d factoryPos = factory.getPosition().toPoint2d();
            if (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, factoryPos)) { //if not proxied
                if (factory.getAddOnTag().isPresent()) {
                    PosConstants._3x3AddonPosList.add(0, factoryPos);
                }
                else {
                    PosConstants._3x3AddonPosList.add(factoryPos);
                }
            }
//            LocationConstants.STARPORTS.addAll(LocationConstants.FACTORIES);
//            LocationConstants.FACTORIES.clear();
        }
    }

    private static void trainBarracksUnitsMechAllIn() {
        for (UnitInPool barracksUip : GameCache.barracksList) {
            if (!UnitUtils.canStructureProduce(barracksUip.unit()) ||
                    !UnitUtils.isStructureAvailableForProduction(barracksUip.unit())) {
                continue;
            }

//            if (Purchase.isBuildOrderComplete() &&
//                    UnitUtils.getAddOn(barracksUip.unit()).isEmpty() &&
//                    UnitUtils.numMyUnits(Units.TERRAN_BARRACKS_REACTOR, true) == 0) {
//                if (!PurchaseStructureMorph.contains(barracksUip.unit()) &&
//                        UnitUtils.canAfford(Units.TERRAN_BARRACKS_REACTOR, true)) {
//                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_REACTOR_BARRACKS, barracksUip));
//                    Cost.updateBank(Units.TERRAN_BARRACKS_REACTOR);
//                }
//                continue;
//            }


            Units unitType = Units.TERRAN_MARINE;

            if (UnitUtils.canAfford(unitType, true) &&
                    (!Purchase.isBuildOrderComplete() || GameCache.gasBank < 125 || GameCache.mineralBank > 200)) { //save minerals for tanks
                Abilities trainCmd = (Abilities)Bot.OBS.getUnitTypeData(false).get(unitType)
                        .getAbility()
                        .orElse(Abilities.INVALID);
                ActionHelper.unitCommand(barracksUip.unit(), trainCmd, false);
                Cost.updateBank(unitType);
            }
        }
    }

    private static void trainBarracksUnits_BcRush() {
        for (UnitInPool barracksUip : GameCache.barracksList) {
            if (!UnitUtils.canStructureProduce(barracksUip.unit()) ||
                    !UnitUtils.isStructureAvailableForProduction(barracksUip.unit())) {
                continue;
            }

            if (UnitUtils.canAfford(Units.TERRAN_MARINE, true) &&
                    (UnitUtils.numMyUnits(Units.TERRAN_MARINE, true) < 2 ||
                            GameCache.mineralBank/4 > GameCache.gasBank/3)) {
                ActionHelper.unitCommand(barracksUip.unit(), Abilities.TRAIN_MARINE, false);
                Cost.updateBank(Units.TERRAN_MARINE);
            }
        }
    }

    private static void trainBarracksUnits() {
        //leave supply space for starport units
        if (Bot.OBS.getFoodUsed() >= 190 &&
                (chooseStarportUnit() != null || UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, true) < 6)) {
            return;
        }

        int numMedivacs = UnitUtils.numMyUnits(Units.TERRAN_MEDIVAC, true);
        int numRavens = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.numMyUnits(UnitUtils.VIKING_TYPE, true);
        int numScvs = UnitUtils.numMyUnits(Units.TERRAN_SCV, true);
        boolean doTrainBio = Bot.OBS.getFoodUsed() + 2 <= 200 -
                (Math.max(0, 5-numMedivacs) +
                    Math.max(0, 2-numRavens) +
                    Math.max(0, 4-numVikings)) * 2 +
                Math.max(0, Strategy.maxScvs-numScvs);

        for (UnitInPool barracksUip : GameCache.barracksList) {
            if (!UnitUtils.canStructureProduce(barracksUip.unit()) || !UnitUtils.isStructureAvailableForProduction(barracksUip.unit())) {
                continue;
            }
            Units unitType = Units.TERRAN_GHOST; //chooseBarracksUnit();

            //get add-on if required
            if (barracksUip.unit().getAddOnTag().isEmpty() && !Purchase.isAddOnQueued(barracksUip.unit()) &&
                    (Bot.OBS.getFoodUsed() > 198 || UnitUtils.isTechLabRequired(unitType))) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS, barracksUip));
                continue;
            }

            if (!doTrainBio) { //TODO: don't max without leaving space
                continue;
            }

            if (UnitUtils.canAfford(unitType)) {
                Abilities trainCmd = (Abilities)Bot.OBS.getUnitTypeData(false).get(unitType)
                        .getAbility()
                        .orElse(Abilities.INVALID);
                ActionHelper.unitCommand(barracksUip.unit(), trainCmd, false);
                Cost.updateBank(unitType);
            }
        }
    }

    //TODO: factors:
    //  -bank, bank ratio
    //  -enemy army composition (light vs armored)
    //  -enemy air units
    //  -ratio limit
    //  -want to favour ghosts for energy generation
    private static Units chooseBarracksUnit() {
//        int numGhosts = UnitUtils.numMyUnits(Units.TERRAN_GHOST, true);
//        int numMarauders = UnitUtils.numMyUnits(Units.TERRAN_MARAUDER, true);
//        int costEnemyArmored = GameCache.allEnemiesList.stream()
//                .filter(enemy -> UnitUtils.canAttack(enemy.unit()) || UnitUtils.canMove(enemy.unit()))
//                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()))
//                .filter(enemy -> Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType())
//                        .getAttributes().contains(UnitAttribute.ARMORED))
//                .map(enemy -> UnitUtils.getCost(enemy.unit()))
//                .mapToInt(cost -> (int)(cost.minerals + cost.gas * 1.2))
//                .sum();
//        int costEnemyLight = GameCache.allEnemiesList.stream()
//                .filter(enemy -> UnitUtils.canAttack(enemy.unit()) || UnitUtils.canMove(enemy.unit()))
//                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()))
//                .filter(enemy -> Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType())
//                        .getAttributes().contains(UnitAttribute.LIGHT))
//                .map(enemy -> UnitUtils.getCost(enemy.unit()))
//                .mapToInt(cost -> (int)(cost.minerals + cost.gas * 1.2))
//                .sum();
//
//        float percentEnemyArmored = costEnemyArmored / (costEnemyLight + costEnemyArmored);
//        float percentMarauders = numMarauders / (numGhosts + numMarauders);
//
//        if (percentEnemyArmored > percentMarauders) {
//            return Units.TERRAN_MARAUDER;
//        }
//        return Units.TERRAN_GHOST;

        //min marines first
        if (UnitUtils.numMyUnits(Units.TERRAN_MARINE, true) < Strategy.MAX_MARINES) {
            return Units.TERRAN_MARINE;
        }

        //no ghost tech, get marauder
        if (UnitUtils.myUnitsOfType(Units.TERRAN_GHOST_ACADEMY).isEmpty()) {
            return Units.TERRAN_MARAUDER;
        }

        if (GameCache.gasBank >= 25 && GameCache.gasBank < 125) {
            return Units.TERRAN_MARAUDER;
        }
        if (GameCache.gasBank > 600 && GameCache.gasBank < 125) {
            return Units.TERRAN_GHOST;
        }
        int numGhosts = UnitUtils.numMyUnits(Units.TERRAN_GHOST, true);
        int numMarauders = UnitUtils.numMyUnits(Units.TERRAN_MARAUDER, true);
        if (numGhosts < 3) {
            return Units.TERRAN_GHOST;
        }
        if (numMarauders * 2 < numGhosts) {
            return Units.TERRAN_MARAUDER;
        }
        return Units.TERRAN_GHOST;
    }

    private static void trainStarportUnits() { //this is specific to ghost hellbat (refactor)
        for (UnitInPool starportUip : GameCache.starportList) {
            if (!UnitUtils.canStructureProduce(starportUip.unit()) || PurchaseUnit.contains(starportUip)) {
                continue;
            }
            Units unitToProduce = chooseStarportUnit();
            if (unitToProduce == null) {
                return;
            }

            //get add-on if required
            if (starportUip.unit().getAddOnTag().isEmpty() && !Purchase.isAddOnQueued(starportUip.unit()) &&
                    (Bot.OBS.getFoodUsed() > 198 || UnitUtils.isTechLabRequired(unitToProduce))) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starportUip));
                continue;
            }

            if (UnitUtils.canAfford(unitToProduce)) {
                Abilities trainCmd = (Abilities)Bot.OBS.getUnitTypeData(false).get(unitToProduce)
                        .getAbility()
                        .orElse(Abilities.INVALID);
                ActionHelper.unitCommand(starportUip.unit(), trainCmd, false);
            }
            Cost.updateBank(unitToProduce);
        }
    }

    private static void trainStarportUnits_BcRush() { //this is specific to ghost hellbat (refactor)
        for (UnitInPool starportUip : GameCache.starportList) {
            if (!UnitUtils.canStructureProduce(starportUip.unit()) || PurchaseUnit.contains(starportUip)) {
                continue;
            }
            Units unitToProduce = Units.TERRAN_BATTLECRUISER;
            if (unitToProduce == null) {
                return;
            }

            //get add-on if required
            if (starportUip.unit().getAddOnTag().isEmpty() && !Purchase.isAddOnQueued(starportUip.unit()) &&
                    (Bot.OBS.getFoodUsed() > 198 || UnitUtils.isTechLabRequired(unitToProduce))) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starportUip));
                continue;
            }

            if (UnitUtils.canAfford(unitToProduce)) {
                Abilities trainCmd = (Abilities)Bot.OBS.getUnitTypeData(false).get(unitToProduce)
                        .getAbility()
                        .orElse(Abilities.INVALID);
                ActionHelper.unitCommand(starportUip.unit(), trainCmd, false);
            }
            Cost.updateBank(unitToProduce);
        }
    }

    private static void trainFactoryUnitsMechAllIn() {
        for (UnitInPool factoryUip : GameCache.factoryList) {
            Unit factory = factoryUip.unit();
            if (!UnitUtils.canStructureProduce(factory) || !UnitUtils.isStructureAvailableForProduction(factoryUip.unit())) {
                continue;
            }

            if (Purchase.isBuildOrderComplete() && UnitUtils.getAddOn(factoryUip.unit()).isEmpty()) {
                if (!PurchaseStructureMorph.contains(factoryUip.unit()) && UnitUtils.canAfford(Units.TERRAN_FACTORY_TECHLAB, true)) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factoryUip));
                    Cost.updateBank(Units.TERRAN_FACTORY_TECHLAB);
                }
                continue;
            }

            Units unitToProduce = chooseFactoryUnitMechAllIn();
            if (UnitUtils.canAfford(unitToProduce, true)) {
                ActionHelper.unitCommand(factory, Bot.OBS.getUnitTypeData(false).get(unitToProduce).getAbility().get(), false);
                Cost.updateBank(unitToProduce);
            }
        }
    }

    private static Units chooseFactoryUnitMechAllIn() {
        //hellion when gas starved
        if (GameCache.gasBank < 100 && GameCache.mineralBank > 200) {
            return Units.TERRAN_HELLION;
        }

//        //build thors if tank count at 6
//        int numTanks = UnitUtils.numMyUnits(UnitUtils.SIEGE_TANK_TYPE, true);
//        if (numTanks >= 6) {
//            return Units.TERRAN_THOR;
//        }

        //1 thor per tempest
        int numTempests = UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size();
        if (numTempests != 0) {
            numTempests++;
        }
        int numMyThors = UnitUtils.numMyUnits(UnitUtils.THOR_TYPE, true);
        if ((int)(numMyThors * 1.4f) < numTempests) {  //2 vs 1, 3 vs 2, 3 vs 3, 4 vs 4, 5 vs 5, 5 vs 6
            return Units.TERRAN_THOR;
        }

        //otherwise siege tank
        return Units.TERRAN_SIEGE_TANK;
    }

    private static void trainFactoryUnits() {
        //leave supply space for starport units
        if (Bot.OBS.getFoodUsed() >= 190 && chooseStarportUnit() != null) {
            return;
        }

        //when gas bank is heavy, turn off hellbat production
        if (GameCache.gasBank > 1000 &&
                GameCache.mineralBank * 3 < GameCache.gasBank &&
                Ghost.totalGhostEnergy() > 500) {
            return;
        }

        for (UnitInPool factoryUip : GameCache.factoryList) {
            Unit factory = factoryUip.unit();
            if (!UnitUtils.canStructureProduce(factory) || !UnitUtils.isStructureAvailableForProduction(factoryUip.unit())) {
                continue;
            }

            if (UnitUtils.getAddOn(factoryUip.unit()).isEmpty()) {
                Units addonType;
                Abilities addonAbility;
                if (UnitUtils.numMyUnits(Units.TERRAN_FACTORY_TECHLAB, true) == 0 &&
                        UnitUtils.numMyUnits(Units.TERRAN_FACTORY_REACTOR, true) >= 2) {
                    addonType = Units.TERRAN_FACTORY_TECHLAB;
                    addonAbility = Abilities.BUILD_TECHLAB_FACTORY;
                }
                else {
                    addonType = Units.TERRAN_FACTORY_REACTOR;
                    addonAbility = Abilities.BUILD_REACTOR_FACTORY;
                }

                if (!PurchaseStructureMorph.contains(factoryUip.unit()) && UnitUtils.canAfford(addonType)) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructureMorph(addonAbility, factoryUip));
                    Cost.updateBank(addonType);
                    if (addonType == Units.TERRAN_FACTORY_TECHLAB) {
                        KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.INFERNAL_PRE_IGNITERS));
                    }
                }
                continue;
            }

            if (UnitUtils.canAfford(Units.TERRAN_HELLION_TANK)) {
                ActionHelper.unitCommand(factory, Bot.OBS.getUnitTypeData(false).get(Units.TERRAN_HELLION_TANK).getAbility().get(), false);
                Cost.updateBank(Units.TERRAN_HELLION_TANK);
            }
        }
    }

    private static Units chooseStarportUnit() {
        //build more vikings when more needed for KillSquads
        if (!AirUnitKillSquad.getAvailableEnemyAirTargets().isEmpty() &&
                UnitUtils.numInProductionOfType(Units.TERRAN_VIKING_FIGHTER) == 0) {
            return Units.TERRAN_VIKING_FIGHTER;
        }

        //maintain 2 raven
        int numRavens = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true);
        if (numRavens < 2) {
            return Units.TERRAN_RAVEN;
        }

        //maintain 3 ravens vs burrow
        if (Switches.enemyHasCloakThreat && numRavens < 3) {
            return Units.TERRAN_RAVEN;
        }

        //maintain 1 viking
        if (UnitUtils.numMyUnits(Units.TERRAN_VIKING_FIGHTER, true) < 1) {
            return Units.TERRAN_VIKING_FIGHTER;
        }

//        //maintain 2 ravens for creep when not defending
//        if (ArmyManager.doOffense && numRavens <= 2) {
//            return Units.TERRAN_RAVEN;
//        }

        //maintain at least 1 medivac per 3 bio units unless floating medivac energy
        float totalMedivacEnergy = (float) Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_MEDIVAC).stream()
                .mapToDouble(u -> u.unit().getEnergy().orElse(0f))
                .sum();
        if (totalMedivacEnergy < 800) { //forget unit ratio is medivacs are banking energy
            int numMarauders = UnitUtils.numMyUnits(Units.TERRAN_MARAUDER, true);
            int numHellbats = UnitUtils.numMyUnits(Units.TERRAN_HELLION_TANK, true);
            int numGhosts = UnitUtils.numMyUnits(Units.TERRAN_GHOST, true);
            int numMedivacs = UnitUtils.numMyUnits(Units.TERRAN_MEDIVAC, true);
            if (numMedivacs * 3.5f < numMarauders + numGhosts + numHellbats) {
                return Units.TERRAN_MEDIVAC;
            }
        }

        //build medivac if existing medivacs are dry
        if (totalMedivacEnergy / UnitUtils.numMyUnits(Units.TERRAN_MEDIVAC, false) < 20) {
            return Units.TERRAN_MEDIVAC;
        }

        //maintain 4 ravens when viking/medivac quota is met
        if (ArmyManager.doOffense && numRavens <= 2) {
            return Units.TERRAN_RAVEN;
        }

        //keep building ravens if gas starts to bank up
        if (GameCache.gasBank > 800 && GameCache.gasBank > GameCache.mineralBank * 2) {
            return Units.TERRAN_RAVEN;
        }

        return null;
    }

    private static void buildStarportUnitsLogic() {
        for (UnitInPool starportUip : GameCache.starportList) {
            if (!UnitUtils.canStructureProduce(starportUip.unit()) || PurchaseUnit.contains(starportUip)) {
                continue;
            }
            Abilities unitToProduce = (Strategy.gamePlan == GamePlan.TANK_VIKING ||
                    Strategy.gamePlan == GamePlan.ONE_BASE_TANK_VIKING ||
                    (Strategy.gamePlan == GamePlan.BUNKER_CONTAIN_STRONG && PosConstants.opponentRace == Race.TERRAN)) ?
                            tankVikingDecideStarportUnit() :
                            decideStarportUnit();
            if (unitToProduce == null) {
                return;
            }
            Units unitType = Bot.abilityToUnitType.get(unitToProduce);
            //get add-on if required
            if (starportUip.unit().getAddOnTag().isEmpty() && !Purchase.isAddOnQueued(starportUip.unit()) &&
                    (Bot.OBS.getFoodUsed() >= 198 || UnitUtils.isTechLabRequired(unitType))) {
                KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starportUip));
            }
            else if (UnitUtils.canAfford(unitType)) {
                if (unitToProduce == Abilities.TRAIN_MEDIVAC) {
                    Chat.tag("MEDIVAC");
                }
                ActionHelper.unitCommand(starportUip.unit(), unitToProduce, false);
                if (!openingStarportUnits.isEmpty()) {
                    openingStarportUnits.remove(0);
                }
                Cost.updateBank(unitType);
            }
        }
    }

    private static boolean isCloakInProduction() {
        return UnitUtils.myUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .anyMatch(techLab -> UnitUtils.getOrder(techLab) == Abilities.RESEARCH_BANSHEE_CLOAKING_FIELD);
    }

    public static Abilities tankVikingDecideStarportUnit() { //never max out without a raven
        //first build hardcoded starport units
        if (!openingStarportUnits.isEmpty()) {
            return (Abilities)Bot.OBS.getUnitTypeData(false).get(openingStarportUnits.get(0)).getAbility().orElse(Abilities.INVALID);
        }

        int numRavens = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.numMyUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int vikingsRequired = numVikingsInTvT();

        //never max out without a raven
        if (Bot.OBS.getFoodUsed() >= 196 && numRavens == 0) {
            return Abilities.TRAIN_RAVEN;
        }

        //when enemy does banshee harass, maintain raven-viking-viking
        if (Strategy.ENEMY_DOES_BANSHEE_HARASS) {
            if (numRavens < 1) {
                return Abilities.TRAIN_RAVEN;
            }
            else if (numVikings < 2) {
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

        if (MedivacScvHealer.needAnother()) {
            return Abilities.TRAIN_MEDIVAC;
        }

        //get a solid base viking count if tank count is large
        if (numRavens >= 2 && numVikings < UnitMicroList.numOfUnitClass(TankOffense.class)) {
            return Abilities.TRAIN_VIKING_FIGHTER;
        }

        //otherwise raven
        return (Strategy.DEFAULT_STARPORT_UNIT == Abilities.TRAIN_BANSHEE &&
                GameCache.bansheeList.size() >= Strategy.MAX_BANSHEES) ?
                        Abilities.TRAIN_RAVEN :
                        Strategy.DEFAULT_STARPORT_UNIT;
    }



    public static Abilities decideStarportUnit() {
        //first build hardcoded starport units
        if (!openingStarportUnits.isEmpty()) {
            return (Abilities)Bot.OBS.getUnitTypeData(false).get(openingStarportUnits.get(0)).getAbility().orElse(Abilities.INVALID);
        }

        int numBanshees = UnitUtils.numMyUnits(Units.TERRAN_BANSHEE, true);
        int numRavens = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.numMyUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int numCyclones = UnitUtils.numMyUnits(Units.TERRAN_CYCLONE, true);
        int numTanks = UnitUtils.numMyUnits(UnitUtils.SIEGE_TANK_TYPE, true);
        int numLiberators = UnitUtils.numMyUnits(UnitUtils.LIBERATOR_TYPE, true);
        int vikingsRequired = ArmyManager.calcNumVikingsNeeded();
        int ravensRequired = (PosConstants.opponentRace == Race.ZERG) ? 4 : 1;

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

        //maintain 2+ ravens if an expansion needs clearing
        if (numRavens < 2 && !ExpansionClearing.expoClearList.isEmpty()) {
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
        if (numBanshees < Strategy.MIN_BANSHEES) {
            return Abilities.TRAIN_BANSHEE;
        }

        if (PosConstants.opponentRace == Race.ZERG) {
            //maintain a raven count of 2 vs zerg
            if (numRavens < 2) {
                return Abilities.TRAIN_RAVEN;
            }

            //maintain a viking count of 1 vs zerg
            if (numVikings < 1) {
                return Abilities.TRAIN_VIKING_FIGHTER;
            }
        }

        //get defensive liberators for each expansion up to 6
        if (Strategy.DO_DEFENSIVE_LIBS &&
                numLiberators < Math.min(Strategy.MAX_LIBS, Strategy.NUM_LIBS_PER_EXPANSION * (Base.numMyBases() - 1)) &&
                !freeUpOffensiveLib()) {
            return Abilities.TRAIN_LIBERATOR;
        }

        //get 1 raven for observers
        if (numRavens == 0 && numBanshees > 0 && numVikings > 0 && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
            return Abilities.TRAIN_RAVEN;
        }

        if (MedivacScvHealer.needAnother()) {
            return Abilities.TRAIN_MEDIVAC;
        }

        //get required ravens after army has 15 core units
        if (numRavens < ravensRequired && numBanshees + numVikings + numCyclones + numTanks >= 15) {
            return Abilities.TRAIN_RAVEN;
        }

        //otherwise banshee
        return (Strategy.DEFAULT_STARPORT_UNIT == Abilities.TRAIN_BANSHEE && GameCache.bansheeList.size() >= Strategy.MAX_BANSHEES) ?
            Abilities.TRAIN_RAVEN :
            Strategy.DEFAULT_STARPORT_UNIT;
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
        //purchase new CCs at 500minerals unless nearing full saturation (in which case 400mins)
        int mineralsRequired = 500;
        if (UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_COMMAND_CENTER) == 0 &&
                UnitUtils.numScvs(true) >= Math.min(Strategy.maxScvs,
                        Base.scvsReqForMyBases() - (4 * UnitUtils.numMyUnits(UnitUtils.COMMAND_CENTER_TYPE, false)))) {
            mineralsRequired = 400;
        }

        if (GameCache.mineralBank > mineralsRequired && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) &&
                (Base.numMyBases() < PosConstants.baseLocations.size() - Strategy.NUM_DONT_EXPAND ||
                        !PosConstants.MACRO_OCS.isEmpty() ||
                        !Placement.possibleCcPosList.isEmpty())) {
            if ((GameCache.mineralBank > GameCache.gasBank && GameCache.gasBank > 2000) ||
                    Base.numAvailableBases() > 0 ||
                    UnitUtils.numMyUnits(UnitUtils.ORBITAL_COMMAND_TYPE, true) < Strategy.MAX_OCS) {
                addCCToPurchaseQueue();
                if (UnitUtils.numMyUnits(Units.TERRAN_ENGINEERING_BAY, true) == 0 &&
                        UnitUtils.numMyUnits(UnitUtils.COMMAND_CENTER_TYPE, true) + 1 == Strategy.NUM_BASES_TO_OC) {
                    KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
            }
        }
    }

    private static void buildNuke() {
        if (GameCache.factoryList.isEmpty() ||
                !Bot.OBS.getUpgrades().contains(Upgrades.PERSONAL_CLOAKING) ||
                !UnitUtils.canAfford(Units.TERRAN_NUKE) ||
                UnitUtils.isNukeAvailable()) {
            return;
        }

        UnitUtils.myUnitsOfType(Units.TERRAN_GHOST_ACADEMY).stream()
                .findFirst()
                .ifPresent(academy -> {
                    if (ActionIssued.getCurOrder(academy).isEmpty()) {
                        ActionHelper.unitCommand(academy, Abilities.BUILD_NUKE, false);
                        Cost.updateBank(Units.TERRAN_NUKE);
                    }
                });
    }
    private static void buildFactoryLogic() {
        if (UnitUtils.numMyUnits(UnitUtils.FACTORY_TYPE, true) < 2 &&
                Base.numMyBases() >= 3 &&
                UnitUtils.canAfford(Units.TERRAN_FACTORY) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_FACTORY) &&
                isAllProductionStructuresActive(Units.TERRAN_FACTORY)) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
            return;
        }

        if (UnitUtils.numMyUnits(UnitUtils.FACTORY_TYPE, true) < 3 &&
                Base.numMyBases() >= 4 &&
                UnitUtils.canAfford(Units.TERRAN_FACTORY) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_FACTORY) &&
                isAllProductionStructuresActive(Units.TERRAN_FACTORY) &&
                Ghost.totalGhostEnergy() > 600) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY));
            return;
        }
    }

    private static void buildStarportLogicForGhostMarauder() {
        if (UnitUtils.numMyUnits(UnitUtils.STARPORT_TYPE, true) < 2 &&
                Base.numMyBases() >= 4 &&
                UnitUtils.canAfford(Units.TERRAN_STARPORT) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_STARPORT) &&
                isAllProductionStructuresActive(Units.TERRAN_STARPORT)) {
            KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        }
    }

    private static void buildStarportLogic() {
        if (!PosConstants._3x3AddonPosList.isEmpty() &&
                UnitUtils.canAfford(Units.TERRAN_STARPORT) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_STARPORT)) {
            if (Bot.OBS.getFoodUsed() > 197 ||
                    (UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_STARPORT) < 3 && isAllProductionStructuresBusy())) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
            }
        }
    }

    private static void buildBarracksLogic() {
        if (UnitUtils.numMyUnits(UnitUtils.BARRACKS_TYPE, true) >= 8) { //TODO: hack to test starports
            return;
        }
        if (!PosConstants._3x3AddonPosList.isEmpty() &&
                UnitUtils.canAfford(Units.TERRAN_BARRACKS) &&
                !PurchaseStructure.isTechRequired(Units.TERRAN_BARRACKS)) {
            if (Bot.OBS.getFoodUsed() > 198 ||
                    (UnitUtils.numStructuresProducingOrQueued(Units.TERRAN_BARRACKS) <= 1 &&
                            isAllProductionStructuresActive(Units.TERRAN_BARRACKS))) {
                KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
            }
        }
    }

    public static boolean isAllProductionStructuresBusy() {
        return isAllProductionStructuresActive(Units.TERRAN_STARPORT) &&
                (isAllProductionStructuresActive(Units.TERRAN_FACTORY) || doPrioritizeStarportUnits());
    }

    //not busy structure = idle || 50%+ done building a tech lab || 80%+ done build a unit
    private static boolean isAllProductionStructuresActive(Units structureType) {
        return Bot.OBS.getUnits(Alliance.SELF, u ->
                u.unit().getType() == structureType && (
                        u.unit().getOrders().isEmpty() || (
                                u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB &&
                                UnitUtils.getAddOn(u.unit()).stream().anyMatch(addOn -> addOn.unit().getBuildProgress() > 0.6f)
                        ) ||
                        u.unit().getOrders().get(0).getProgress().orElse(0f) > 0.8f
                )).isEmpty();
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
            int scvsForMaxSaturation = Base.scvsReqForMyBases();
            int numScvs = UnitUtils.numScvs(true);
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

    public static Point2d getNextAvailableExpansionPosition() {
        List<Base> expansionOptions = GameCache.baseList.subList(0, GameCache.baseList.size() - getNumEnemyBasesIgnored()).stream()
                .filter(base -> base.isUntakenBase() &&
                        !base.isDriedUp() &&
                        InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, base.getCcPos()) == 0 &&
                        base.isReachable())
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
        return (PosConstants.MACRO_OCS.isEmpty()) ? 2 : 5; //try to expand deeper on enemy side when macro OCs are complete
    }

    public static boolean purchaseMacroCC() {
        if (PosConstants.MACRO_OCS.isEmpty()) {
            return false;
        }
        KetrocBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, PosConstants.MACRO_OCS.remove(0)));
        return true;
    }

    public static boolean ccToBeOC(Point2d ccPos) {
        return PosConstants.baseLocations
                .subList(Strategy.NUM_BASES_TO_OC, PosConstants.baseLocations.size()) //ignore OC base locations
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
        return (int)(Math.min(Base.numMyBases(), Math.max(0, Strategy.maxScvs - UnitUtils.numScvs(true))) * 2.34 + //scvs (2.34 cuz 1 supply * 1/2 build time of depot)
                UnitUtils.myUnitsOfType(Units.TERRAN_STARPORT).size() * (Strategy.gamePlan == GamePlan.BC_RUSH ? 5 : 2.34) +
                UnitUtils.myUnitsOfType(Units.TERRAN_FACTORY).size() * 3.34 +
                (Strategy.gamePlan == GamePlan.GHOST_HELLBAT || Strategy.gamePlan == GamePlan.MECH_ALL_IN ? UnitUtils.myUnitsOfType(Units.TERRAN_BARRACKS).size() * 3.34 : 0));
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
        float xEnemy = PosConstants.baseLocations.get(PosConstants.baseLocations.size() - 1).getX();
        float yEnemy = PosConstants.baseLocations.get(PosConstants.baseLocations.size() - 1).getY();
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

    public static void endCycloneProduction() {
        GameCache.factoryList.forEach(factory -> {
            if (UnitUtils.getOrder(factory.unit()) != null) {
                ActionHelper.unitCommand(factory.unit(), Abilities.CANCEL, false);
            }
        });
    }
}