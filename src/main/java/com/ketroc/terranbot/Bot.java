package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.BuildManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.purchases.*;

import java.util.*;

public class Bot extends S2Agent {
    public static ActionInterface ACTION;
    public static LinkedList<Purchase> purchaseQueue = new LinkedList<Purchase>();
    public static ObservationInterface OBS;
    public static QueryInterface QUERY;
    public static DebugInterface DEBUG;
    public static Map<Abilities, Units> abilityToUnitType = new HashMap<>(); //TODO: move

    public Bot() { }

    @Override
    public void onGameStart() {
        OBS = observation();
        ACTION = actions();
        QUERY = query();
        DEBUG = debug();

        //set map
        System.out.println("OBS.getGameInfo().getMapName() = " + OBS.getGameInfo().getMapName());
        LocationConstants.MAP = OBS.getGameInfo().getMapName();

        //DEBUG.debugShowMap(); //TODO: debug
        //get map, get hardcoded map locations
        UnitInPool mainCC = OBS.getUnits(Alliance.SELF, cc -> cc.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0);
        ACTION.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
        ACTION.sendActions();

        GameState.z = Bot.OBS.terrainHeight(mainCC.unit().getPosition().toPoint2d()) + 0.5f;
        LocationConstants.init(mainCC);

        //build lists
        GameState.onStep();

        //load abilityToUnitType map
        Bot.OBS.getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
            unitTypeData.getAbility().ifPresent(ability -> {
                if (ability instanceof Abilities && unitType instanceof Units) {
                    Bot.abilityToUnitType.put((Abilities) ability, (Units)unitType);
                }
            });
        });

        //set enemy race
        Set<PlayerInfo> players = OBS.getGameInfo().getPlayersInfo();
        for (PlayerInfo player : players) {
            if (player.getRequestedRace() != Race.TERRAN) {
                LocationConstants.opponentRace = player.getRequestedRace();
                break;
            }
        }
        LocationConstants.opponentRace = Race.ZERG; //TODO: delete - for testing

        //set build order
        switch (LocationConstants.opponentRace) {
            case TERRAN:
                Switches.TvtFastStart = true;
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameState.baseList.get(0).getCc()));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.WALL_3x3));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY, LocationConstants.MID_WALL_3x3));
                break;
            case ZERG:
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.MID_WALL_3x3));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY, LocationConstants.WALL_3x3));
                purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameState.baseList.get(0).getCc()));
                //purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, GameState.baseList.get(1).getCc()));
                break;
            case PROTOSS:
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants.MID_WALL_3x3));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY, LocationConstants.WALL_3x3));
                purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameState.baseList.get(0).getCc()));
                //purchaseQueue.add(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, GameState.baseList.get(1).getCc()));
                break;
        }


    }
    @Override
    public void onStep() {
        try {
            long start = System.currentTimeMillis();
            if (OBS.getGameLoop() % Strategy.SKIP_FRAMES == 0) {
                GameState.onStep();

                int lines = 0;
                DEBUG.debugTextOut("banshees: " + GameState.bansheeList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 15);
                DEBUG.debugTextOut("vikings: " + GameState.vikingList.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 15);
                DEBUG.debugTextOut("Purchase Queue: " + Bot.purchaseQueue.size(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 15);
                for (int i=0; i<purchaseQueue.size() && i<5; i++) {
                    DEBUG.debugTextOut(Bot.purchaseQueue.get(i).getType(), Point2d.of((float) 0.1, (float) ((100.0 + 20.0 * lines++) / 1080.0)), Color.WHITE, 15);
                }

                if (Switches.TvtFastStart) {
                    Strategy.onStep_TvtFaststart();
                }
                else {
                    purchaseLoop:
                    for (int i = 0; i < purchaseQueue.size(); i++) {
                        PurchaseResult result = purchaseQueue.get(i).build();
                        switch (result) {
                            case SUCCESS:
                                purchaseQueue.remove(i);
                                break purchaseLoop;
                            case WAITING:
                                break;
                            case CANCEL:
                                purchaseQueue.remove(i--);
                                break;
                        }
                    }
                }

                long start1 = System.currentTimeMillis();
                BuildManager.onStep();
                WorkerManager.onStep();
                ArmyManager.onStep();
                LocationConstants.onStep();
                long resultTime = System.currentTimeMillis() - start1;
                if (resultTime > 12)
                    System.out.println("managers' onsteps time: " + resultTime);


                Bot.ACTION.sendActions();
            }
            if (System.currentTimeMillis() - start > 30)
                System.out.println("bot.onStep() = " + (System.currentTimeMillis() - start));
            Bot.DEBUG.sendDebug(); //draw the screen debug
        }
        catch (Exception e) {
            System.out.println("At " + OBS.getGameLoop());
            e.printStackTrace();
        }
    } // end onStep()



    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        try {
            Unit unit = unitInPool.unit();
            System.out.println(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() + ") at: " + currentGameTime());
            switch((Units)unit.getType()) {
                case TERRAN_BARRACKS:
                    purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameState.baseList.get(0).getCc())); //TODO: only first time (or only if base isn't OC already)
                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.STARPORTS.get(LocationConstants.STARPORTS.size() - 1)));
                    break;
                case TERRAN_FACTORY:
                    //purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, unitInPool)); //TODO: turned off cuz no siege tanks
                    ACTION.unitCommand(unit, Abilities.LIFT_FACTORY, false);
                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT, LocationConstants.STARPORTS.remove(0)));
                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(0)));
                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(1)));
                    break;
                case TERRAN_STARPORT:
                    purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, unitInPool));
                    if (GameState.allFriendliesMap.getOrDefault(Units.TERRAN_ARMORY, Collections.emptyList()).isEmpty() && GameState.starportList.size() == 2) {
                        purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY, LocationConstants.ARMORY_WEAPONS));
                        purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY, LocationConstants.ARMORY_ARMOR));
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_SPEED, GameState.allFriendliesMap.get(Units.TERRAN_STARPORT_TECHLAB).get(0)));
                    }
                    break;
                case TERRAN_STARPORT_TECHLAB:
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_CLOAK, unitInPool));
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, GameState.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0))); //TODO: null check
                    break;
                case TERRAN_ARMORY:
                    if (LocationConstants.ARMORY_WEAPONS.distance(unit.getPosition().toPoint2d()) < 1) {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1, unitInPool));
                    }
                    else {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1, unitInPool));
                    }
                    break;
                case TERRAN_SUPPLY_DEPOT:
                    ACTION.unitCommand(unit, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false); //lower depot
                    break;
                case TERRAN_PLANETARY_FORTRESS:
                    List<Point2d> turretPositions = BuildManager.calculateTurretPositions(unit.getPosition().toPoint2d());
                    for (Point2d turretPosition : turretPositions) {
                        purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPosition));
                    }
                    break;
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
            switch (alliance) {
                case SELF:
                    switch ((Units) unit.getType()) {
                        case TERRAN_SUPPLY_DEPOT: //add this location to build new depot locations list
                            LocationConstants.extraDepots.add(unit.getPosition().toPoint2d());
                            break;
                        case TERRAN_STARPORT:
                            LocationConstants.STARPORTS.add(unit.getPosition().toPoint2d());
                            break;
                        case TERRAN_MISSILE_TURRET:
                            LocationConstants.TURRETS.add(unit.getPosition().toPoint2d());
                            break;
                        case TERRAN_SCV: //replace scv that died making a structure
                            //if scv was building a structure (or on the way to start building one)
                            if (!unit.getOrders().isEmpty() && BuildManager.BUILD_ACTIONS.contains((Abilities) unit.getOrders().get(0).getAbility())) {
                                //get structure at scv target position
                                Point2d targetPos = unit.getOrders().get(0).getTargetedWorldSpacePosition().get().toPoint2d(); //TODO: no target possible (should be handled??) getTargetedWorldSpacePosition throws nosuchelementexception
                                for (Unit structure : GameState.inProductionList) {
                                    if (structure.getPosition().toPoint2d().distance(targetPos) < 1) {
                                        //send scv
                                        List<UnitInPool> scvs = WorkerManager.getAvailableScvs(targetPos);
                                        if (!scvs.isEmpty()) {
                                            Bot.ACTION.unitCommand(scvs.get(0).unit(), Abilities.SMART, structure, false);
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                    }
                    break;
                case ENEMY:
                    if (unitInPool.equals(Switches.bansheeDiveTarget)) {
                        Switches.bansheeDiveTarget = null;
                        GameState.bansheeDivers.clear();
                    }
                    else if (unitInPool.equals(Switches.vikingDiveTarget)) {
                        Switches.vikingDiveTarget = null;
                        GameState.vikingDivers.clear();
                    }
                    break;
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
        switch((Upgrades)upgrade) {
            case TERRAN_BUILDING_ARMOR:
                purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, GameState.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY).get(0)));
                break;
            case TERRAN_SHIP_WEAPONS_LEVEL1:
                for (UnitInPool armory : GameState.allFriendliesMap.get(Units.TERRAN_ARMORY)) {
                    if (armory.unit().getPosition().toPoint2d().distance(LocationConstants.ARMORY_WEAPONS) < 1) {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2, armory));
                    }
                }
                break;
            case TERRAN_SHIP_WEAPONS_LEVEL2:
                for (UnitInPool armory : GameState.allFriendliesMap.get(Units.TERRAN_ARMORY)) {
                    if (armory.unit().getPosition().toPoint2d().distance(LocationConstants.ARMORY_WEAPONS) < 1) {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3, armory));
                    }
                }
                break;
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1:
                for (UnitInPool armory : GameState.allFriendliesMap.get(Units.TERRAN_ARMORY)) {
                    if (armory.unit().getPosition().toPoint2d().distance(LocationConstants.ARMORY_ARMOR) < 1) {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2, armory));
                    }
                }
                break;
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2:
                for (UnitInPool armory : GameState.allFriendliesMap.get(Units.TERRAN_ARMORY)) {
                    if (armory.unit().getPosition().toPoint2d().distance(LocationConstants.ARMORY_ARMOR) < 1) {
                        purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3, armory));
                    }
                }
                break;
        }

    }

    @Override
    public void onUnitEnterVision(UnitInPool unitInPool) {

    }

    @Override
    public void onNydusDetected() {

    }

    @Override
    public void onNuclearLaunchDetected() {

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
