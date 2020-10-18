package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
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
import com.ketroc.terranbot.micro.ExpansionClearing;
import com.ketroc.terranbot.micro.Harassers;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.*;
import com.ketroc.terranbot.strategies.*;
import com.ketroc.terranbot.utils.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BansheeBot extends Bot {

    public static LinkedList<Purchase> purchaseQueue = new LinkedList<Purchase>();
    public static int count1 = 0;
    public static int count2 = 0;

    public BansheeBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }


    public void onAlert(Alert alert) {

    }

    @Override
    public void onGameStart() {
        try {
            super.onGameStart();

            //set map
            LocationConstants.MAP = Bot.OBS.getGameInfo().getMapName();

            //set enemy race
            LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                    .filter(playerInfo -> playerInfo.getPlayerId() != Bot.OBS.getPlayerId())
                    .findFirst()
                    .get()
                    .getRequestedRace();

            //choose strategy
            Strategy.onGameStart();

            //start first scv
            UnitInPool mainCC = Bot.OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
            Bot.ACTION.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
            Bot.ACTION.sendActions();

            //get map, get hardcoded map locations
            LocationConstants.onGameStart(mainCC);
            DebugHelper.onGameStart();

            //build unit lists
            GameCache.onStep();

            //set main midpoint (must be done after GameState.onStep())
            LocationConstants.setRepairBayLocation();

            BuildOrder.onGameStart();
            BunkerContain.onGameStart();

//            int myId = Bot.OBS.getPlayerId();
//            int enemyId = Bot.OBS.getGameInfo().getPlayersInfo().stream()
//                    .filter(p -> p.getPlayerId() != myId)
//                    .findFirst().get().getPlayerId();
//
//            DEBUG.debugShowMap();
//            DEBUG.debugCreateUnit(Units.ZERG_HATCHERY,
//                    LocationConstants.baseLocations.get(1), enemyId, 1);
//            DEBUG.debugCreateUnit(Units.PROTOSS_ORACLE_STASIS_TRAP, LocationConstants.baseLocations.get(2), enemyId, 1);
//
//            DEBUG.debugCreateUnit(Units.ZERG_CREEP_TUMOR_BURROWED,
//                    Position.towards(LocationConstants.baseLocations.get(3), LocationConstants.enemyMainBaseMidPos, 4), enemyId, 1);
//            DEBUG.debugCreateUnit(Units.TERRAN_RAVEN, LocationConstants.baseLocations.get(0), myId, 1);
//            DEBUG.sendDebug();

            Bot.ACTION.sendActions();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStep() {
        try {
            super.onStep();
            for (ChatReceived chat : Bot.OBS.getChatMessages()) {
                Chat.respondToBots(chat);
            }

            if (Bot.OBS.getGameLoop() % Strategy.SKIP_FRAMES == 0) { // && LocalDate.now().isBefore(LocalDate.of(2020, 8, 5))) {
                if (Bot.OBS.getGameLoop() == Strategy.SKIP_FRAMES) {
                    Bot.ACTION.sendChat("Last updated: Oct 17, 2020", ActionChat.Channel.BROADCAST);
                }

                //TODO: delete - for testing
                Optional<StructureScv> first = StructureScv.scvBuildingList.stream()
                        .filter(structureScv -> structureScv.scvAddedFrame + Time.toFrames("4:30") < Bot.OBS.getGameLoop())
                        .findFirst();
                if (first.isPresent()) {
                    System.out.println("Stalled StructureScv = \n" + first);
                }

                //free up ignored units
                Ignored.onStep();

                //remove expired enemy scans
                EnemyScan.onStep();

                //rebuild unit cache every frame
                GameCache.onStep();

                //handle action errors like "cannot place building"
//                ActionErrorManager.onStep(); //TODO: turn on for tournament

                //micro to clear expansion positions
                ExpansionClearing.onStep();

                //check switches
                Switches.onStep();

                //execute actions queued to this game frame
                DelayedAction.onStep();
                DelayedChat.onStep();

                //print report of current game state
//                if (Bot.OBS.getGameLoop() % Time.toFrames("2:00") == 0) { //every 5min
//                    printCurrentGameInfo();
//                }

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
                ProxyHatchDefense.onStep();
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
                    Bot.DEBUG.debugTextOut("count1: " + count1, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("count2: " + count2, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    for (int i = 0; i < ExpansionClearing.expoClearList.size(); i++) {
                        Bot.DEBUG.debugTextOut(String.valueOf(ExpansionClearing.expoClearList.get(i).expansionPos),
                                Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                        Bot.DEBUG.debugTextOut(String.valueOf(ExpansionClearing.expoClearList.get(i).raven),
                                Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }
                    Bot.DEBUG.debugTextOut("# Scvs Ignored: " + Ignored.ignoredUnits.stream()
                            .filter(ignored -> Bot.OBS.getUnit(ignored.unitTag) != null)
                            .map(ignored -> Bot.OBS.getUnit(ignored.unitTag).unit().getType())
                            .filter(unitType -> unitType == Units.TERRAN_SCV)
                            .count(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("# Scvs Building: " + StructureScv.scvBuildingList.stream()
                            .map(structureScv -> structureScv.getScv().unit().getType())
                            .filter(unitType -> unitType == Units.TERRAN_SCV)
                            .count(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("banshees: " + GameCache.bansheeList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("liberators: " + GameCache.liberatorList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("ravens: " + GameCache.ravenList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("vikings: " + GameCache.vikingList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    if (LocationConstants.opponentRace == Race.PROTOSS) {
                        Bot.DEBUG.debugTextOut("tempests: " + GameCache.allEnemiesMap.getOrDefault(Units.PROTOSS_TEMPEST, Collections.emptyList()).size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }

                    UnitInPool tempest = UnitUtils.getClosestEnemyUnitOfType(Units.PROTOSS_TEMPEST, ArmyManager.retreatPos);
                    if (tempest != null) {
                        Bot.DEBUG.debugTextOut("vikings near tempest: " + UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, tempest.unit().getPosition().toPoint2d(), Strategy.DIVE_RANGE).size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }

                    Bot.DEBUG.debugTextOut("vikings wanted: " + ArmyManager.calcNumVikingsNeeded()*0.7, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("Purchase Queue: " + BansheeBot.purchaseQueue.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("BaseTarget: " + LocationConstants.baseAttackIndex, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("Switches.enemyCanProduceAir: " + Switches.enemyCanProduceAir, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    if (ArmyManager.attackGroundPos != null) {
                        int x = (int) ArmyManager.attackGroundPos.getX();
                        int y = (int) ArmyManager.attackGroundPos.getY();
                        Bot.DEBUG.debugBoxOut(Point.of(x - 0.3f, y - 0.3f, Position.getZ(x, y)), Point.of(x + 0.3f, y + 0.3f, Position.getZ(x, y)), Color.YELLOW);
                    }
                    for (int i = 0; i < purchaseQueue.size() && i < 5; i++) {
                        Bot.DEBUG.debugTextOut(BansheeBot.purchaseQueue.get(i).getType(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
                    }
                    Bot.DEBUG.debugBoxOut(Point.of(LocationConstants.enemyMineralPos.getX()-0.33f, LocationConstants.enemyMineralPos.getY()-0.33f, Position.getZ(LocationConstants.enemyMineralPos)), Point.of(LocationConstants.enemyMineralPos.getX()+0.33f, LocationConstants.enemyMineralPos.getY()+0.33f, Position.getZ(LocationConstants.enemyMineralPos)), Color.BLUE);
                    Bot.DEBUG.sendDebug();
                }
                Bot.ACTION.sendActions();
            }
        }
        catch (Exception e) {
            System.out.println("Bot.onStep() error at: " + Time.getTime());
            e.printStackTrace();
        }
    } // end onStep()

    private void printCurrentGameInfo() {
        System.out.println("\n\nGame info at " + Time.getTime());
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
        System.out.println("BansheeBot.purchaseQueue.size() = " + BansheeBot.purchaseQueue.size());
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
            System.out.println("Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()) = " + Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()));
            System.out.println("base.lastScoutedFrame = " + base.lastScoutedFrame);
            System.out.println("Bot.OBS.getVisibility(base.getCcPos()).toString() = " + Bot.OBS.getVisibility(base.getCcPos()).toString());
        }
        System.out.println("\n\n");
    }


    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        try {
            Unit unit = unitInPool.unit();
            System.out.println(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() + ") at: " + Time.getTime());


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
                            purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                        }

                        break;
                    case TERRAN_BARRACKS_TECHLAB: //queue up marauders after the 2nd depot
                        Unit barracks = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BARRACKS).get(0);
                        int insertIndex = (Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) ? 1 : 0;
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        break;
                    case TERRAN_BUNKER:
                        if (BunkerContain.proxyBunkerLevel != 0 && UnitUtils.getDistance(unit, LocationConstants.proxyBunkerPos) < 1) {
                            BunkerContain.onBunkerComplete();
                        }
                        else {
                            //rally bunker to inside main base wall
                            Bot.ACTION.unitCommand(unit, Abilities.SMART, LocationConstants.insideMainWall, false);

                            //load bunker with nearby marines
                            List<UnitInPool> nearbyMarines = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MARINE, unit.getPosition().toPoint2d(), 60);
                            if (!nearbyMarines.isEmpty()) {
                                Bot.ACTION.unitCommand(UnitUtils.toUnitList(nearbyMarines), Abilities.SMART, unit, false);
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
                        Bot.ACTION.unitCommand(unit, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false); //lower depot
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

                        //send some scvs to this base so it can saturate gas when needed
                        WorkerManager.sendScvsToNewPf(unit);
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
        System.out.println(upgrade + " finished at: " + Time.getTime());

        //add to list of completed upgrades
        GameCache.upgradesCompleted.add((Upgrades)upgrade);


        switch((Upgrades)upgrade) {
            case TERRAN_BUILDING_ARMOR:
                if (!Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
                }
                break;
//            case BANSHEE_CLOAK:
//                purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_SPEED, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_STARPORT_TECHLAB).get(0).getTag())));
//                break;
//            case BANSHEE_SPEED:
//                purchaseQueue.add(new PurchaseUpgrade(Upgrades.RAVEN_CORVID_REACTOR, Bot.OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_STARPORT_TECHLAB).get(0).getTag())));
//                break;
            case TERRAN_SHIP_WEAPONS_LEVEL1: case TERRAN_SHIP_WEAPONS_LEVEL2: case TERRAN_SHIP_WEAPONS_LEVEL3:
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
            case BANSHEE_CLOAK: case BANSHEE_SPEED: case RAVEN_CORVID_REACTOR:
                UpgradeManager.updateUpgradeList(upgrade);
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
    public void onNydusDetected() { //called when you hear the scream
        GameResult.setNydusRushed(); //TODO: temp for Spiny
    }

    @Override
    public void onNuclearLaunchDetected() { //called when you hear "nuclear launch detected"

    }

    @Override
    public void onGameEnd() {
        setNextGameStrategy();
        System.out.println("count1 = " + count1);
        System.out.println("count2 = " + count2);
        GameCache.allEnemiesMap.forEach((unitType, unitList) -> System.out.println(unitType + ": " + unitList.size()));
        try {
            control().saveReplay(Path.of(System.currentTimeMillis() + ".SC2Replay"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setNextGameStrategy() {
        Result result = Bot.OBS.getResults().stream()
                .filter(playerResult -> playerResult.getPlayerId() == Bot.OBS.getPlayerId())
                .findFirst()
                .get()
                .getResult();

        Path path = Paths.get("./data/prevResult.txt");
        if (result == Result.DEFEAT) {
            Strategy.selectedStrategy++;
        }
        try {
            Files.write(path, (Bot.opponentId + "~" + Strategy.selectedStrategy).getBytes());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("==========================");
        System.out.println("  Result: " + result.toString());
        System.out.println("==========================");
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
