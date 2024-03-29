package com.ketroc.bots;

import com.github.ocraft.s2client.bot.gateway.*;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.Switches;
import com.ketroc.geometry.Position;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.ArmyManager;
import com.ketroc.managers.WorkerManager;
import com.ketroc.models.*;
import com.ketroc.strategies.DroneRush;
import com.ketroc.strategies.ScvRush;
import com.ketroc.utils.*;
import com.ketroc.utils.Error;

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

    public DroneDrill(String opponentId) {
        super(opponentId);
    }


    public void onAlert(Alert alert) {

    }

    @Override
    public void onGameStart() {
        try {
            super.onGameStart();

            Launcher.STEP_SIZE = (Launcher.isRealTime) ? 4 : 2;

            //set map
            PosConstants.MAP = OBS.getGameInfo().getMapName();

            //set enemy race
            PosConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream()
                    .filter(playerInfo -> playerInfo.getPlayerId() != OBS.getPlayerId())
                    .findFirst()
                    .get()
                    .getRequestedRace();


            //get map, get hardcoded map locations
            PosConstants.onGameStart();
            DebugHelper.onGameStart();

            //build unit lists
            GameCache.onStepStart();

            //set main midpoint (must be done after GameState.onStep())
            PosConstants.setRepairBayLocation();

            ACTION.sendActions();

            geyser1 = GameCache.baseList.get(0).getGases().get(0).getNode();
            geyser2 = GameCache.baseList.get(0).getGases().get(1).getNode();

        }
        catch (Exception e) {
            Error.onException(e);
        }
    }

    @Override
    public void onStep() {
        super.onStep();
        availableDrones = getAvailableDrones();
        try {
            if (Time.nowFrames() % Launcher.STEP_SIZE == 0) { // && LocalDate.now().isBefore(LocalDate.of(2020, 8, 5))) {
                if (Time.nowFrames() == Launcher.STEP_SIZE) {
                    ACTION.sendChat("Last updated: Sept 24, 2020", ActionChat.Channel.BROADCAST);
                }
                //free up ignored units
                Ignored.onStepStart();

                //remove expired enemy scans
                EnemyScan.onStepStart();

                //rebuild unit cache every frame
                GameCache.onStepStart();

                //check switches
                Switches.onStep();

                //execute actions queued to this game frame
                DelayedAction.onStep();
                DelayedChat.onStep();

                //print report of current game state
//                if (Time.nowFrames() % Time.toFrames("5:00") == 0) { //every 5min
//                    printCurrentGameInfo();
//                }

                //move flying CCs
                FlyingCC.onStep();

                //update status of scvs building structures
                StructureScv.checkScvsActivelyBuilding();

                //scv rush opener
                if (!Switches.scvRushComplete) {
                    Switches.scvRushComplete = ScvRush.onStep();
                }

                //clearing bases that have just dried up or died
                GameCache.baseList.stream().forEach(Base::onStep);

                curMinerals = OBS.getMinerals();
                curGas = OBS.getVespene();
                larvaList = UnitUtils.myUnitsOfType(Units.ZERG_LARVA);
                if (PosConstants.MAP.equals(MapNames.PILLARS_OF_GOLD) || PosConstants.MAP.equals(MapNames.PILLARS_OF_GOLD505)) { //no cluster available
                    droneRushBuildStep = -1;
                    mutaRushBuild();
                }

                switch (droneRushBuildStep) {
                    //drone up to supply cap
                    case 0:
                        if (OBS.getFoodUsed() == OBS.getFoodCap()) {
                            droneRushBuildStep++;
                        }
                        break;

                    //wait until 130minerals then build 2 extractors
                    case 1:
                        if (curMinerals >= 125) {
                            UnitInPool closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                            closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                            droneRushBuildStep++;
                        }
                        break;

                    //build 2 drones
                    case 2:
                        if (OBS.getFoodUsed() == OBS.getFoodCap() &&
                                OBS.getUnits(Alliance.SELF, gas -> gas.unit().getType() == Units.ZERG_EXTRACTOR).size() == 2) {
                            droneRushBuildStep++;
                        }
                        break;

                    //cancel extractors and give eggs commands
                    case 3:
                        List<Unit> extractors = UnitUtils.toUnitList(
                                OBS.getUnits(Alliance.SELF, gas -> gas.unit().getType() == Units.ZERG_EXTRACTOR)
                        );
                        ActionHelper.unitCommand(extractors, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                        droneRushBuildStep++;
                        break;

                    //start dronerush code & rally eggs
                    case 4:
                        if (!eggRallyComplete && OBS.getFoodUsed() == 16) {
                            List<Unit> eggs = UnitUtils.myUnitsOfType(Units.ZERG_EGG);
                            if (!eggs.isEmpty()) {
                                ActionHelper.unitCommand(eggs, Abilities.SMART, PosConstants.enemyMineralTriangle.getInner().unit(), false);
                            }
                            eggRallyComplete = true;
                        }
                        lateDronesHitBarracksScv();
                        DroneRush.onStep();
                        mutaRushBuild();
                        break;
                }
                if (OBS.getMinerals() >= 50 && OBS.getFoodWorkers() < 14) {
                    if (!larvaList.isEmpty()) {
                        ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_DRONE, false);
                    }
                }

                ACTION.sendActions();
            }
        }
        catch (Exception e) {
            Print.print("Bot.onStep() error");
            Error.onException(e);
        }
    } // end onStep()

    private void mutaRushBuild() {
        if (mutaRushBuildStep >= 3 && curMinerals >= 100 &&
                OBS.getFoodUsed() + 6 > OBS.getFoodCap() && !isProducing(Abilities.TRAIN_OVERLORD)) {
            if (!larvaList.isEmpty()) {
                ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
            }
        }
        if (OBS.getFoodWorkers() > 6) {
            if (!availableDrones.isEmpty()) {
                for (Unit extractor : UnitUtils.myUnitsOfType(Units.ZERG_EXTRACTOR)) {
                    if (extractor.getBuildProgress() == 1f && extractor.getAssignedHarvesters().orElse(3) < 3) {
                        UnitInPool closestDrone = getClosest(availableDrones, extractor.getPosition().toPoint2d());
                        ActionHelper.unitCommand(closestDrone.unit(), Abilities.SMART, extractor, false);
                        availableDrones.remove(closestDrone);
                        break;
                    }
                }
            }
        }
        switch (mutaRushBuildStep) {
            //gas
            case 0:
                if (OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    UnitInPool closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                    ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //gas
            case 1:
                if (OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    UnitInPool closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                    ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //pool
            case 2:
                if (curMinerals >= 200) {
                    Point2d poolPos = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -7);
                    UnitInPool closestDrone = getClosest(availableDrones, poolPos);
                    ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_SPAWNING_POOL, poolPos, false);
                    availableDrones.remove(closestDrone);
                    mutaRushBuildStep++;
                }
                break;
            //lair
            case 3:
                if (curMinerals >= 150 && curGas >= 100 && !UnitUtils.myUnitsOfType(Units.ZERG_SPAWNING_POOL).isEmpty()) {
                    ActionHelper.unitCommand(mainHatch.unit(), Abilities.MORPH_LAIR, false);
                    mutaRushBuildStep++;
                }
                if (curMinerals >= 251 && larvaList.size() >= 3) {
                    ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;
            //spire
            case 4:
                List<Unit> lair = UnitUtils.myUnitsOfType(Units.ZERG_LAIR);
                if (!lair.isEmpty()) {
                    if (curMinerals >= 200 && curGas >= 200) {
                        Point2d spirePos = Position.rotate(
                                Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -7),
                                GameCache.baseList.get(0).getCcPos(), 60);
                        UnitInPool closestDrone = getClosest(availableDrones, spirePos);
                        ActionHelper.unitCommand(closestDrone.unit(), Abilities.BUILD_SPIRE, spirePos, false);
                        availableDrones.remove(closestDrone);
                        mainHatch = OBS.getUnit(lair.get(0).getTag());
                        mutaRushBuildStep++;
                    }
                }
                if (curMinerals >= 301 && larvaList.size() >= 3) {
                    ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;

            //start making mutas
            case 5:
                if (curMinerals >= curGas && larvaList.size() >= 3 && OBS.getUnits(Alliance.SELF, spire -> spire.unit().getType() == Units.ZERG_SPIRE && spire.unit().getBuildProgress() > 0.7f).isEmpty()) {
                    ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                if (curMinerals >= 100 && curGas >= 100) {
                    if (!larvaList.isEmpty()) {
                        ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                    }
                }
                if (curGas < 100) {
                    mutaRushBuildStep++;
                }
                break;
            case 6:
                if (curMinerals >= 100 && curGas >= 100) {
                    if (!larvaList.isEmpty()) {
                        ActionHelper.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                    }
                }
                List<Unit> mutas = UnitUtils.myUnitsOfType(Units.ZERG_MUTALISK);
                List<UnitInPool> enemyAA = OBS.getUnits(Alliance.ENEMY, enemy ->
                        OBS.getUnitTypeData(false).get(enemy.unit().getType()).getWeapons().stream()
                                .anyMatch(weapon -> weapon.getTargetType() == Weapon.TargetType.AIR || weapon.getTargetType() == Weapon.TargetType.ANY));
                if (!mutas.isEmpty()) {
                    if (!enemyAA.isEmpty()) {
                        ActionHelper.unitCommand(mutas, Abilities.ATTACK, enemyAA.get(0).unit(), false);
                    }
                    else {
                        List<UnitInPool> enemies = OBS.getUnits(Alliance.ENEMY);
                        if (!enemies.isEmpty()) {
                            ActionHelper.unitCommand(mutas, Abilities.ATTACK, enemies.get(0).unit().getPosition().toPoint2d(), false);
                        }
                        else if (PosConstants.nextEnemyBase == null) {
                            ArmyManager.spreadArmy(mutas);
                        }
                        else {
                            Point2d attackPos = UnitUtils.getNextEnemyBase().getResourceMidPoint();
                            ActionHelper.unitCommand(mutas, Abilities.ATTACK, attackPos, false);
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
        if (PosConstants.opponentRace != Race.TERRAN || lateDrones.isEmpty()) {
            return;
        }
        List<UnitInPool> enemyBarracksList = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
        if (!enemyBarracksList.isEmpty() && enemyBarracksList.get(0).unit().getBuildProgress() < 1f) {
            Unit producingScv = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracksList.get(0).unit().getPosition().toPoint2d());
            if (producingScv != null && UnitUtils.getDistance(producingScv, enemyBarracksList.get(0).unit()) < 3.5f) {
                ActionHelper.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.ATTACK, producingScv, false);
            }
        }
        List<Unit> idleDrones = lateDrones.stream()
                .map(UnitInPool::unit)
                .filter(drone -> drone.getOrders().isEmpty())
                .collect(Collectors.toList());
        if (!idleDrones.isEmpty()) {
            ActionHelper.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.SMART, PosConstants.enemyMineralTriangle.getInner().unit(), false);
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
                UnitUtils.getDistance(OBS.getUnit(drone.unit().getOrders().get(0).getTargetedUnitTag().get()).unit(),
                        PosConstants.enemyMineralTriangle.getInner().unit()) < 1) {
            lateDrones.add(drone);
            if (PosConstants.opponentRace == Race.TERRAN) { //for finding proxy barracks
                ActionHelper.unitCommand(drone.unit(), Abilities.MOVE, PosConstants.baseLocations.get(baseScoutIndex++), false);
                ActionHelper.unitCommand(drone.unit(), Abilities.SMART, PosConstants.enemyMineralTriangle.getInner().unit(), true);
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
        return OBS.getUnits(Alliance.SELF, drone ->
                drone.unit().getType() == Units.ZERG_DRONE &&
                (drone.unit().getOrders().isEmpty() || WorkerManager.isMiningMinerals(drone)) &&
                (DroneRush.droneList == null || !DroneRush.droneList.contains(drone)));
    }

    public static boolean isProducing(Abilities training) {
        for (Unit egg : UnitUtils.myUnitsOfType(Units.ZERG_EGG)) {
            if (UnitUtils.getOrder(egg) == training) {
                return true;
            }
        }
        return false;

    }
}
