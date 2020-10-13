package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.managers.*;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.strategies.*;

import java.util.*;
import java.util.stream.Collectors;

public class DroneDrill extends Bot {
    public static int droneRushBuildStep;
    public static int mutaRushBuildStep;
    public static int curMinerals;
    public static int curGas;
    public static boolean eggRallyComplete;
    public static int baseScoutIndex = 2;
    public static Unit geyser1;
    public static Unit geyser2;
    public static List<UnitInPool> availableDrones;
    public static UnitInPool mainHatch;
    public static List<Unit> larvaList;
    public static List<UnitInPool> lateDrones = new ArrayList<>();
    public static List<Abilities> mutasBuildOrder = new ArrayList<>(List.of(
            Abilities.BUILD_SPAWNING_POOL, Abilities.BUILD_EXTRACTOR, Abilities.BUILD_EXTRACTOR,
            Abilities.MORPH_LAIR, Abilities.BUILD_SPIRE
    ));

    public DroneDrill(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }


    public void onAlert(Alert alert) {

    }

    @Override
    public void onGameStart() {
        try {
            super.onGameStart();

            Strategy.SKIP_FRAMES = (Bot.isRealTime) ? 6 : 2;

            //set map
            LocationConstants.MAP = OBS.getGameInfo().getMapName();

            //set enemy race
            LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                    .filter(playerInfo -> playerInfo.getPlayerId() != Bot.OBS.getPlayerId())
                    .findFirst()
                    .get()
                    .getRequestedRace();

            //start first scv
            mainHatch = OBS.getUnits(Alliance.SELF, hatch -> hatch.unit().getType() == Units.ZERG_HATCHERY).get(0);

            //get map, get hardcoded map locations
            LocationConstants.onGameStart(mainHatch);
            DebugHelper.onGameStart();

            //build unit lists
            GameCache.onStep();

            //set main midpoint (must be done after GameState.onStep())
            LocationConstants.setRepairBayLocation();

            ACTION.sendActions();

            geyser1 = GameCache.baseList.get(0).getGases().get(0).getGeyser();
            geyser2 = GameCache.baseList.get(0).getGases().get(1).getGeyser();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStep() {
        super.onStep();
        availableDrones = getAvailableDrones();
        try {
            if (OBS.getGameLoop() % Strategy.SKIP_FRAMES == 0) { // && LocalDate.now().isBefore(LocalDate.of(2020, 8, 5))) {
                if (OBS.getGameLoop() == Strategy.SKIP_FRAMES) {
                    DroneDrill.ACTION.sendChat("Last updated: Sept 24, 2020", ActionChat.Channel.BROADCAST);
                }
                //free up ignored units
                Ignored.onStep();

                //remove expired enemy scans
                EnemyScan.onStep();

                //rebuild unit cache every frame
                GameCache.onStep();

                //check switches
                Switches.onStep();

                //execute actions queued to this game frame
                DelayedAction.onStep();
                DelayedChat.onStep();

                //print report of current game state
//                if (Bot.OBS.getGameLoop() % 3000 == 0) { //every 5min
//                    printCurrentGameInfo();
//                }

                //move flying CCs
                FlyingCC.onStep();

                //update status of scvs building structures
                StructureScv.checkScvsActivelyBuilding();  //TODO: move to GameState onStep()??

                //scv rush opener
                if (!Switches.scvRushComplete) {
                    Switches.scvRushComplete = ScvRush.onStep();
                }

                //clearing bases that have just dried up or died
                GameCache.baseList.stream().forEach(Base::onStep);

                curMinerals = Bot.OBS.getMinerals();
                curGas = Bot.OBS.getVespene();
                larvaList = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_LARVA);
                if (LocationConstants.MAP.equals(MapNames.PILLARS_OF_GOLD)) { //no cluster available
                    droneRushBuildStep = -1;
                    mutaRushBuild();
                }

                switch (droneRushBuildStep) {
                    //drone up to supply cap
                    case 0:
                        if (Bot.OBS.getFoodUsed() == Bot.OBS.getFoodCap()) {
                            droneRushBuildStep++;
                        }
                        break;

                    //wait until 130minerals then build 2 extractors
                    case 1:
                        if (curMinerals >= 125) {
                            UnitInPool closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                            closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                            droneRushBuildStep++;
                        }
                        break;

                    //build 2 drones
                    case 2:
                        if (Bot.OBS.getFoodUsed() == Bot.OBS.getFoodCap() &&
                                Bot.OBS.getUnits(Alliance.SELF, gas -> gas.unit().getType() == Units.ZERG_EXTRACTOR).size() == 2) {
                            droneRushBuildStep++;
                        }
                        break;

                    //cancel extractors and give eggs commands
                    case 3:
                        List<Unit> extractors = UnitUtils.toUnitList(
                                Bot.OBS.getUnits(Alliance.SELF, gas -> gas.unit().getType() == Units.ZERG_EXTRACTOR)
                        );
                        Bot.ACTION.unitCommand(extractors, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                        droneRushBuildStep++;
                        break;

                    //start dronerush code & rally eggs
                    case 4:
                        if (!eggRallyComplete && Bot.OBS.getFoodUsed() == 16) {
                            List<Unit> eggs = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG);
                            if (!eggs.isEmpty()) {
                                Bot.ACTION.unitCommand(eggs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
                            }
                            eggRallyComplete = true;
                        }
                        lateDronesHitBarracksScv();
                        DroneRush.onStep();
                        mutaRushBuild();
                        break;
                }
                if (Bot.OBS.getMinerals() >= 50 && Bot.OBS.getFoodWorkers() < 14) {
                    if (!larvaList.isEmpty()) {
                        Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_DRONE, false);
                    }
                }

                DroneDrill.ACTION.sendActions();
            }
        }
        catch (Exception e) {
            System.out.println("Bot.onStep() error At game frame: " + OBS.getGameLoop());
            e.printStackTrace();
        }
    } // end onStep()

    private void mutaRushBuild() {
        if (mutaRushBuildStep >= 3 && curMinerals >= 100 &&
                Bot.OBS.getFoodUsed() + 6 > Bot.OBS.getFoodCap() && !isProducing(Abilities.TRAIN_OVERLORD)) {
            if (!larvaList.isEmpty()) {
                Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
            }
        }
        if (Bot.OBS.getFoodWorkers() > 6) {
            if (!availableDrones.isEmpty()) {
                for (Unit extractor : UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EXTRACTOR)) {
                    if (extractor.getBuildProgress() == 1f && extractor.getAssignedHarvesters().orElse(3) < 3) {
                        UnitInPool closestDrone = getClosest(availableDrones, extractor.getPosition().toPoint2d());
                        Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.SMART, extractor, false);
                        availableDrones.remove(closestDrone);
                        break;
                    }
                }
            }
        }
        switch (mutaRushBuildStep) {
            //gas
            case 0:
                if (Bot.OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    UnitInPool closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //gas
            case 1:
                if (Bot.OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    UnitInPool closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //pool
            case 2:
                if (curMinerals >= 200) {
                    Point2d poolPos = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -7);
                    UnitInPool closestDrone = getClosest(availableDrones, poolPos);
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_SPAWNING_POOL, poolPos, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //lair
            case 3:
                if (curMinerals >= 150 && curGas >= 100 && !UnitUtils.getFriendlyUnitsOfType(Units.ZERG_SPAWNING_POOL).isEmpty()) {
                    Bot.ACTION.unitCommand(mainHatch.unit(), Abilities.MORPH_LAIR, false);
                    mutaRushBuildStep++;
                }
                if (curMinerals >= 251 && larvaList.size() >= 3) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;
            //spire
            case 4:
                List<Unit> lair = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_LAIR);
                if (!lair.isEmpty()) {
                    if (curMinerals >= 200 && curGas >= 200) {
                        Point2d spirePos = Position.rotate(
                                Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -7),
                                GameCache.baseList.get(0).getCcPos(), 60);
                        UnitInPool closestDrone = getClosest(availableDrones, spirePos);
                        Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_SPIRE, spirePos, false);
                        availableDrones.remove(closestDrone);
                        mainHatch = Bot.OBS.getUnit(lair.get(0).getTag());
                        mutaRushBuildStep++;
                    }
                }
                if (curMinerals >= 301 && larvaList.size() >= 3) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;

            //start making mutas
            case 5:
                if (curMinerals >= curGas && larvaList.size() >= 3 && Bot.OBS.getUnits(Alliance.SELF, spire -> spire.unit().getType() == Units.ZERG_SPIRE && spire.unit().getBuildProgress() > 0.7f).isEmpty()) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                if (curMinerals >= 100 && curGas >= 100) {
                    if (!larvaList.isEmpty()) {
                        Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                    }
                }
                if (curGas < 100) {
                    LocationConstants.baseAttackIndex = LocationConstants.baseLocations.size()-2;
                    mutaRushBuildStep++;
                }
                break;
            case 6:
                if (curMinerals >= 100 && curGas >= 100) {
                    if (!larvaList.isEmpty()) {
                        Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                    }
                }
                List<Unit> mutas = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_MUTALISK);
                List<UnitInPool> enemyAA = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType()).getWeapons().stream()
                                .anyMatch(weapon -> weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY));
                if (!mutas.isEmpty()) {
                    if (!enemyAA.isEmpty()) {
                        Bot.ACTION.unitCommand(mutas, Abilities.ATTACK, enemyAA.get(0).unit(), false);
                    }
                    else {
                        List<UnitInPool> enemies = Bot.OBS.getUnits(Alliance.ENEMY);
                        if (!enemies.isEmpty()) {
                            Bot.ACTION.unitCommand(mutas, Abilities.ATTACK, enemies.get(0).unit().getPosition().toPoint2d(), false);
                        }
                        else if (Switches.finishHim) {
                            ArmyManager.spreadArmy(mutas);
                        }
                        else {
                            Point2d attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
                            Point2d lambdaAttackPos = attackPos;
                            if (mutas.stream().anyMatch(muta -> UnitUtils.getDistance(muta, lambdaAttackPos) < 3)) {
                                LocationConstants.rotateBaseAttackIndex();
                                attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
                            }
                            Bot.ACTION.unitCommand(mutas, Abilities.ATTACK,
                                    LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex), false);
                        }
                    }
                }
                break;

            //
            case 7:
                break;
            //
            case 8:
                break;
        }
    }

    private void lateDronesHitBarracksScv() {
        if (LocationConstants.opponentRace != Race.TERRAN || lateDrones.isEmpty()) {
            return;
        }
        List<UnitInPool> enemyBarracksList = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
        if (!enemyBarracksList.isEmpty() && enemyBarracksList.get(0).unit().getBuildProgress() < 1f) {
            Unit producingScv = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracksList.get(0).unit().getPosition().toPoint2d());
            if (producingScv != null && UnitUtils.getDistance(producingScv, enemyBarracksList.get(0).unit()) < 3.5f) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.ATTACK, producingScv, false);
            }
        }
        List<Unit> idleDrones = lateDrones.stream()
                .map(UnitInPool::unit)
                .filter(drone -> drone.getOrders().isEmpty())
                .collect(Collectors.toList());
        if (!idleDrones.isEmpty()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
        }
    }


    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
    }

    @Override
    public void onUnitIdle(UnitInPool unitInPool) {

    }

    @Override
    public void onUnitCreated(UnitInPool drone) {
        if (drone.unit().getType() == Units.ZERG_DRONE &&
                !drone.unit().getOrders().isEmpty() &&
                UnitUtils.getDistance(Bot.OBS.getUnit(drone.unit().getOrders().get(0).getTargetedUnitTag().get()).unit(),
                        LocationConstants.enemyMineralTriangle.getInner().unit()) < 1) {
            lateDrones.add(drone);
            if (LocationConstants.opponentRace == Race.TERRAN) { //for finding proxy barracks
                Bot.ACTION.unitCommand(drone.unit(), Abilities.MOVE, LocationConstants.baseLocations.get(baseScoutIndex++), false)
                        .unitCommand(drone.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), true);
            }
        }
    }

    @Override
    public void onUnitDestroyed(UnitInPool unitInPool) {

    }

    @Override
    public void onUpgradeCompleted(Upgrade upgrade) {

    }

    @Override
    public void onUnitEnterVision(UnitInPool unitInPool) {

    }

    @Override
    public void onNydusDetected() { //called when you hear the scream

    }

    @Override
    public void onNuclearLaunchDetected() { //called when you hear "nuclear launch detected"

    }

    @Override
    public void onGameEnd() {

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

    public static UnitInPool getClosest(List<UnitInPool> unitList, Point2d pos) {
        return unitList.stream().min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), pos))).orElse(unitList.get(0));
    }

    public static List<UnitInPool> getAvailableDrones() {
        return Bot.OBS.getUnits(Alliance.SELF, drone ->
                drone.unit().getType() == Units.ZERG_DRONE &&
                (drone.unit().getOrders().isEmpty() || WorkerManager.isMiningMinerals(drone)) &&
                (DroneRush.droneList == null || !DroneRush.droneList.contains(drone)));
    }

    public static boolean isProducing(Abilities training) {
        for (Unit egg : UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG)) {
            if (egg.getOrders().get(0).getAbility() == training) {
                return true;
            }
        }
        return false;

    }
}
