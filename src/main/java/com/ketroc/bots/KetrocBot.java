package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrade;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.observation.PlayerResult;
import com.github.ocraft.s2client.protocol.observation.Result;
import com.github.ocraft.s2client.protocol.score.CategoryScoreDetails;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.GameResult;
import com.ketroc.Switches;
import com.ketroc.geometry.Position;
import com.ketroc.gson.JsonUtil;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.*;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.purchases.*;
import com.ketroc.strategies.*;
import com.ketroc.strategies.defenses.*;
import com.ketroc.utils.Error;
import com.ketroc.utils.*;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class KetrocBot extends Bot {
    public static LinkedList<Purchase> purchaseQueue = new LinkedList<Purchase>();

    public KetrocBot(String opponentId) {
        super(opponentId);
    }

    @Override
    public void onAlert(Alert alert) {
        try {

        }
        catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onGameStart() {
        try {
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

            UnitInPool mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);

            //get map, get hardcoded map locations
            LocationConstants.onGameStart(mainCC);

            //initialize list of extra cc positions
            Placement.onGameStart();

            //choose strategy
            Strategy.onGameStart();

            DebugHelper.onGameStart();

            //build unit lists
            GameCache.onGameStart();
            GameCache.setInitialEnemyBases();

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
            WorkerManager.sendScvsToMine(OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV));

            DEBUG.sendDebug();
            ACTION.sendActions();
        }
        catch (Throwable e) {
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

            TurretingRaven.onStepStart();

            OBS.getChatMessages().stream()
                    .filter(chat -> chat.getPlayerId() != OBS.getPlayerId())
                    .forEach(chat -> Chat.respondToBots(chat));

            //first step of the game
            if (Time.at(Launcher.STEP_SIZE)) {
                //ACTION.sendChat("Last updated: June 30, 2021", ActionChat.Channel.BROADCAST);
                JsonUtil.chatAllWinRates(true);
            }

            MyUnitAbilities.onStepStart();
            WorkerManager.onStepStart(); //fix workers, make refineries

            switch (LocationConstants.opponentRace) {
                case ZERG:
                    BileTracker.onStep();
                    break;
                case PROTOSS:
                    AdeptShadeTracker.onStep();
                    break;
                case TERRAN:
                    NukeTracker.onStep();
                    break;
            }
            //PlacementMap.visualizePlacementMap();

            Ignored.onStep(); //free up ignored units
            EnemyScan.onStep(); //remove expired enemy scans
            GameCache.onStep(); //rebuild unit cache every frame
            ActionIssued.onStep(); //remove saved actions that are >12 frames old
            OverLordHunter.manageOverlordHunter(); //send marines and barracks to clear scout overlords

//            StructureScv.updateBank(); //update bank for build commands which haven't been given yet
            GasStealDefense.onStep(); //check for early-game gas steal and respond
            ActionErrorManager.onStep(); //handle action errors like "cannot place building"
            ExpansionClearing.onStep(); //micro to clear expansion positions
            Switches.onStep(); //check switches
            DelayedAction.onStep(); //execute actions queued to this game frame
            DelayedChat.onStep(); //execute chat queued to this game frame
            FlyingCC.onStep(); //move flying CCs

            //print report of current game state
            if (Time.periodic(2)) { //every 2min
                printCurrentGameInfo();
            }

            //update status of scvs building structures
            StructureScv.checkScvsActivelyBuilding();

            //don't build up during probe rush
            WorkerRushDefense3.onStep();
//            if (!Strategy.WALL_OFF_IMMEDIATELY) { TODO: testing -turned off blind counter
////                if (WorkerRushDefense.onStep()) {
////                    return;
////                }
//                WorkerRushDefense3.onStep();
//            }
//            else {
//                WorkerRushDefense2.onStep();
//            }

            //scv rush opener
            if (!Switches.scvRushComplete) {
                Switches.scvRushComplete = ScvRush.onStep();
            }

            CannonRushDefense.onStep();
            ProxyHatchDefense.onStep();
            ScvTarget.onStep();
            ProxyBunkerDefense.onStep();
            BunkerContain.onStep();
            MarineAllIn.onStep();
            EnemyManager.onStep();
            GameCache.baseList.forEach(Base::onStep); //clearing bases that have just dried up or died
            GameCache.baseList.forEach(base -> base.onStepEnd()); //speed mining

            //purchase from queue
            Purchase toRemove = null;
            Strategy.onStep();
            if (Switches.fastDepotBarracksOpener) {
                Strategy.onStep_Faststart();
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
            ArmyManager.onStep(); //decide army movements
            UnitMicroList.onStep(); //do individual unit micro
            AirUnitKillSquad.onStep(); //micro anti-air kill squads
            GroundUnitKillSquad.onStep(); //micro kill squads for solo enemy ground units
            Harassers.onStep();
            MuleMessages.onStep(); //make minimap troll messages with mules
            LocationConstants.onStep(); //manage which enemy base to target

            purchaseQueue.remove(toRemove);

            TurretingRaven.onStepEnd();

            DebugHelper.onStep();
            ACTION.sendActions();
        }
        catch (Throwable e) {
            Print.print("Bot.onStep() error");
            Error.onException(e);
        }
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
                            ActionHelper.unitCommand(unit, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                        }

                        //get OC
                        if (GameCache.baseList.get(0).getCc() != null &&
                                GameCache.baseList.get(0).getCc().unit().getType() == Units.TERRAN_COMMAND_CENTER) {
                            purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc()));
                        }

                        //put factory at top of queue
                        if (UnitUtils.numMyUnits(Units.TERRAN_FACTORY, true) == 0 && BunkerContain.proxyBunkerLevel != 2) {
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
//                            if (Strategy.DO_DEFENSIVE_TANKS || Strategy.DO_OFFENSIVE_TANKS || Strategy.DO_USE_CYCLONES) {
//                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, unit));
//                            }
//                            //start with (1 factory + 1 starport), or start with (2 starports)
//                            else if (UnitUtils.getNumFriendlyUnits(UnitUtils.STARPORT_TYPE, true) == 0) {
//                                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
//                            }
                        }
                        break;
                    case TERRAN_FACTORY_TECHLAB:
                        if (BunkerContain.proxyBunkerLevel == 2 && UnitUtils.getDistance(unit, LocationConstants.proxyBarracksPos) < 5) {
                            BunkerContain.onFactoryTechLabComplete();
                        }
                        //TODO: testing cyclones
                        else if (Strategy.DO_USE_CYCLONES) {
                            PurchaseUpgrade.add(Upgrades.CYCLONE_LOCK_ON_DAMAGE_UPGRADE);
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
                                UnitUtils.numMyUnits(UnitUtils.COMMAND_STRUCTURE_TYPE_TERRAN, false)
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
                        break;
                }
            }
        }
        catch (Throwable e) {
            Print.print(unitInPool.unit().getType() + " at " + unitInPool.unit().getPosition().toPoint2d());
            Error.onException(e);
        }
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {
        try {
            //WorkerManager.onUnitIdle(unitInPool);
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onUnitCreated(UnitInPool uip) {
        try {
            if (Time.nowFrames() == 1) { //hack so this is never called on game start (since it does and doesn't depending on how it run)
                return;
            }

            if (uip.unit().getAlliance() == Alliance.SELF) {
                GameCache.allMyUnitsSet.add(uip);
            }

            Unit unit = uip.unit();
            if (unit.getType() instanceof Units.Other) {
                System.out.println("****************************************************************");
                System.out.println("Units.Other type for in Ketrocbot.onUnitCreated:" + OBS.getUnitTypeData(false).get(unit.getType()).getName());
                System.out.println("****************************************************************");
                return;
            }

            switch ((Units) unit.getType()) {
                case TERRAN_AUTO_TURRET:
                    PlacementMap.makeUnavailable(unit);
                    break;
                case TERRAN_SIEGE_TANK:
                    if (BunkerContain.proxyBunkerLevel == 2) {
                        BunkerContain.onTankCreated(uip);
                    }
                    break;
                case TERRAN_CYCLONE:
                    Bot.ACTION.toggleAutocast(unit.getTag(), Abilities.EFFECT_LOCK_ON);
                    break;
                case TERRAN_MARINE:
                    if (BunkerContain.proxyBunkerLevel > 0) {
                        BunkerContain.onMarineCreated(uip);
                    }
                    break;
                case TERRAN_RAVEN:
                    //TODO: add to control group 1 - ActionUi needs to be made accessible in ocraft
                    break;
                case TERRAN_SCV:
                    //ignore scvs that exited the refinery as they are considered "created"
                    if (OBS.getUnits(Alliance.SELF, u ->
                            UnitUtils.REFINERY_TYPE.contains(u.unit().getType()) &&
                                    UnitUtils.getDistance(u.unit(), unit) < 3.5f).isEmpty()) {
                        if (!Switches.fastDepotBarracksOpener || Bot.OBS.getFoodWorkers() != 13 || Time.nowSeconds() > 15) { //don't add first created scv if needed for depot
                            WorkerManager.sendScvsToMine(uip);
                        }
                    }
                    break;
                case TERRAN_FACTORY:
                    //set rally on factories to left side
                    Bot.ACTION.unitCommand(unit, Abilities.RALLY_BUILDING, unit.getPosition().toPoint2d().add(-2, -1), false);
                    break;
            }
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onUnitDestroyed(UnitInPool uip) { //TODO: this is called for enemy player too
        try {
            Unit unit = uip.unit();
            Alliance alliance = unit.getAlliance();

            if (alliance == Alliance.SELF) {
                GameCache.allMyUnitsSet.remove(uip);
            }

            //make available non-flying structures
            if ((UnitUtils.isStructure(unit.getType()) || unit.getType() == Units.TERRAN_AUTO_TURRET) &&
                    !unit.getFlying().orElse(true) &&
                    !UnitUtils.GAS_STRUCTURE_TYPES.contains(unit.getType())) {
                PlacementMap.makeAvailable(unit);
            }

            //cancelled structures are handled in requeueCancelledStructure(), not here
            if (alliance == Alliance.SELF && unit.getBuildProgress() < 1) {
                return;
            }

            if (unit.getType() instanceof Units) {
                switch (alliance) {
                    case SELF:
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
                                if (UnitUtils.isWallingStructure(unit)) {
                                    LocationConstants.extraDepots.add(0, Position.toWholePoint(unit.getPosition().toPoint2d()));
                                }
                                else {
                                    LocationConstants.extraDepots.add(Position.toWholePoint(unit.getPosition().toPoint2d()));
                                }
                                if (UnitUtils.getDistance(unit, LocationConstants.WALL_2x2) < 1) {
                                    Chat.tag("main_base_breached");
                                }
                                break;
                            case TERRAN_BARRACKS: case TERRAN_ENGINEERING_BAY: case TERRAN_GHOST_ACADEMY: case TERRAN_ARMORY:
                                if (UnitUtils.isWallingStructure(unit)) {
                                    LocationConstants._3x3Structures.add(0, Position.toWholePoint(unit.getPosition().toPoint2d()));
                                }
                                else {
                                    LocationConstants._3x3Structures.add(Position.toHalfPoint(unit.getPosition().toPoint2d()));
                                }
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
                                    for (DefenseUnitPositions tankPos : base.getTurrets()) {
                                        if (tankPos.getUnit() != null && unit.getTag().equals(tankPos.getUnit().getTag())) {
                                            tankPos.setUnit(null, base);
                                        }
                                    }
                                }
                                break;
                            case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG:
                                //remove from base defense liberator
                                for (Base base : GameCache.baseList) {
                                    for (DefenseUnitPositions libPos : base.getLiberators()) {
                                        if (libPos.getUnit() != null && unit.getTag().equals(libPos.getUnit().getTag())) {
                                            libPos.setUnit(null, base);
                                        }
                                    }
                                }
                                break;
                            case TERRAN_SCV:
                                Base.releaseScv(unit);
                        }
                        break;

                    case ENEMY:
                        //attempt to count banshee kills
                        Harassers.onEnemyUnitDeath(unit);

                        //turn on trolling if game is all but over
                        // (check to turn on when command structure dies)
                        // (check to turn off when any enemy unit dies)
                        if (MuleMessages.doTrollMule ||
                                (ArmyManager.doOffense && UnitUtils.COMMAND_STRUCTURE_TYPE.contains(unit.getType()))) {
                            MuleMessages.checkIfGameIsWon();
                        }
                }
            }
        }
        catch (Throwable e) {
            Print.print(uip.unit().getType() + " at " + uip.unit().getPosition().toPoint2d());
            Error.onException(e);
        }
    }

    @Override
    public void onUpgradeCompleted(Upgrade upgrade) {
        try {
            Print.print(upgrade + " finished at: " + Time.nowClock());

            switch ((Upgrades) upgrade) {
                case LIBERATOR_AG_RANGE_UPGRADE:
                    Liberator.castRange = 8;
                    break;
                case TERRAN_SHIP_WEAPONS_LEVEL1:
                case TERRAN_SHIP_WEAPONS_LEVEL2:
                case TERRAN_SHIP_WEAPONS_LEVEL3:
                case TERRAN_VEHICLE_WEAPONS_LEVEL1:
                case TERRAN_VEHICLE_WEAPONS_LEVEL2:
                case TERRAN_VEHICLE_WEAPONS_LEVEL3:
                case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1:
                case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2:
                case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
                case BANSHEE_CLOAK:
                case BANSHEE_SPEED:
                case RAVEN_CORVID_REACTOR:
                    UpgradeManager.updateUpgradeList(upgrade);
                    break;
            }
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onUnitEnterVision(UnitInPool uip) {
        try {
            Unit unit = uip.unit();
            //make unavailable non-flying enemy structures
            if (unit.getAlliance() == Alliance.ENEMY &&
                    UnitUtils.isStructure(unit.getType()) &&
                    !UnitUtils.GAS_STRUCTURE_TYPES.contains(unit.getType())) {
                PlacementMap.makeUnavailable(unit);
            }
            if (uip.unit().getType() == Units.PROTOSS_ADEPT_PHASE_SHIFT) {
                AdeptShadeTracker.add(uip);
            }
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onNydusDetected() { //called when you hear the scream
        try {
            Chat.tag("vs_Nydus");
            GameResult.setNydusRushed(); //TODO: temp for Spiny
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onNuclearLaunchDetected() { //called when you hear "nuclear launch detected"
        try {
            Chat.tag("vs_Nuke");
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    @Override
    public void onGameEnd() {
        try {
            Cyclone.cycloneKillReport();
            recordGameResult();

            CategoryScoreDetails lostMinerals = OBS.getScore().getDetails().getLostMinerals();
            if (lostMinerals.getArmy() + lostMinerals.getEconomy() +
                    lostMinerals.getTechnology() + lostMinerals.getUpgrade() + lostMinerals.getNone() == 0) {
                Chat.tag("PERFECT_GAME");
            }
            Print.print("opponentId = " + opponentId);
            GameCache.allEnemiesMap.forEach((unitType, unitList) -> Print.print(unitType + ": " + unitList.size()));
            control().saveReplay(Path.of("./data/" + System.currentTimeMillis() + ".SC2Replay"));
        } catch (Throwable e) {
            Error.onException(e);
        }
    }

    private void recordGameResult() {

        Result result = OBS.getResults().stream()
                .filter(playerResult -> playerResult.getPlayerId() == OBS.getPlayerId())
                .map(PlayerResult::getResult)
                .findFirst()
                .orElse(Result.VICTORY);

        //if (result == Result.TIE || result == Result.UNDECIDED) { //removed to treat ties as losses
        if (result == Result.UNDECIDED) {
            return;
        }

        JsonUtil.setGameResult(Strategy.gamePlan, result == Result.VICTORY);

        //old text file save
//        Path path = Paths.get("./data/prevResult.txt");
//        char charResult = (result == Result.VICTORY) ? 'W' : 'L';
//        try {
//            String newFileText = opponentId + "~" + Strategy.gamePlan + "~" + charResult;
//            String prevFileText = Files.readString(Paths.get("./data/prevResult.txt"));
//            if (prevFileText.contains(opponentId)) {
//                newFileText = prevFileText + "\r\n" + newFileText;
//            }
//            Files.write(path, newFileText.getBytes());
//            Print.print("New File Text = " + newFileText);
//        }
//        catch (IOException e) {
//            Error.onException(e);
//        }
        Print.print("==========================");
        Print.print("  Result: " + result.toString());
        Print.print("==========================");
    }

    public static void printCurrentGameInfo() {
        Print.print("\n\nGame info");
        Print.print("===================\n");
        Print.print("GameState.liberatorList.size() = " + GameCache.liberatorList.size());
        Print.print("GameState.siegeTankList.size() = " + GameCache.siegeTankList.size());
        Print.print("GameState.vikingList.size() = " + GameCache.vikingList.size());
        Print.print("GameState.bansheeList.size() = " + GameCache.bansheeList.size());
        Print.print("Strategy.DO_INCLUDE_LIBS = " + Strategy.DO_DEFENSIVE_LIBS);
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
            Print.print("base.isDryedUp() = " + base.isDriedUp());
            Print.print("Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()) = " + QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()));
            Print.print("base.lastScoutedFrame = " + base.lastScoutedFrame);
            Print.print("Bot.OBS.getVisibility(base.getCcPos()).toString() = " + OBS.getVisibility(base.getCcPos()).toString());
        }
        Print.print("\n\n");

        Print.print("Ignored.ignoredUnits");
        Print.print("====================");
        Ignored.ignoredUnits.forEach(ignored -> {
            UnitInPool unit = Bot.OBS.getUnit(ignored.unitTag);
            if (unit == null) {
                Print.print("null unit: " + ignored.unitTag);
            }
            else {
                Print.print(unit.unit().getAlliance() + " " +
                        unit.unit().getType() + " " +
                        unit.unit().getPosition() + ": " +
                        ignored.unitTag);
            }
        });
        Print.print("\n\n");

        Print.print("UnitMicroList.unitMicroList");
        Print.print("===========================");
        UnitMicroList.unitMicroList.forEach(micro -> {
            Print.print(micro.getClass().toString() + " " +
                    micro.unit.unit().getType() + " " +
                    micro.unit.unit().getPosition());
        });
    }
}
