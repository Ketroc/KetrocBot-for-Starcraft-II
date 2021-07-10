package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.github.ocraft.s2client.protocol.observation.PlayerResult;
import com.github.ocraft.s2client.protocol.observation.Result;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.GameResult;
import com.ketroc.Switches;
import com.ketroc.gson.JsonUtil;
import com.ketroc.managers.*;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.*;
import com.ketroc.strategies.*;
import com.ketroc.strategies.defenses.*;
import com.ketroc.utils.*;
import com.ketroc.utils.Error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class KetrocBot extends Bot {
    public static LinkedList<Purchase> purchaseQueue = new LinkedList<Purchase>();

    public KetrocBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    @Override
    public void onAlert(Alert alert) {

    }

    @Override
    public void onGameStart() {
        try {
            //end date for bot functioning
//            if (LocalDate.now().isAfter(LocalDate.parse("2021-05-01"))) {
//                return;
//            }
            super.onGameStart();
            Print.print("opponentId = " + opponentId);

            //set map
            LocationConstants.MAP = OBS.getGameInfo().getMapName();
            System.out.println("LocationConstants.MAP = " + LocationConstants.MAP);

            //set enemy race
            LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                    .filter(playerInfo -> playerInfo.getPlayerId() != OBS.getPlayerId())
                    .findFirst()
                    .get()
                    .getRequestedRace();

            //start first scv
            UnitInPool mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
//            ActionHelper.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
//            Bot.ACTION.sendActions();

            //get map, get hardcoded map locations
            LocationConstants.onGameStart(mainCC);

            //initialize list of extra cc positions
            Placement.onGameStart();

            //choose strategy
            Strategy.onGameStart();

            DebugHelper.onGameStart();

            //build unit lists
            GameCache.onStep();

            //set main midpoint (must be done after GameState.onStep())
            LocationConstants.setRepairBayLocation();
            PlacementMap.onGameStart();

            BuildOrder.onGameStart();
            BunkerContain.onGameStart();
            MarineAllIn.onGameStart();
            WorkerRushDefense2.onGameStart();
            Base.onGameStart();

            Strategy.printStrategySettings();

            //initialize starting scvs
            OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV)
                    .forEach(scv -> Base.assignScvToAMineralPatch(scv));

            DEBUG.sendDebug();
            ACTION.sendActions();
        }
        catch (Exception e) {
            Error.onException(e);
        }
    }

    @Override
    public void onStep() {
        try {
            //************************************
            //********* DO EVERY FRAME ***********
            //************************************
            super.onStep();

            //prevent multiple runs on the same frame
            if (OBS.getGameLoop() == gameFrame) {
                Print.print("gameFrame repeated = " + gameFrame);
                return;
            }
            gameFrame = OBS.getGameLoop();

            for (ChatReceived chat : OBS.getChatMessages()) {
                if (chat.getPlayerId() != OBS.getPlayerId()) {
                    Chat.respondToBots(chat);
                }
            }

            //first step of the game
            if (Time.nowFrames() == Strategy.STEP_SIZE) {
                ACTION.sendChat("Last updated: June 30, 2021", ActionChat.Channel.BROADCAST);
                JsonUtil.chatAllWinRates();
            }

            if (Time.nowFrames() % Strategy.STEP_SIZE != 0 ||
                    (isRealTime && Time.nowFrames() < 24)) {
                return;
            }

            //************************************
            //***** DO EVERY STEPSIZE FRAME ******
            //************************************

            //TODO: delete - for testing
            if (Time.nowFrames() == Time.toFrames(60)) {
                GameCache.baseList.get(0).scvReport();
            }

            DebugHelper.onStep(); //reset debug status for printing info
            ActionIssued.onStep(); //remove saved actions that are >12 frames old
//            PlacementMap.visualizePlacementMap();
//            PlacementMap.setColumn();

            Ignored.onStep(); //free up ignored units
            EnemyScan.onStep(); //remove expired enemy scans
            GameCache.onStep(); //rebuild unit cache every frame
            GasStealDefense.onStep(); //check for early-game gas steal and respond
            ActionErrorManager.onStep(); //handle action errors like "cannot place building"
            ExpansionClearing.onStep(); //micro to clear expansion positions
            Switches.onStep(); //check switches
            DelayedAction.onStep(); //execute actions queued to this game frame
            DelayedChat.onStep(); //execute chat queued to this game frame
            FlyingCC.onStep(); //move flying CCs

            //print report of current game state
//                if (Time.nowFrames() % Time.toFrames("2:00") == 0) { //every 5min
//                    printCurrentGameInfo();
//                }



            //update status of scvs building structures
            StructureScv.checkScvsActivelyBuilding();

            //don't build up during probe rush
            if (!Strategy.WALL_OFF_IMMEDIATELY) {
                if (WorkerRushDefense.onStep()) {
                    return;
                }
            }

            //scv rush opener
            if (!Switches.scvRushComplete) {
                Switches.scvRushComplete = ScvRush.onStep();
            }


            CannonRushDefense.onStep();
            ProxyHatchDefense.onStep();
            ProxyBunkerDefense.onStep();
            BunkerContain.onStep();
            MarineAllIn.onStep();
            EnemyManager.onStep();

            //clearing bases that have just dried up or died
            GameCache.baseList.forEach(Base::onStep);
            GameCache.baseList.forEach(base -> base.onStepEnd());

            //purchase from queue
            Purchase toRemove = null;
            Strategy.onStep();
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
                                Print.print(((PurchaseStructure)toRemove).getStructureType() + " failed to build at: " + ((PurchaseStructure)toRemove).getPosition());
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
            UnitMicroList.onStep(); //do individual unit micro
            AirUnitKillSquad.onStep(); //micro anti-air kill squads
            Harassers.onStep();
            MuleMessages.onStep(); //make minimap troll messages with mules
            LocationConstants.onStep(); //manage which enemy base to target

            purchaseQueue.remove(toRemove);

            PlacementMap.setColumn();

            //TODO: DELETE - FOR TESTING
//            if (Bot.OBS.getEffects().stream()
//                    .anyMatch(effectLocations -> effectLocations.getEffect() == Effects.NUKE_PERSISTENT)) {
//                EffectLocations nukeDot = Bot.OBS.getEffects().stream()
//                        .filter(effectLocations -> effectLocations.getEffect() == Effects.NUKE_PERSISTENT)
//                        .findFirst()
//                        .orElse(null);
//            }

            if (isDebugOn) {
                displayGameInfo();
            }
            ACTION.sendActions();
        }
        catch (Exception e) {
            Print.print("Bot.onStep() error");
            Error.onException(e);
        }
    } // end onStep()

    private void displayGameInfo() {
        //                    DebugHelper.addInfoLine(DEBUG.debugTextOut("Cannon Rushed: " + (CannonRushDefense.cannonRushStep != 0), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);
//                    DEBUG.debugTextOut("Safe to Expand: " + CannonRushDefense.isSafe, Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 12);

        DebugHelper.addInfoLine("scvs/gas: " + WorkerManager.numScvsPerGas);
        DebugHelper.addInfoLine("");


        for (int i = 0; i < ExpansionClearing.expoClearList.size(); i++) {
            DebugHelper.addInfoLine("base: " + ExpansionClearing.expoClearList.get(i).expansionPos +
                    " raven: " +
                    (ExpansionClearing.expoClearList.get(i).raven != null
                            ? ExpansionClearing.expoClearList.get(i).raven.unit.unit().getPosition().toPoint2d()
                            : "none"));
        }
        DebugHelper.addInfoLine("# Scvs Ignored: " + Ignored.ignoredUnits.stream()
                .filter(ignored -> OBS.getUnit(ignored.unitTag) != null)
                .map(ignored -> OBS.getUnit(ignored.unitTag).unit().getType())
                .filter(unitType -> unitType == Units.TERRAN_SCV)
                .count());
        DebugHelper.addInfoLine("# Scvs Building: " + StructureScv.scvBuildingList.stream()
                .map(structureScv -> structureScv.getScv().unit().getType())
                .filter(unitType -> unitType == Units.TERRAN_SCV)
                .count());
        DebugHelper.addInfoLine("banshees: " + GameCache.bansheeList.size());
        DebugHelper.addInfoLine("liberators: " + GameCache.liberatorList.size());
        DebugHelper.addInfoLine("ravens: " + GameCache.ravenList.size());
        DebugHelper.addInfoLine("vikings: " + GameCache.vikingList.size());
        if (LocationConstants.opponentRace == Race.PROTOSS) {
            DebugHelper.addInfoLine("tempests: " + UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size());
        }

        UnitInPool tempest = UnitUtils.getClosestEnemyUnitOfType(Units.PROTOSS_TEMPEST, ArmyManager.retreatPos);
        if (tempest != null) {
            DebugHelper.addInfoLine("vikings near tempest: " + UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER,
                    tempest.unit().getPosition().toPoint2d(), Strategy.DIVE_RANGE).size());
        }

        DebugHelper.addInfoLine("vikings wanted: " + ArmyManager.calcNumVikingsNeeded()*0.7);
        DebugHelper.addInfoLine("Purchase Queue: " + KetrocBot.purchaseQueue.size());
        DebugHelper.addInfoLine("BaseTarget: " + LocationConstants.baseAttackIndex);
        DebugHelper.addInfoLine("Switches.enemyCanProduceAir: " + Switches.enemyCanProduceAir);
        if (ArmyManager.attackGroundPos != null) {
            DebugHelper.draw3dBox(ArmyManager.attackGroundPos, Color.YELLOW, 0.6f);
        }
        for (int i = 0; i < purchaseQueue.size() && i < 5; i++) {
            DebugHelper.addInfoLine(KetrocBot.purchaseQueue.get(i).getType());
        }

        DebugHelper.draw3dBox(LocationConstants.enemyMineralPos, Color.BLUE, 0.67f);
        DebugHelper.draw3dBox(LocationConstants.pointOnEnemyRamp, Color.GREEN, 0.5f);
        DebugHelper.draw3dBox(LocationConstants.pointOnMyRamp, Color.GREEN, 0.5f);
        DEBUG.sendDebug();
    }

    private void printCurrentGameInfo() {
        Print.print("\n\nGame info");
        Print.print("===================\n");
        Print.print("GameState.liberatorList.size() = " + GameCache.liberatorList.size());
        Print.print("GameState.siegeTankList.size() = " + GameCache.siegeTankList.size());
        Print.print("GameState.vikingList.size() = " + GameCache.vikingList.size());
        Print.print("GameState.bansheeList.size() = " + GameCache.bansheeList.size());
        Print.print("Strategy.DO_INCLUDE_LIBS = " + Strategy.DO_INCLUDE_LIBS);
        Print.print("Strategy.DO_DEFENSIVE_TANKS = " + Strategy.DO_DEFENSIVE_TANKS);
        Print.print("Strategy.DO_OFFENSIVE_TANKS = " + Strategy.DO_OFFENSIVE_TANKS);
        Print.print("Strategy.maxScvs = " + Strategy.maxScvs);
        Print.print("Switches.enemyCanProduceAir = " + Switches.enemyCanProduceAir);
        Print.print("Switches.phoenixAreReal = " + Switches.phoenixAreReal);
        Print.print("Switches.isDivingTempests = " + Switches.isDivingTempests);
        Print.print("Switches.includeTanks = " + Switches.includeTanks);
        Print.print("Switches.vikingDiveTarget == null? = " + Boolean.valueOf(Switches.vikingDiveTarget == null).toString());
        Print.print("Switches.bansheeDiveTarget == null? = " + Boolean.valueOf(Switches.bansheeDiveTarget == null).toString());
        Print.print("UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER) = " + UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER));
        Print.print("UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST) = " + UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST));
        Print.print("LocationConstants.FACTORIES.toString() = " + LocationConstants.FACTORIES.toString());
        Print.print("LocationConstants.STARPORTS.toString() = " + LocationConstants.STARPORTS.toString());
        Print.print("LocationConstants.MACRO_OCS.toString() = " + LocationConstants.MACRO_OCS.toString());
        Print.print("UpgradeManager.armoryArmorUpgrades.toString() = " + UpgradeManager.mechArmorUpgrades.toString());
        Print.print("UpgradeManager.armoryAttackUpgrades.toString() = " + UpgradeManager.airAttackUpgrades.toString());
        Print.print("BansheeBot.purchaseQueue.size() = " + KetrocBot.purchaseQueue.size());
        Print.print("\n\n");
        for (int i=0; i<GameCache.baseList.size(); i++) {
            Base base = GameCache.baseList.get(i);
            Print.print("\nBase " + i);
            if (base.isMyBase()) {
                Print.print("isMyBase");
            }
            if (base.isEnemyBase) {
                Print.print("isEnemyBase");
            }
            if (base.isUntakenBase()) {
                Print.print("isUntakenBase()");
            }
            Print.print("base.isDryedUp() = " + base.isDryedUp());
            Print.print("Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()) = " + QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()));
            Print.print("base.lastScoutedFrame = " + base.lastScoutedFrame);
            Print.print("Bot.OBS.getVisibility(base.getCcPos()).toString() = " + OBS.getVisibility(base.getCcPos()).toString());
        }
        Print.print("\n\n");
    }


    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        try {
            Unit unit = unitInPool.unit();
            Print.print(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() + ") at: " + Time.nowClock());


            if (unit.getType() instanceof Units) {
                Units unitType = (Units) unit.getType();

                //remove scv that built it from the tracking list
                StructureScv.onStructureCompleted(unitInPool.unit());

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
                            ActionHelper.unitCommand(unit, Abilities.SMART, barracksRally, false);
                        }

                        //get OC
                        if (GameCache.baseList.get(0).getCc() != null &&
                                GameCache.baseList.get(0).getCc().unit().getType() == Units.TERRAN_COMMAND_CENTER) {
                            purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                        }

                        //put factory at top of queue
                        if (UnitUtils.getNumFriendlyUnits(Units.TERRAN_FACTORY, true) == 0 && BunkerContain.proxyBunkerLevel != 2) {
                            if (GameCache.gasBank > 0) {
                                purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                            }
                            else {
                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                            }
                        }

                        break;
                    case TERRAN_BARRACKS_TECHLAB: //queue up marauders after the 2nd depot
//                        Unit barracks = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BARRACKS).get(0);
//                        int insertIndex = (Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) ? 1 : 0;
//                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
//                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        break;
                    case TERRAN_BUNKER:
                        if (BunkerContain.proxyBunkerLevel != 0 && UnitUtils.getDistance(unit, LocationConstants.proxyBunkerPos) < 1) {
                            BunkerContain.onBunkerComplete();
                        }
                        else {
                            //rally bunker to inside main base wall
                            ActionHelper.unitCommand(unit, Abilities.SMART, LocationConstants.insideMainWall, false);

                            //load bunker with nearby marines
                            List<UnitInPool> nearbyMarines = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MARINE, unit.getPosition().toPoint2d(), 60);
                            if (!nearbyMarines.isEmpty()) {
                                ActionHelper.unitCommand(UnitUtils.toUnitList(nearbyMarines), Abilities.SMART, unit, false);
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
                            //start with (1 factory + 1 starport), or start with (2 starports)
                            ActionHelper.unitCommand(unit, Abilities.SMART, LocationConstants.insideMainWall, false);
                            if (Strategy.DO_DEFENSIVE_TANKS || Strategy.DO_OFFENSIVE_TANKS || Strategy.DO_USE_CYCLONES) {
                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, unit));
                            }
//                            else if (UnitUtils.getNumFriendlyUnits(UnitUtils.STARPORT_TYPE, true) == 0) {
//                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
//                            }
                        }
                        break;
                    case TERRAN_FACTORY_TECHLAB:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onFactoryTechLabComplete();
                        }
                        //TODO: testing cyclones
                        else if (Strategy.DO_USE_CYCLONES) {
                            KetrocBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.CYCLONE_LOCK_ON_DAMAGE_UPGRADE, unitInPool));
                        }
                        else if (Time.nowFrames() < Time.toFrames("5:00")) {
                            KetrocBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, GameCache.factoryList.get(0)));
                        }
                        break;
                    case TERRAN_STARPORT:
                        ActionHelper.unitCommand(unit, Abilities.RALLY_BUILDING, ArmyManager.retreatPos, false);
                        break;
//                    case TERRAN_SUPPLY_DEPOT:
//                        ActionHelper.unitCommand(unit, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false); //lower depot
//                        break;
                    case TERRAN_COMMAND_CENTER:
                        //start mining out mineral wall
                        if (LocationConstants.MAP.contains("Golden Wall") &&
                                UnitUtils.getNumFriendlyUnits(UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN, false)
                                        >= (Strategy.MASS_RAVENS ? 3 : 4)) {
                            IgnoredMineralWallScv.addScv();
                        }
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        //set rally point main base mineral patch (next base if main is dry)
//                        for (Base base : GameCache.baseList) {
//                            if (base.isMyBase() && !base.getMineralPatchUnits().isEmpty()) {
//                                ActionHelper.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, base.getFullestMineralPatch(), false);
//                                break;
//                            }
//                        }
                        break;
                    case TERRAN_PLANETARY_FORTRESS:
                        //set rally point to nearest mineral patch
//                        List<UnitInPool> bigMinerals = Bot.OBS.getUnits(Alliance.NEUTRAL,
//                                u -> UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(u.unit().getType()) && UnitUtils.getDistance(unit, u.unit()) < 10);
//                        if (!bigMinerals.isEmpty()) {
//                            ActionHelper.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, bigMinerals.get(0).unit(), false);
//                        }

                        //send some scvs to this base so it can saturate gas when needed
                        //WorkerManager.sendScvsToNewPf(unit); //TODO: turned off cuz it doesn't use new MineralPatch objects for the scvs
                        break;
                }
            }
        }
        catch (Exception e) {
            Print.print(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
            Error.onException(e);
        }
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
        //WorkerManager.onUnitIdle(unitInPool);
    }

    @Override
    public void onUnitCreated(UnitInPool unitInPool) {
        if (Time.nowFrames() == 1) { //hack so this is never called on game start (since it does and doesn't depending on how it run)
            return;
        }

        Unit unit = unitInPool.unit();
        if (unit.getType() instanceof Units.Other) {
            System.out.println("****************************************************************");
            System.out.println("Units.Other type for in Ketrocbot.onUnitCreated:" + OBS.getUnitTypeData(false).get(unit.getType()).getName());
            System.out.println("****************************************************************");
            return;
        }

        switch ((Units)unit.getType()) {
            case TERRAN_SIEGE_TANK:
                if (BunkerContain.proxyBunkerLevel == 2) {
                    BunkerContain.onTankCreated(unitInPool);
                }
                break;
            case TERRAN_MARINE:
                if (BunkerContain.proxyBunkerLevel > 0) {
                    BunkerContain.onMarineCreated(unitInPool);
                }
                break;
            case TERRAN_SCV:
                //ignore scvs that existed the refinery as they are considered "created"
                if (OBS.getUnits(Alliance.SELF, u ->
                        UnitUtils.REFINERY_TYPE.contains(u.unit().getType()) &&
                        UnitUtils.getDistance(u.unit(), unit) < 3.5f).isEmpty()) {
                    Base.assignScvToAMineralPatch(unitInPool);
                }
                break;
        }
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
                        if (UnitUtils.isStructure(unit.getType())) {
                            PlacementMap.makeAvailable(unit);
                        }

                        switch ((Units) unit.getType()) {
                            case TERRAN_COMMAND_CENTER: //ignore CCs in enemy territory
                                Point2d enemyNatPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 2);
                                if (UnitUtils.getDistance(unit, enemyNatPos) <= Placement.MIN_DISTANCE_FROM_ENEMY_NAT){
                                    break;
                                }
                            case TERRAN_ORBITAL_COMMAND:
                                //if macro OC
                                if (GameCache.baseList.stream().noneMatch(base -> UnitUtils.getDistance(unit, base.getCcPos()) < 1)) {
                                    Placement.possibleCcPosList.add(Position.toHalfPoint(unit.getPosition().toPoint2d()));
                                }
                                break;
                            case TERRAN_SUPPLY_DEPOT: //add this location to build new depot locations list
                                LocationConstants.extraDepots.add(Position.toWholePoint(unit.getPosition().toPoint2d()));
                                if (UnitUtils.getDistance(unit, LocationConstants.WALL_2x2) < 1) {
                                    Chat.tag("main_base_breached");
                                }
                                break;
                            case TERRAN_BARRACKS: case TERRAN_ENGINEERING_BAY: case TERRAN_GHOST_ACADEMY:
                                LocationConstants._3x3Structures.add(Position.toHalfPoint(unit.getPosition().toPoint2d()));
                                purchaseQueue.addFirst(new PurchaseStructure((Units) unit.getType()));
                                break;
                            case TERRAN_FACTORY: case TERRAN_FACTORY_FLYING:
//                                if (!LocationConstants.STARPORTS.isEmpty()) { //TODO: use same location for factory
//                                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.STARPORTS.get(LocationConstants.STARPORTS.size()-1)));
//                                }
                                LocationConstants.FACTORIES.add(Position.toHalfPoint(unit.getPosition().toPoint2d()));
                                break;
                            case TERRAN_STARPORT:
                                LocationConstants.STARPORTS.add(Position.toHalfPoint(unit.getPosition().toPoint2d()));
                                break;
                            case TERRAN_SIEGE_TANK: case TERRAN_SIEGE_TANK_SIEGED:
                                //remove from base defense tank
                                for (Base base : GameCache.baseList) {
                                    for (DefenseUnitPositions tankPos : base.getTanks()) {
                                        if (tankPos.getUnit() != null && unit.getTag().equals(tankPos.getUnit().getTag())) {
                                            tankPos.setUnit(null);
                                        }
                                    }
                                }
                                break;
                            case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                                //remove from base defense liberator
                                for (Base base : GameCache.baseList) {
                                    for (DefenseUnitPositions libPos : base.getLiberators()) {
                                        if (libPos.getUnit() != null && unit.getTag().equals(libPos.getUnit().getTag())) {
                                            libPos.setUnit(null);
                                        }
                                    }
                                }
                                break;
                            case TERRAN_SCV:
                                Base.releaseScv(unit);
                        }
                        break;
                    case ENEMY:
                        //turn on trolling if I think I've won.
                        if (!MuleMessages.doTrollMule &&
                                UnitUtils.COMMAND_STRUCTURE_TYPE.contains(unit.getType()) &&
                                Bot.OBS.getFoodUsed() > 180 &&
                                Base.numEnemyBases() < 3 &&
                                UnitUtils.getEnemySupply() < 40) {
                            Chat.chatNeverRepeat(Chat.getRandomMessage(Chat.WINNING_CHAT));
                            MuleMessages.doTrollMule = true;
                        }
                }
            }
        }
        catch (Exception e) {
            Print.print(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
            Error.onException(e);
        }
    }

    @Override
    public void onUpgradeCompleted(Upgrade upgrade) {
        Print.print(upgrade + " finished at: " + Time.nowClock());

        //add to list of completed upgrades
        GameCache.upgradesCompleted.add((Upgrades)upgrade);


        switch((Upgrades)upgrade) {
            case TERRAN_BUILDING_ARMOR:
                if (!OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, OBS.getUnit(GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0).getTag())));
                }
                break;
            case LIBERATOR_AG_RANGE_UPGRADE:
                Liberator.castRange = 8;
                break;
            case TERRAN_SHIP_WEAPONS_LEVEL1: case TERRAN_SHIP_WEAPONS_LEVEL2: case TERRAN_SHIP_WEAPONS_LEVEL3:
            case TERRAN_VEHICLE_WEAPONS_LEVEL1: case TERRAN_VEHICLE_WEAPONS_LEVEL2: case TERRAN_VEHICLE_WEAPONS_LEVEL3:
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2: case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
            case BANSHEE_CLOAK: case BANSHEE_SPEED: case RAVEN_CORVID_REACTOR:
                UpgradeManager.updateUpgradeList(upgrade);
                break;
        }

    }

    @Override
    public void onUnitEnterVision(UnitInPool unitInPool) {

    }

    @Override
    public void onNydusDetected() { //called when you hear the scream
        Chat.tag("vs_Nydus");
        GameResult.setNydusRushed(); //TODO: temp for Spiny
    }

    @Override
    public void onNuclearLaunchDetected() { //called when you hear "nuclear launch detected"
        Chat.tag("vs_Nuke");
    }

    @Override
    public void onGameEnd() {
        Cyclone.cycloneKillReport();
        recordGameResult();
        Print.print("opponentId = " + opponentId);
        GameCache.allEnemiesMap.forEach((unitType, unitList) -> Print.print(unitType + ": " + unitList.size()));
        try {
            control().saveReplay(Path.of("./data/" + System.currentTimeMillis() + ".SC2Replay"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recordGameResult() {

        Result result = OBS.getResults().stream()
                .filter(playerResult -> playerResult.getPlayerId() == OBS.getPlayerId())
                .map(PlayerResult::getResult)
                .findFirst()
                .orElse(Result.VICTORY);

        if (result == Result.TIE || result == Result.UNDECIDED) {
            return;
        }

        JsonUtil.setGameResult(Strategy.gamePlan, result == Result.VICTORY);

        Path path = Paths.get("./data/prevResult.txt");
        char charResult = (result == Result.VICTORY) ? 'W' : 'L';
        try {
            String newFileText = opponentId + "~" + Strategy.gamePlan + "~" + charResult;
            String prevFileText = Files.readString(Paths.get("./data/prevResult.txt"));
            if (prevFileText.contains(opponentId)) {
                newFileText = prevFileText + "\r\n" + newFileText;
            }
            Files.write(path, newFileText.getBytes());
            Print.print("New File Text = " + newFileText);
        }
        catch (IOException e) {
            Error.onException(e);
        }
        Print.print("==========================");
        Print.print("  Result: " + result.toString());
        Print.print("==========================");
    }

}
