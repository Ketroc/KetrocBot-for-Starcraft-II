package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.strategies.CannonRushDefense;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;

public class BuildManager {
    public static final List<Abilities> BUILD_ACTIONS = Arrays.asList(
            Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER, Abilities.BUILD_STARPORT, Abilities.BUILD_SUPPLY_DEPOT,
            Abilities.BUILD_ARMORY, Abilities.BUILD_BARRACKS, Abilities.BUILD_BUNKER, Abilities.BUILD_ENGINEERING_BAY,
            Abilities.BUILD_FACTORY, Abilities.BUILD_FUSION_CORE, Abilities.BUILD_GHOST_ACADEMY, Abilities.BUILD_MISSILE_TURRET,
            Abilities.BUILD_SENSOR_TOWER
    );

    public static void onStep() {
        //cancel structure logic
        cancelStructureLogic();

        //build depot logic
        buildDepotLogic();

        //build missile turrets logic
        if (Strategy.ARCHON_SPENDING_ON) {
            buildTurretLogic();
        }

        //keep CCs active (make scvs, morph ccs, call mules)
        ccActivityLogic();

        //turn low health expansion command centers into macro OCs
        saveDyingCCs();

        //build marines
        buildBarracksUnitsLogic();

        //build siege tanks
        if (!Strategy.ARCHON_MODE) {
            if (Strategy.DO_INCLUDE_TANKS) {
                buildFactoryUnitsLogic();
            } else if (!UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_FACTORY).isEmpty()) {
                liftFactory();
            }
        }

        if (Strategy.ARCHON_SPENDING_ON) {
            //build starport units
            buildStarportUnitsLogic();

            //build starport logic
            buildStarportLogic();

            //build command center logic
            buildCCLogic();
        }
    }

    private static void cancelStructureLogic() {
        for (Unit structure : GameCache.inProductionList) {
            if (structure.getBuildProgress() < 1.0f) {
                if (UnitUtils.getHealthPercentage(structure) < 8) {
                    Bot.ACTION.unitCommand(structure, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                }
            }
        }
    }

    private static void buildDepotLogic() {
        if (GameCache.mineralBank > 100 && checkIfDepotNeeded() && !LocationConstants.extraDepots.isEmpty()) {
            Bot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }
    }

    //build 1 turret at each base except main base
    private static void buildTurretLogic() {
        //check if we need 0, 1, or 3 turrets at each base
        int turretsRequired = 0;
        if (!UnitUtils.getEnemyUnitsOfType(Units.ZERG_MUTALISK).isEmpty()) {
            turretsRequired = 3;
        }
        else if (Switches.enemyCanProduceAir || Switches.enemyHasCloakThreat) {
            turretsRequired = 1;
        }
        if (turretsRequired > 0) {
            for (Base base : GameCache.baseList) {
                if (base.isMyBase() && !base.isMyMainBase() && base.isComplete()) {
                    for (int i=0; i<turretsRequired; i++) {
                        DefenseUnitPositions turret = base.getTurrets().get(i);
                        if (turret.getUnit().isEmpty() &&
                                !Purchase.isStructureQueued(Units.TERRAN_MISSILE_TURRET, turret.getPos()) &&
                                !StructureScv.isAlreadyInProductionAt(Units.TERRAN_MISSILE_TURRET, turret.getPos())) {
                            Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turret.getPos()));
                        }
                    }
                }
            }
        }
    }

    private static void ccActivityLogic() {
        for (Unit cc : GameCache.ccList) {
            if (cc.getBuildProgress() == 1.0f && !cc.getActive().get()) {
                switch ((Units)cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition().toPoint2d())) {
                            if (UnitUtils.hasTechToBuild(Units.TERRAN_ORBITAL_COMMAND)) {

                                //if not main cc, and if needed for expansion
                                if (UnitUtils.getDistance(cc, LocationConstants.baseLocations.get(0)) > 1 && isNeededForExpansion()) {
                                    Base nextFreeBase = GameCache.baseList.stream().filter(base -> base.isUntakenBase()).findFirst().orElse(null);
                                    if (nextFreeBase == null) { //do nothing, waits for expansion to free up
                                        break;
                                    }
                                    Point2d floatTo = nextFreeBase.getCcPos();
                                    FlyingCC.addFlyingCC(cc, floatTo, false);
                                    LocationConstants.MACRO_OCS.add(cc.getPosition().toPoint2d());
                                    GameCache.baseList.stream().filter(base -> base.isUntakenBase()).findFirst().get().setCc(Bot.OBS.getUnit(cc.getTag()));

                                    //remove OC morph from purchase queue
                                    for (int i=0; i<Bot.purchaseQueue.size(); i++) {
                                        Purchase p = Bot.purchaseQueue.get(i);
                                        if (p instanceof PurchaseStructureMorph) {
                                            if (((PurchaseStructureMorph) p).getStructure().getTag().equals(cc.getTag())) {
                                                Bot.purchaseQueue.remove(i);
                                                break;
                                            }
                                        }
                                    }

                                }
                                else if (!isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                    Bot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                        else { //if base that will become a PF TODO: use same logic as OC
                            if (UnitUtils.hasTechToBuild(Units.TERRAN_PLANETARY_FORTRESS)) {
                                if (!isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                    Bot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                                }
                                break; //don't queue scv
                            }
                        }
                        //build scv
                        if (Bot.OBS.getFoodWorkers() < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                            Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        if (cc.getEnergy().get() >= Strategy.energyToMuleAt) {
                            //scan enemy base
                            if (Switches.scoutScanNow) {
                                if (!Switches.enemyCanProduceAir) {
                                    Bot.ACTION.unitCommand(cc, Abilities.EFFECT_SCAN, Position.towards(LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), LocationConstants.baseLocations.get(0), 5), false);
                                }
                                Switches.scoutScanNow = false;
                            }
                            else {
                                //calldown mule
                                boolean didMule = false;
                                for (int i = GameCache.baseList.size()-1; i >= 0; i--) {
                                    Base base = GameCache.baseList.get(i);
                                    if (base.isMyBase() && base.getCc().map(UnitInPool::unit).map(Unit::getType).orElse(Units.INVALID) == Units.TERRAN_PLANETARY_FORTRESS) {
                                        if (base.getCc().map(UnitInPool::unit).map(Unit::getBuildProgress).orElse(0f) == 1.0f && !base.getMineralPatches().isEmpty()) {
                                            Bot.ACTION.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, base.getMineralPatches().get(0), false);
                                            didMule = true;
                                            break;
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
                                            .ifPresent(nearestMineral -> Bot.ACTION.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, nearestMineral, false));
                                }
                            }
                        }
                        //no break
                    case TERRAN_PLANETARY_FORTRESS:
                        //build scv
                        if (Bot.OBS.getFoodWorkers() < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                            Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                }
            }
        }
    }

    private static boolean isNeededForExpansion() {
        //if safe and oversaturated
        return !wallUnderAttack() && CannonRushDefense.isSafe && Base.totalScvsRequiredForMyBases() < Math.min(Strategy.maxScvs, Bot.OBS.getFoodWorkers() + 5);
    }

    private static void saveDyingCCs() {
        //skip if already maxed on macro OCs
        if (LocationConstants.MACRO_OCS.isEmpty()) {
            return;
        }
        //loop through bases looking for a dying cc
        for (Base base : GameCache.baseList) { //TODO: switch to new base list
            if (!base.isMyBase()) {
                continue;
            }
            Unit cc = base.getCc().get().unit();

            //if complete CC or incomplete PF, low health, and ground attacking enemy nearby
            if (cc.getType() == Units.TERRAN_COMMAND_CENTER && cc.getBuildProgress() == 1.0f && UnitUtils.getHealthPercentage(cc) < Strategy.floatBaseAt
                    && !Bot.OBS.getUnits(Alliance.ENEMY, u -> UnitUtils.getDistance(u.unit(), cc) <= 10 && UnitUtils.doesAttackGround(u.unit())).isEmpty()) {
                if (cc.getOrders().isEmpty() && !LocationConstants.MACRO_OCS.isEmpty()) {
                    FlyingCC.addFlyingCC(cc, LocationConstants.MACRO_OCS.remove(0), true);

                    //remove cc from base
                    base.setCc(null);

                    //cancel PF morph in purchase queue
                    for (int i=0; i<Bot.purchaseQueue.size(); i++) {
                        Purchase p = Bot.purchaseQueue.get(i);
                        if (p instanceof PurchaseStructureMorph) {
                            if (((PurchaseStructureMorph) p).getStructure().getTag().equals(cc.getTag())) {
                                Bot.purchaseQueue.remove(i);
                                break;
                            }
                        }
                    }
                }
                //cancel PF upgrade
                else if (cc.getOrders().get(0).getAbility() == Abilities.MORPH_PLANETARY_FORTRESS) {
                    Bot.ACTION.unitCommand(cc, Abilities.CANCEL_MORPH_PLANETARY_FORTRESS, false);
                }
                //cancel scv production
                else {
                    Bot.ACTION.unitCommand(cc, Abilities.CANCEL_LAST, false);
                }
            }
        }
//        //send flying CCs to macro OC location
//        List<Unit> flyingCCs = GameState.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, Collections.emptyList());
//        for (Unit cc : flyingCCs) {
//            //if not on the way to land already
//            if (cc.getOrders().isEmpty()) {
//                Bot.ACTION.unitCommand(cc, Abilities.LAND, LocationConstants.MACRO_OCS.remove(LocationConstants.MACRO_OCS.size()-1), false);
//            }
//            //Bot.onUnitDestroyed() re-adds this position to MACRO_OCS if the flying cc dies
//        }
    }

    private static void buildBarracksUnitsLogic() {
        //if first barracks is idle
        if (!GameCache.barracksList.isEmpty() && !GameCache.barracksList.get(0).unit().getActive().get()) {
            Unit barracks = GameCache.barracksList.get(0).unit();

            // ============= ANTI-NYDUS MARAUDER/BARRACKS STUFF ============
            //if barracks is still set up for marauders
            if (Strategy.ANTI_NYDUS_BUILD && UnitUtils.getDistance(barracks, LocationConstants.MID_WALL_3x3) > 1) {

                //if marauders needed
                if (UnitUtils.getNumUnits(Units.TERRAN_MARAUDER, false) < 2) {
//                    if (UnitUtils.canAfford(Units.TERRAN_MARAUDER)) {
//                        Bot.ACTION.unitCommand(barracks, Abilities.TRAIN_MARAUDER, false);
//                        Cost.updateBank(Units.TERRAN_MARAUDER);
//                    }
                }
                //time to lift off barracks
                else if (barracks.getType() == Units.TERRAN_BARRACKS){
                    LocationConstants.STARPORTS.add(0, barracks.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(barracks, Abilities.LIFT, false);
                }
                //move flying barracks
                else if (barracks.getOrders().isEmpty()) {
                    Bot.ACTION.unitCommand(barracks, Abilities.LAND, LocationConstants.MID_WALL_3x3, false);
                }
            }


            else {
                //always produce marines when wall under attack

                // if no planetary at natural
                if (!GameCache.baseList.get(1).isMyBase() || !GameCache.baseList.get(1).getCc().isPresent() ||
                        GameCache.baseList.get(1).getCc().get().unit().getType() != Units.TERRAN_PLANETARY_FORTRESS) {
                    //make marines if wall under attack
                    if (wallUnderAttack()) {
                        if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                            Bot.ACTION.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                            Cost.updateBank(Units.TERRAN_MARINE);
                        }
                        return;
                    }

                    //no marines needed if early marauders were built
                    if (UnitUtils.getNumUnits(Units.TERRAN_MARAUDER, false) > 0) {
                        return;
                    }

                    //maintain early game marine count (2 for TvP/TvZ, 4 for TvT)
                    int marineCount = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).size();
                    for (Unit bunker : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER)) {
                        marineCount += bunker.getCargoSpaceTaken().orElse(0); //count marines in bunkers
                    }
                    if ((marineCount < 2 || (marineCount < 4 && LocationConstants.opponentRace == Race.TERRAN)) && Bot.OBS.getMinerals() >= 50) {
                        if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                            Bot.ACTION.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                            Cost.updateBank(Units.TERRAN_MARINE);
                        }
                    }
                }
            }
        }
    }

    private static void buildFactoryUnitsLogic() {
        if (!GameCache.factoryList.isEmpty()) {
            Unit factory = GameCache.factoryList.get(0).unit();
            if (!factory.getActive().get()) {
                if (factory.getAddOnTag().isPresent()) {
                    //2 tanks per expansion base
                    if (GameCache.siegeTankList.size() < Math.min(Strategy.MAX_TANKS, Strategy.NUM_TANKS_PER_EXPANSION * (Base.numMyBases() - 1)) &&
                            UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                        Bot.ACTION.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                }
                else if (!Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    Bot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    Bot.ACTION.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }
    }

    private static void liftFactory() {
        UnitInPool factory = GameCache.factoryList.get(0);
        if (factory.unit().getBuildProgress() == 1f) {
            if (factory.unit().getActive().orElse(true)) {
                Bot.ACTION.unitCommand(factory.unit(), Abilities.CANCEL_LAST, false);
            }
            else {
                Bot.ACTION.unitCommand(factory.unit(), Abilities.LIFT, false);
                LocationConstants.STARPORTS.add(0, factory.unit().getPosition().toPoint2d());
            }
        }
    }

    private static void buildStarportUnitsLogic() {
        for (UnitInPool starport : GameCache.starportList) {
            if (!starport.unit().getActive().get()) {
                Abilities unitToProduce = (Strategy.ARCHON_MODE) ? Abilities.TRAIN_RAVEN : decideStarportUnit();
                Units unitType = Bot.abilityToUnitType.get(unitToProduce);
                if (UnitUtils.canAfford(unitType)) {
                    if (starport.unit().getAddOnTag().isEmpty() &&
                            (unitToProduce == Abilities.TRAIN_RAVEN || unitToProduce == Abilities.TRAIN_BANSHEE || unitToProduce == Abilities.TRAIN_BATTLECRUISER) &&
                            !Purchase.isStructureQueued(Units.TERRAN_STARPORT_TECHLAB)) {
                        Bot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starport));
                    }
                    else {
                        //get cloak when 2nd banshee begins
                        if (unitToProduce == Abilities.TRAIN_BANSHEE && !Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                                UnitUtils.getNumUnits(Units.TERRAN_BANSHEE, true) == 1) {
                            if (!Purchase.isUpgradeQueued(Upgrades.BANSHEE_CLOAK) && !isCloakInProduction()) {
                                Unit availableTechLab = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream().filter(techLab -> techLab.getOrders().isEmpty()).findFirst().get();
                                Bot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_CLOAK, Bot.OBS.getUnit(availableTechLab.getTag())));
                            }
                        }
                        Bot.ACTION.unitCommand(starport.unit(), unitToProduce, false);
                        Cost.updateBank(unitType);
                    }
                }
            }
        }
    }

    private static boolean isCloakInProduction() {
        return UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream()
                .anyMatch(techLab -> !techLab.getOrders().isEmpty() && techLab.getOrders().get(0).getAbility() != Abilities.RESEARCH_BANSHEE_CLOAKING_FIELD);
    }

    public static Abilities decideStarportUnit() { //never max out without a raven
        int numBanshees = UnitUtils.getNumUnits(Units.TERRAN_BANSHEE, true);
        int numRavens = UnitUtils.getNumUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.getNumUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int numLiberators = UnitUtils.getNumUnits(Units.TERRAN_LIBERATOR, true) + UnitUtils.getNumUnits(Units.TERRAN_LIBERATOR_AG, false);
        int vikingsRequired = ArmyManager.calcNumVikingsNeeded();
        int vikingsByRatio  = (int)(numBanshees * Strategy.VIKING_BANSHEE_RATIO);
        if (vikingsByRatio > vikingsRequired) { //build vikings 1:1 with banshees until 1.5x vikings required
            vikingsRequired = Math.min((int)(vikingsRequired * 1.8), vikingsByRatio);
        }
        int ravensRequired = (LocationConstants.opponentRace == Race.ZERG) ? 2 : 1;

        //never max out without a raven
        if (Bot.OBS.getFoodUsed() >= 196 && numRavens == 0) {
            return Abilities.TRAIN_RAVEN;
        }

        //get 1 raven if enemy can produce cloaked/burrowed attackers
        if (numRavens == 0 && Switches.enemyHasCloakThreat) {
            return Abilities.TRAIN_RAVEN;
        }

        //get viking
        if (numVikings < vikingsRequired) {
            return Abilities.TRAIN_VIKING_FIGHTER;
        }

        //maintain a banshee count of 1
        if (numBanshees < 1) {
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
        if (Strategy.DO_INCLUDE_LIBS && numLiberators < Math.min(Strategy.MAX_LIBS, Strategy.NUM_LIBS_PER_EXPANSION * (Base.numMyBases()-1))) {
            return Abilities.TRAIN_LIBERATOR;
        }
        //get 1 raven for observers
        if (numRavens == 0 && numBanshees > 0 && numVikings > 0 && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
            return Abilities.TRAIN_RAVEN;
        }
        //TvZ, get raven after first banshee, then a 2nd after 15 air units.
        if (LocationConstants.opponentRace == Race.ZERG) {
            if ((numBanshees >= 1 && numRavens == 0) || (numBanshees + numVikings > 15 && numRavens < ravensRequired)) {
                return Abilities.TRAIN_RAVEN;
            }
        }
        //TvT/TvP, get a raven after 6 banshees+vikings
        else if (UnitUtils.getNumUnits(Units.TERRAN_RAVEN, true) < ravensRequired && numBanshees + numVikings >= 6) {
            return Abilities.TRAIN_RAVEN;
        }

        //otherwise banshee
        return Abilities.TRAIN_BANSHEE;
    }

    private static void buildCCLogic() {
        if (GameCache.mineralBank > 400 && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) &&
                (Base.numMyBases() < LocationConstants.baseLocations.size() - Strategy.NUM_DONT_EXPAND || !LocationConstants.MACRO_OCS.isEmpty())) {
            addCCToPurchaseQueue();

            //TODO: move this
            //build after 4th base started
            if (!Strategy.techBuilt && Base.numMyBases() >= 4) {
                Bot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0).getTag()))); //TODO: null check
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
                if (!Strategy.ARCHON_MODE) {
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
                }
                if (LocationConstants.opponentRace == Race.ZERG) {
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(0)));
                    Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(1)));
                }
                Strategy.techBuilt = true;
            }
        }
    }

    private static void buildStarportLogic() {
        if (UnitUtils.canAfford(Units.TERRAN_STARPORT) && UnitUtils.hasTechToBuild(Units.TERRAN_STARPORT) && !LocationConstants.STARPORTS.isEmpty()) {
            if (Bot.OBS.getFoodUsed() > 197 ||
                    (GameCache.inProductionMap.getOrDefault(Units.TERRAN_STARPORT, 0) < 3 && areAllProductionStructuresBusy())) {
                Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
            }
        }
    }

    private static boolean areAllProductionStructuresBusy() {
        return GameCache.starportList.stream()
                .noneMatch(u -> u.unit().getOrders().isEmpty() ||
                        u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB ||
                        u.unit().getOrders().get(0).getProgress().orElse(0f) > 0.8f) &&
                GameCache.factoryList.stream()
                        .noneMatch(u -> u.unit().getType() == Units.TERRAN_FACTORY &&
                                (u.unit().getOrders().isEmpty() ||
                                u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB ||
                                u.unit().getOrders().get(0).getProgress().orElse(0f) > 0.8f));
    }

    private static void addCCToPurchaseQueue() {
        if (Strategy.ARCHON_MODE) {
            if (!purchaseExpansionCC()) {
                purchaseMacroCC();
            }
            return;
        }

        int scvsForMaxSaturation = Base.totalScvsRequiredForMyBases();
        int numScvs = Bot.OBS.getFoodWorkers();
        if (wallUnderAttack() || !CannonRushDefense.isSafe) {
            purchaseMacroCC();
        }
        else if (Math.min(numScvs + 25, Strategy.maxScvs) <= scvsForMaxSaturation) {
            if (!purchaseMacroCC()) {
                purchaseExpansionCC();
            }
        }
        else {
            if (!purchaseExpansionCC()) {
                purchaseMacroCC();
            }
        }
    }

    private static boolean wallUnderAttack() {
        return GameCache.wallStructures.stream().anyMatch(unit -> unit.getType() == Units.TERRAN_SUPPLY_DEPOT); //if depot is raised then unsafe to expand
    }

    private static boolean purchaseExpansionCC() {
        //if an expansion position is available, build expansion CC
        Optional<Point2d> expansionPos = getNextAvailableExpansionPosition();
        if (expansionPos.isPresent()) {
            Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, expansionPos.get()));
            List<Unit> marines = GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_MARINE, Collections.emptyList());
            if (!marines.isEmpty()) {
                Bot.ACTION.unitCommand(marines, Abilities.ATTACK, expansionPos.get(), true);
            }
        }
        return expansionPos.isPresent();
    }

    private static Optional<Point2d> getNextAvailableExpansionPosition() {
        int numEnemyBasesIgnored = (LocationConstants.MACRO_OCS.isEmpty()) ? 2 : 5; //try to expand deeper on enemy side when macro OCs are complete
        return GameCache.baseList.subList(0, GameCache.baseList.size()-numEnemyBasesIgnored).stream()
                .filter(base -> base.isUntakenBase() && !base.isDryedUp() && isPlaceable(base.getCcPos(), Abilities.BUILD_COMMAND_CENTER))
                .findFirst()
                .map(Base::getCcPos);
    }

    private static boolean purchaseMacroCC() {
        if (!LocationConstants.MACRO_OCS.isEmpty()) {
            Bot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, LocationConstants.MACRO_OCS.remove(0)));
            GameCache.numMacroOCs++;
            return true;
        }
        return false;
    }

    public static boolean ccToBeOC(Point2d ccPos) {
        for (int i=1; i<LocationConstants.baseLocations.size(); i++) {
            if (ccPos.distance(LocationConstants.baseLocations.get(i)) < 1) {
                return false;
            }
        }
        return true;
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
        // TODO: decide based on current production rate rather than hardcoded 10
        if (supplyCap - curSupply + supplyInProduction() >= supplyPerProductionCycle()) { //if not nearing supply block
            return false;
        }

        return true;
    }

    private static int supplyPerProductionCycle() {
        return Math.min(Strategy.maxScvs - Bot.OBS.getFoodWorkers(), Base.numMyBases()) * 2 + //scvs (2 cuz 1 supply * 1/2 build time of depot)
                GameCache.starportList.size() * 2;
    }

    private static int supplyInProduction() {
        //include every depot
        int supply = 8 * (int) StructureScv.scvBuildingList.stream().filter(scv -> scv.buildAbility == Abilities.BUILD_SUPPLY_DEPOT).count();
        //include CCs that are 40%+ done
        for (StructureScv scv : StructureScv.scvBuildingList) {
            List<UnitInPool> cc = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_COMMAND_CENTER, scv.structurePos, 1);
            if (!cc.isEmpty() && cc.get(0).unit().getBuildProgress() > 0.4) {
                supply += 14;
            }
        }

        return supply;
    }

    public static int numStructuresQueued(Units structureType) {
        int count = 0;
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType().equals(structureType)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isMorphQueued(Abilities morphType) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getMorphOrAddOn() == morphType) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgradeQueued(Upgrades upgrade) {
        for (Purchase p : Bot.purchaseQueue) {
            if (p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getUpgrade() == upgrade) {
                return true;
            }
        }
        return false;
    }

    public static List<Point2d> calculateTurretPositions(Point2d ccPos) {//pick position away from enemy main base like a knight move (3.5x1.5)
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1).getX();
        float yEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1).getY();
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
        }
        else { //move 1x3
            xMove = 3.5f;
        }
        xTurret1 = xCC + xMove;
        xTurret2 = xCC - xMove;

        if (xDistance*yDistance > 0) {
            yTurret1 = yCC - yMove;
            yTurret2 = yCC + yMove;
        }
        else {
            yTurret1 = yCC + yMove;
            yTurret2 = yCC - yMove;
        }
        return List.of(Point2d.of(xTurret1, yTurret1), Point2d.of(xTurret2, yTurret2));
    }

    public static boolean isPlaceable(Point2d pos, Abilities buildAction) { //TODO: not perfect.  sometimes return false positive
        //if creep is there
        if (Bot.OBS.hasCreep(pos)) {
            return false;
        }
        float distance = getStructureRadius(buildAction);

        //if enemy ground unit/structure there
        return Bot.OBS.getUnits(Alliance.ENEMY,
                enemy -> enemy.unit().getPosition().toPoint2d().distance(pos) < distance &&
                        !enemy.unit().getFlying().orElse(false)).isEmpty();  //default false to handle structure snapshots
    }

    public static float getStructureRadius(Abilities buildAction) {
        StructureSize size = getSize(buildAction);
        switch (size) {
            case _1x1:
                return 0.3f;
            case _2x2:
                return 0.7f;
            case _3x3:
                return 1.1f;
            default: //_5x5
                return 2.2f;
        }

    }

    public static StructureSize getSize(Abilities buildAction) {
        switch (buildAction) {
            case BUILD_COMMAND_CENTER:
                return StructureSize._5x5;
            case BUILD_ENGINEERING_BAY: case BUILD_BARRACKS: case BUILD_BUNKER: case BUILD_ARMORY: case BUILD_FACTORY:
            case BUILD_STARPORT: case BUILD_FUSION_CORE: case BUILD_GHOST_ACADEMY:
                return StructureSize._3x3;
            case BUILD_MISSILE_TURRET: case BUILD_SUPPLY_DEPOT:
                return StructureSize._2x2;
            default: //case BUILD_SENSOR_TOWER:
                return StructureSize._1x1;
        }
    }

}
