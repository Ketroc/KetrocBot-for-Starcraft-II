package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.github.ocraft.s2client.protocol.observation.Result;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.managers.*;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.*;
import com.ketroc.terranbot.strategies.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Bot extends S2Agent {
    public static ActionInterface ACTION;
    public static ObservationInterface OBS;
    public static QueryInterface QUERY;
    public static DebugInterface DEBUG;

    public static LinkedList<Purchase> purchaseQueue = new LinkedList<Purchase>();
    public static Map<Abilities, Units> abilityToUnitType = new HashMap<>(); //TODO: move
    public static Map<Abilities, Upgrades> abilityToUpgrade = new HashMap<>(); //TODO: move
    public static boolean isDebugOn;
    public static boolean isRealTime;

    public Bot(boolean isDebugOn, boolean isRealTime) {
        this.isDebugOn = isDebugOn;
        this.isRealTime = isRealTime;
    }

    @Override
    public void onAlert(Alert alert) {

    }

    @Override
    public void onGameStart() {
        try {
            OBS = observation();
            ACTION = actions();
            QUERY = query();
            DEBUG = debug();

            //TODO: temp for TWL
            String prevGameCode = "invalid";
            try {
                prevGameCode = Files.readString(Paths.get("./data/prevResult.txt"));
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            //0-lost w bunker, 1-lost w reg, 2-won w bunker, 3-won w regular
            switch (prevGameCode) {
                case "invalid": case "1": case "2":
                    //do bunker contain
                    BunkerContain.proxyBunkerLevel = 0;
                    break;
                case "0": case "3":
                    //do regular opener
                    BunkerContain.proxyBunkerLevel = 0;
                    break;
            }
            System.out.println("========= Prev Match code: " + prevGameCode + " ==========");
            switch (BunkerContain.proxyBunkerLevel) {
                case 0:
                    System.out.println("========= Bunker Contain: off ==========");
                    break;
                case 1:
                    System.out.println("========= Bunker Contain: bunker only ==========");
                    break;
                case 2:
                    System.out.println("========= Bunker Contain: bunker/tanks/turret ==========");
                    break;
            }


            //set map
            LocationConstants.MAP = OBS.getGameInfo().getMapName();

            //set enemy race
            Set<PlayerInfo> players = OBS.getGameInfo().getPlayersInfo();
            LocationConstants.opponentRace = Race.TERRAN;
            for (PlayerInfo player : players) {
                if (player.getRequestedRace() != Race.TERRAN) {
                    LocationConstants.opponentRace = player.getRequestedRace();
                    break;
                }
            }
            //start first scv
            UnitInPool mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
            ACTION.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
            ACTION.sendActions();

            //get map, get hardcoded map locations
            LocationConstants.onGameStart(mainCC);

            //build unit lists
            GameCache.onStep();

            //set main midpoint (must be done after GameState.onStep())
            LocationConstants.setRepairBayLocation();

            //load abilityToUnitType map
            Bot.OBS.getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
                unitTypeData.getAbility().ifPresent(ability -> {
                    if (ability instanceof Abilities && unitType instanceof Units) {
                        Bot.abilityToUnitType.put((Abilities) ability, (Units) unitType);
                    }
                });
            });

            //load abilityToUpgrade map
            Bot.OBS.getUpgradeData(false).forEach((upgrade, upgradeData) -> {
                upgradeData.getAbility().ifPresent(ability -> {
                    if (ability instanceof Abilities && upgrade instanceof Upgrades) {
                        switch ((Abilities) ability) { //fix for api bug
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1;
                                break;
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2;
                                break;
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3;
                                break;
                        }
                        Bot.abilityToUpgrade.put((Abilities) ability, (Upgrades) upgrade);
                    }
                });
            });

            Strategy.onGameStart();
            BuildOrder.onGameStart();
            BunkerContain.onGameStart();

            ACTION.sendActions();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void onStep() {
        try {
            for (ChatReceived chat : Bot.OBS.getChatMessages()) {
                Chat.respondToBots(chat);
            }

            if (OBS.getGameLoop() % Strategy.SKIP_FRAMES == 0) { // && LocalDate.now().isBefore(LocalDate.of(2020, 8, 5))) {
                if (OBS.getGameLoop() == Strategy.SKIP_FRAMES) {
                    Bot.ACTION.sendChat("Last updated: Aug 24, 2020", ActionChat.Channel.BROADCAST);
                }

                //rebuild unit cache every frame
                GameCache.onStep();

                //check switches
                Switches.onStep();

                //execute actions queued to this game frame
                DelayedAction.onStep();
                DelayedChat.onStep();

                //print report of current game state
                if (Bot.OBS.getGameLoop() % 3000 == 0) { //every 5min
                    printCurrentGameInfo();
                }

                //move flying CCs
                FlyingCC.onStep();

                //update status of scvs building structures
                StructureScv.checkScvsActivelyBuilding();  //TODO: move to GameState onStep()??

                //don't build up during probe rush
                if (ProbeRushDefense.onStep()) {
                    return;
                }

                //scv rush opener
                if (!Switches.scvRushComplete) {
                    Switches.scvRushComplete = ScvRush.onStep();
                }

                CannonRushDefense.onStep();
                BunkerContain.onStep();
                Harassers.onStep();

                //clearing bases that have just dried up or died
                GameCache.baseList.stream().forEach(Base::onStep);

                //purchase from queue
                Purchase toRemove = null;
                if (Switches.tvtFastStart) {
                    Strategy.onStep_TvtFaststart();
                }
                else {
                    purchaseLoop:
                    for (int i = 0; i < purchaseQueue.size(); i++) {
                        PurchaseResult result = purchaseQueue.get(i).build();
                        switch (result) {
                            case SUCCESS:
                                toRemove = purchaseQueue.get(i); // delay removal of purchase so that the other onStep()s don't add it back
                                break purchaseLoop;
                            case WAITING:
                                break;
                            case CANCEL:
                                if (toRemove instanceof PurchaseStructure) {
                                    System.out.println(((PurchaseStructure)toRemove).getStructureType() + " failed to build at: " + ((PurchaseStructure)toRemove).getPosition());
                                }
                                purchaseQueue.remove(i--);
                                break;
                        }
                    }
                }

                //TODO: order to spend should be workers, units, build queue, structures
                //Strategy.onStep(); //effect game strategy
                UpgradeManager.onStep();
                BuildManager.onStep(); //build structures TODO: split into Structure and Unit Managers, then move Unit Manager above purchase loop
                WorkerManager.onStep(); //fix workers, make refineries
                ArmyManager.onStep(); //decide army movements
                LocationConstants.onStep(); //manage which enemy base to target

                purchaseQueue.remove(toRemove);

                if (isDebugOn) {
                    int lines = 0;
//                    DEBUG.debugTextOut("Cannon Rushed: " + (CannonRushDefense.cannonRushStep != 0), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                    DEBUG.debugTextOut("Safe to Expand: " + CannonRushDefense.isSafe, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("banshees: " + GameCache.bansheeList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("liberators: " + GameCache.liberatorList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("ravens: " + GameCache.ravenList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("vikings: " + GameCache.vikingList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    if (LocationConstants.opponentRace == Race.PROTOSS) {
                        DEBUG.debugTextOut("tempests: " + GameCache.allEnemiesMap.getOrDefault(Units.PROTOSS_TEMPEST, Collections.emptyList()).size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }

                    UnitInPool tempest = UnitUtils.getClosestEnemyUnitOfType(Units.PROTOSS_TEMPEST, ArmyManager.retreatPos);
                    if (tempest != null) {
                        DEBUG.debugTextOut("vikings near tempest: " + UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, tempest.unit().getPosition().toPoint2d(), Strategy.DIVE_RANGE).size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }

                    DEBUG.debugTextOut("vikings wanted: " + ArmyManager.calcNumVikingsNeeded()*0.7, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("Purchase Queue: " + Bot.purchaseQueue.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("BaseTarget: " + LocationConstants.baseAttackIndex, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    DEBUG.debugTextOut("Switches.enemyCanProduceAir: " + Switches.enemyCanProduceAir, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    if (ArmyManager.attackPos != null) {
                        int x = (int) ArmyManager.attackPos.getX();
                        int y = (int) ArmyManager.attackPos.getY();
                        DEBUG.debugBoxOut(Point.of(x - 0.3f, y - 0.3f, Position.getZ(x, y)), Point.of(x + 0.3f, y + 0.3f, Position.getZ(x, y)), Color.YELLOW);
                    }
                    for (int i = 0; i < purchaseQueue.size() && i < 5; i++) {
                        DEBUG.debugTextOut(Bot.purchaseQueue.get(i).getType(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }
                    Bot.DEBUG.debugBoxOut(Point.of(LocationConstants.enemyMineralPos.getX()-0.33f, LocationConstants.enemyMineralPos.getY()-0.33f, Position.getZ(LocationConstants.enemyMineralPos)), Point.of(LocationConstants.enemyMineralPos.getX()+0.33f, LocationConstants.enemyMineralPos.getY()+0.33f, Position.getZ(LocationConstants.enemyMineralPos)), Color.BLUE);
                    Bot.DEBUG.sendDebug(); //draw the screen debug
                }
                Bot.ACTION.sendActions();
            }
        }
        catch (Exception e) {
            System.out.println("Bot.onStep() error At game frame: " + OBS.getGameLoop());
            e.printStackTrace();
        }
    } // end onStep()

    private void printCurrentGameInfo() {
        System.out.println("\n\nGame info at " + convertGameLoopToStringTime(Bot.OBS.getGameLoop()));
        System.out.println("===================\n");
        System.out.println("GameState.liberatorList.size() = " + GameCache.liberatorList.size());
        System.out.println("GameState.siegeTankList.size() = " + GameCache.siegeTankList.size());
        System.out.println("GameState.vikingList.size() = " + GameCache.vikingList.size());
        System.out.println("GameState.bansheeList.size() = " + GameCache.bansheeList.size());
        System.out.println("Strategy.DO_INCLUDE_LIBS = " + Strategy.DO_INCLUDE_LIBS);
        System.out.println("Strategy.DO_INCLUDE_TANKS = " + Strategy.DO_INCLUDE_TANKS);
        System.out.println("Strategy.maxScvs = " + Strategy.maxScvs);
        System.out.println("Switches.enemyCanProduceAir = " + Switches.enemyCanProduceAir);
        System.out.println("Switches.phoenixAreReal = " + Switches.phoenixAreReal);
        System.out.println("Switches.isDivingTempests = " + Switches.isDivingTempests);
        System.out.println("Switches.includeTanks = " + Switches.includeTanks);
        System.out.println("Switches.vikingDiveTarget == null? = " + Boolean.valueOf(Switches.vikingDiveTarget == null).toString());
        System.out.println("Switches.bansheeDiveTarget == null? = " + Boolean.valueOf(Switches.bansheeDiveTarget == null).toString());
        System.out.println("UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER) = " + UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER));
        System.out.println("UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST) = " + UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST));
        System.out.println("LocationConstants.STARPORTS.toString() = " + LocationConstants.STARPORTS.toString());
        System.out.println("LocationConstants.MACRO_OCS.toString() = " + LocationConstants.MACRO_OCS.toString());
        System.out.println("UpgradeManager.shipArmor.toString() = " + UpgradeManager.shipArmor.toString());
        System.out.println("UpgradeManager.shipAttack.toString() = " + UpgradeManager.shipAttack.toString());
        System.out.println("Bot.purchaseQueue.size() = " + Bot.purchaseQueue.size());
        System.out.println("\n\n");
        for (int i=0; i<GameCache.baseList.size(); i++) {
            Base base = GameCache.baseList.get(i);
            System.out.println("\nBase " + i);
            if (base.isMyBase()) {
                System.out.println("isMyBase");
            }
            if (base.isEnemyBase) {
                System.out.println("isEnemyBase");
            }
            if (base.isUntakenBase()) {
                System.out.println("isUntakenBase()");
            }
            System.out.println("base.isDryedUp() = " + base.isDryedUp());
            System.out.println("isPlaceable(base.getCcPos(), Abilities.BUILD_COMMAND_CENTER) = " + BuildManager.isPlaceable(base.getCcPos(), Abilities.BUILD_COMMAND_CENTER));
            System.out.println("base.lastScoutedFrame = " + base.lastScoutedFrame);
            System.out.println("Bot.OBS.getVisibility(base.getCcPos()).toString() = " + Bot.OBS.getVisibility(base.getCcPos()).toString());
        }
        System.out.println("\n\n");
    }


    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        try {
            Unit unit = unitInPool.unit();
            System.out.println(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() + ") at: " + currentGameTime());


            if (unit.getType() instanceof Units) {
                Units unitType = (Units) unit.getType();

                //remove scv that built it from the tracking list
                StructureScv.removeScvFromList(unitInPool.unit());

                switch (unitType) {
                    case TERRAN_BARRACKS:
                        if (BunkerContain.proxyBunkerLevel != 0) {
                            BunkerContain.onBarracksComplete();
                        }
                        else {
                            //set rally
                            Point2d barracksRally;
                            Point2d bunkerPos = Purchase.getPositionOfQueuedStructure(Units.TERRAN_BUNKER);
                            if (bunkerPos != null) {
                                barracksRally = Position.towards(bunkerPos, unit.getPosition().toPoint2d(), 2f);
                            } else {
                                barracksRally = LocationConstants.insideMainWall;
                            }
                            Bot.ACTION.unitCommand(unit, Abilities.SMART, barracksRally, false);

                            //queue tech lab if marauders needed
                            if (Strategy.ANTI_NYDUS_BUILD) {
                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS, unit));
                            }
                        }

                        //get OC
                        if (GameCache.baseList.get(0).getCc().map(UnitInPool::unit).map(Unit::getType).orElse(Units.INVALID) == Units.TERRAN_COMMAND_CENTER) {
                            if (BunkerContain.proxyBunkerLevel == 2) {
                                purchaseQueue.add(purchaseQueue.size()-2, new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc().get())); //TODO: only first time (or only if base isn't OC already)
                            }
                            else {
                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc().get()));
                            }
                        }

                        //queue factory
                        if (GameCache.factoryList.isEmpty() &&
                                !Purchase.isStructureQueued(Units.TERRAN_FACTORY) &&
                                !StructureScv.isAlreadyInProduction(Units.TERRAN_FACTORY)) {
                            //normal spot
                            if (UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_STARPORT, LocationConstants.FACTORY, 1).isEmpty()) {
                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.FACTORY));
                            }
                            //any starport spot if starport already in the factory position
                            else {
                                Strategy.DO_INCLUDE_TANKS = false; //unreliable position for tank pathing so don't make tanks
                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.STARPORTS.remove(0)));
                            }
                        }

                        break;
                    case TERRAN_BARRACKS_TECHLAB: //queue up marauders after the 2nd depot
                        Unit barracks = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BARRACKS).get(0);
                        int insertIndex = (Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) ? 1 : 0;
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        break;
                    case TERRAN_BUNKER:
                        if (UnitUtils.getDistance(unit, LocationConstants.proxyBunkerPos) < 1) {
                            BunkerContain.onBunkerComplete();
                        }
                        else {
                            //rally bunker to inside main base wall
                            Bot.ACTION.unitCommand(unit, Abilities.SMART, LocationConstants.insideMainWall, false);

                            //load bunker with nearby marines
                            List<UnitInPool> nearbyMarines = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MARINE, unit.getPosition().toPoint2d(), 60);
                            if (!nearbyMarines.isEmpty()) {
                                Bot.ACTION.unitCommand(UnitUtils.unitInPoolToUnitList(nearbyMarines), Abilities.SMART, unit, false);
                            }
                        }
                        break;
                    case TERRAN_ENGINEERING_BAY:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onEngineeringBayComplete(unitInPool);
                        }
                        break;
                    case TERRAN_FACTORY:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onFactoryComplete();
                        }
                        else {
                            purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                        }
                        break;
                    case TERRAN_FACTORY_TECHLAB:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onTechLabComplete();
                        }
                        break;
                    case TERRAN_STARPORT:
                        Bot.ACTION.unitCommand(unit, Abilities.RALLY_BUILDING, ArmyManager.retreatPos, false);
                        break;
                    case TERRAN_SUPPLY_DEPOT:
                        ACTION.unitCommand(unit, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false); //lower depot
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        //set rally point main base mineral patch (next base if main is dry)
                        for (Base base : GameCache.baseList) {
                            if (base.isMyBase() && !base.getMineralPatches().isEmpty()) {
                                Bot.ACTION.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, base.getMineralPatches().get(0), false);
                                break;
                            }
                        }
                        break;
                    case TERRAN_PLANETARY_FORTRESS:
                        //set rally point to nearest mineral patch
                        List<UnitInPool> bigMinerals = Bot.OBS.getUnits(Alliance.NEUTRAL,
                                u -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(u.unit().getType()) && UnitUtils.getDistance(unit, u.unit()) < 10);
                        if (!bigMinerals.isEmpty()) {
                            Bot.ACTION.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, bigMinerals.get(0).unit(), false);
                        }

                        //add missile turrets to purchaseQueue
//                        if (LocationConstants.opponentRace == Race.TERRAN) {
//                            List<Point2d> turretPositions = BuildManager.calculateTurretPositions(unit.getPosition().toPoint2d());
//                            for (Point2d turretPosition : turretPositions) {
//                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPosition));
//                            }
//                        }
                        break;
                }
            }
        }
        catch (Exception e) {
            System.out.println(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
            e.printStackTrace();
        }
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
        //WorkerManager.onUnitIdle(unitInPool);
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) { //TODO: this is called for enemy player too
        try {
            Unit unit = unitInPool.unit();
            Alliance alliance = unit.getAlliance();

            //cancelled structures are handled in requeueCancelledStructure(), not here
            if (alliance == Alliance.SELF && unit.getBuildProgress() < 1) {
                return;
            }

            if (unit.getType() instanceof Units) {
                switch (alliance) {
                    case SELF:
                        switch ((Units) unit.getType()) {
                            case TERRAN_SUPPLY_DEPOT: //add this location to build new depot locations list
                                LocationConstants.extraDepots.add(unit.getPosition().toPoint2d());
                                break;
                            case TERRAN_BARRACKS: case TERRAN_ENGINEERING_BAY: case TERRAN_GHOST_ACADEMY:
                                LocationConstants._3x3Structures.add(unit.getPosition().toPoint2d());
                                purchaseQueue.addFirst(new PurchaseStructure((Units) unit.getType()));
                                break;
//turret build logic run every frame
//                            case TERRAN_MISSILE_TURRET:
//                                //rebuild turret if command center still exists
//                                if (!UnitUtils.getUnitsNearbyOfType(alliance, UnitUtils.COMMAND_CENTER_TYPE, unit.getPosition().toPoint2d(), 10).isEmpty()) {
//                                    purchaseQueue.add(new PurchaseStructure((Units) unit.getType(), unit.getPosition().toPoint2d()));
//                                }
//                                break;
                            case TERRAN_FACTORY: case TERRAN_FACTORY_FLYING:
                                if (!LocationConstants.STARPORTS.isEmpty()) { //TODO: use same location for factory
                                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.STARPORTS.get(LocationConstants.STARPORTS.size()-1)));
                                }
                                break;
                            case TERRAN_STARPORT:
                                LocationConstants.STARPORTS.add(unit.getPosition().toPoint2d());
                                break;
                            case TERRAN_SIEGE_TANK: case TERRAN_SIEGE_TANK_SIEGED:
                                //remove from base defense tank
                                for (Base base : GameCache.baseList) {
                                    for (DefenseUnitPositions tankPos : base.getTanks()) {
                                        if (unit.getTag().equals(tankPos.getUnit().map(UnitInPool::unit).map(Unit::getTag).orElse(null))) {
                                            tankPos.setUnit(null);
                                        }
                                    }
                                }
                                break;
                            case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                                //remove from base defense liberator
                                for (Base base : GameCache.baseList) {
                                    for (DefenseUnitPositions libPos : base.getLiberators()) {
                                        if (unit.getTag().equals(libPos.getUnit().map(UnitInPool::unit).map(Unit::getTag).orElse(null))) {
                                            libPos.setUnit(null);
                                        }
                                    }
                                }
                                break;
                        }
                        break;
                }
            }
        }
        catch (Exception e) {
            System.out.println(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgradeCompleted(Upgrade upgrade) {
        System.out.println(upgrade + " finished at: " + currentGameTime());

        //add to list of completed upgrades
        GameCache.upgradesCompleted.add((Upgrades)upgrade);


        switch((Upgrades)upgrade) {
            case TERRAN_BUILDING_ARMOR:
                if (!Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
                }
                break;
            case BANSHEE_CLOAK:
                purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_SPEED, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_STARPORT_TECHLAB).get(0).getTag())));
                break;
            case TERRAN_SHIP_WEAPONS_LEVEL1: case TERRAN_SHIP_WEAPONS_LEVEL2: case TERRAN_SHIP_WEAPONS_LEVEL3:
                case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
                UpgradeManager.updateUpgradeList();
//                if (!UpgradeManager.armoryUpgrades.isEmpty()) {
//                    for (Unit armory : GameCache.allFriendliesMap.get(Units.TERRAN_ARMORY)) {
//                        //if armory idle or contains this finishing upgrade
//                        if (armory.getOrders().isEmpty() || armory.getOrders().get(0).getProgress().orElse(0f) > 0.999) { //upgrade is at 0.9992-0.9993 in this hook
//                            //TODO: is checking purchase queue necessary???
////                            if (Bot.purchaseQueue.stream().noneMatch(p -> p instanceof PurchaseUpgrade && ((PurchaseUpgrade) p).getStructure().getTag().equals(armory.getTag()))) {
//                                if (!UpgradeManager.armoryUpgrades.isEmpty()) {
//                                    purchaseQueue.add(new PurchaseUpgrade(UpgradeManager.armoryUpgrades.remove(0), Bot.OBS.getUnit(armory.getTag())));
//                                    break;
//                                }
////                            }
//                        }
//                    }
//                }
                break;
        }

    }

    @Override
    public void onUnitEnterVision(UnitInPool unitInPool) {
//        try {
//            if (unitInPool.unit().getAlliance() == Alliance.ENEMY  && unitInPool.unit().getType() instanceof Units) {
//                switch ((Units) unitInPool.unit().getType()) {
//                    case ZERG_OVERLORD:
//                        break;
//                    default:
//                        //if this unit is not significantly further away from my main base, make it the new target
//                        if (ArmyManager.attackPos == null || GameState.baseList.get(0).getCcPos().distance(unitInPool.unit().getPosition().toPoint2d()) < GameState.baseList.get(0).getCcPos().distance(ArmyManager.attackPos)) {
//                            ArmyManager.attackPos = unitInPool.unit().getPosition().toPoint2d();
//                            Switches.isDefending = true;
//                            break;
//                        }
//                }
//            }
//        }
//        catch (Exception e) {
//            System.out.println(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
//            e.printStackTrace();
//        }
    }

    @Override
    public void onNydusDetected() {
        GameResult.setNydusRushed(); //TODO: temp for Spiny
    }

    @Override
    public void onNuclearLaunchDetected() {

    }

    @Override
    public void onGameEnd() {
        Result result = Bot.OBS.getResults().stream()
                .filter(playerResult -> playerResult.getPlayerId() == Bot.OBS.getPlayerId())
                .findFirst()
                .get()
                .getResult();
        String resultCode;
        //0 - loss + bunker, 1 - loss w reg, 2 - win + bunker, 3 - win w reg
        Path path = Paths.get("./data/prevResult.txt");
        if (!result.equals(Result.VICTORY)) {
            if (GameResult.wasNydusRushed) {
                resultCode = "0";
            }
            else {
                resultCode = "1";
            }
        }
        else {
            if (GameResult.wasNydusRushed) {
                resultCode = "2";
            }
            else {
                resultCode = "3";
            }
        }

        try {
            Files.write(path, resultCode.getBytes());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("==========================");
        System.out.println("  Result: " + resultCode);
        System.out.println("==========================");
    }

    public boolean afterTime(String time) {
        long seconds = convertStringToSeconds(time);
        return observation().getGameLoop()/22.4 > seconds;
    }

    public boolean beforeTime(String time) {
        long seconds = convertStringToSeconds(time);
        return observation().getGameLoop()/22.4 < seconds;
    }

    public long convertStringToSeconds(String time) {
        String[] arrTime = time.split(":");
        return Integer.parseInt(arrTime[0])*60 + Integer.parseInt(arrTime[1]);
    }
    public String convertGameLoopToStringTime(long gameLoop) {
        return convertSecondsToString(Math.round(gameLoop/22.4));
    }

    public String convertSecondsToString(long seconds) {
        return seconds/60 + ":" + String.format("%02d", seconds%60);
    }
    public String currentGameTime() {
        return convertGameLoopToStringTime(observation().getGameLoop());
    }

    public Unit findScvNearestBase(Unit cc) {
        return findNearestScv(cc.getPosition().toPoint2d(), true);
    }

    public Unit findNearestScv(Point2d pt, boolean isHoldingMinerals) {
        List<UnitInPool> scvList;
        scvList = observation().getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && //is scv
                ((isHoldingMinerals) ? scv.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) : true));  //is holding minerals

        UnitInPool closestScv = scvList.get(0);
        double closestDistance = pt.distance(closestScv.unit().getPosition().toPoint2d());
        scvList.remove(0);
        for (UnitInPool scv : scvList) {
            double curDistance = pt.distance(scv.unit().getPosition().toPoint2d());
            if (curDistance < closestDistance) {
                closestScv = scv;
                closestDistance = curDistance;
            }
        }

        return closestScv.unit();
    }

    public Unit findNearestScv(Point2d pt) {
        return findNearestScv(pt, false);
    }
}
