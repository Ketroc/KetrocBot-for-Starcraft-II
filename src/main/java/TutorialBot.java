import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.BattlenetMap;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.response.ResponseGameInfo;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/*
onBuildingConstructionComplete(UnitInPool unitInPool)
onStep()
onUnitCreated(UnitInPool unitInPool)
onUnitIdle(UnitInPool unitInPool)
onAlert(Alert alert)

onUpgradeCompleted(Upgrade upgrade)
onUnitEnterVision(UnitInPool unitInPool)
onUnitDestroyed(UnitInPool unitInPool)
onError(java.util.List<ClientError> clientErrors, java.util.List<java.lang.String> protocolErrors)
onGameEnd()
onGameFullStart()
onGameStart()
onNuclearLaunchDetected()
onNydusDetected()


19 = (145.0, 50.0) depot
21 = (145.5, 59.5) barracks
18 = (133.5, 43.5) cc
24 = (128.5, 51.5) bunker
19 = (145.0, 52.0) depot
24 = (142.5, 57.5) bunker
20 = (167.5, 49.5) gas
20 = (156.5, 39.5) gas
20 = (126.5, 40.5) gas
 */

public class TutorialBot {



    public static void main(String[] args) {
        Bot bot = new Bot();
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, bot),
                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.VERY_EASY))
                .launchStarcraft()
                .startGame(BattlenetMap.of("Triton LE"));

        while (s2Coordinator.update()) {
        }

        s2Coordinator.quit();
    }



    private static class Bot extends S2Agent {
        int step = 1;
        boolean stepReady = false;
        LinkedList<StructureToCreate> toBuild = new LinkedList<StructureToCreate>();

        Tag cc1Tag; //main cc
        Tag cc2Tag; //natural cc
        Tag scv1Tag; //first scv created
        Tag mineralPatch1Tag; //mineral patch near first base
        Tag mineralPatch2Tag; //mineral patch near 2nd base
        Tag barracksTag; //1st barracks
        Tag bunker1Tag; //bunker at front of natural
        Tag bunker2Tag; //bunker at reaper entrance
        Tag tempTag; //temporary tag


        public boolean afterTime(String time) {
            long seconds = convertStringToSeconds(time);
            return observation().getGameLoop()/22.4 > seconds;
        }

        boolean beforeTime(String time) {
            long seconds = convertStringToSeconds(time);
            return observation().getGameLoop()/22.4 < seconds;
        }

        long convertStringToSeconds(String time) {
            String[] arrTime = time.split(":");
            return Integer.parseInt(arrTime[0])*60 + Integer.parseInt(arrTime[1]);
        }
        String convertGameLoopToStringTime(long gameLoop) {
            return convertSecondsToString(Math.round(gameLoop/22.4));
        }

        String convertSecondsToString(long seconds) {
            return seconds/60 + ":" + String.format("%02d", seconds%60);
        }
        String currentGameTime() {
            return convertGameLoopToStringTime(observation().getGameLoop());
        }

        Unit findScvNearestBase(Unit cc) {
            return findNearestScv(cc.getPosition().toPoint2d(), true);
        }

        Unit findNearestScv(Point2d pt, boolean isHoldingMinerals) {
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

        Unit findNearestScv(Point2d pt) {
            return findNearestScv(pt, false);
        }


        @Override
        public void onGameStart() {
            Unit cc1Unit = observation().getUnits(Alliance.SELF, c -> c.unit().getType() == Units.TERRAN_COMMAND_CENTER).get(0).unit();

            //===== 1 ===== save closest mineral patch
            findNearestMineralPatch(cc1Unit.getPosition().toPoint2d()).ifPresent(mineralPatch ->
                    actions().unitCommand(cc1Unit, Abilities.RALLY_COMMAND_CENTER, mineralPatch, false));

            //===== 1 ===== rally cc to ramp
            actions().unitCommand(cc1Unit, Abilities.RALLY_COMMAND_CENTER, Point2d.of(145, 50), false);

            //===== 1 ===== save cc1Tag
            cc1Tag = cc1Unit.getTag();
            step = 2;
        }

        @Override
        public void onUnitCreated(UnitInPool unitInPool) {
            //===== 2 ===== rally cc to mineral line after first worker
            if (step == 2 && beforeTime("0:15") && observation().getUnits(Alliance.SELF).size() == 14) {
                stepReady = true;
            }
        }

        @Override
        public void onBuildingConstructionComplete(UnitInPool unitInPool) {
            Unit unit = unitInPool.unit();
            System.out.println(unit.getType().toString() + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() +
                    ") at: " + currentGameTime());

            //===== 5 ===== build 1st barracks after first depot
            if (step == 5 && beforeTime("0:45") && unit.getType() == Units.TERRAN_SUPPLY_DEPOT) {
                stepReady = true;
            }

            else if (beforeTime("1:45") && unit.getType() == Units.TERRAN_BARRACKS) {
                barracksTag = unit.getTag();
            }

            //===== 13 ===== rally all marines and barracks to the bunker, and save bunker tag
            else if (step == 13 && beforeTime("3:00") && unit.getType() == Units.TERRAN_BUNKER) {
                bunker1Tag = unit.getTag();
                stepReady = true;
            }
        }

        @Override
        public void onStep() {
            if (!toBuild.isEmpty()) {
                buildStructure(toBuild.poll());
            }

            //===== 4 ===== build 1st depot
            if (step == 4 && beforeTime("0:20") && observation().getMinerals() >= 100) {
                stepReady = true;
            }
            //===== 6 ===== rally cc to expansion location
            else if (step == 6 && beforeTime("1:00") && observation().getFoodUsed() == 17) {
                stepReady = true;
            }

            //===== 7 ===== rally cc back to mineral patch
            else if (step == 7 && beforeTime("1:15") && observation().getFoodUsed() == 18) {
                stepReady = true;
            }
            //===== 9 ===== build command center at natural
            else if (step == 9 && observation().getMinerals() >= 400) {
                stepReady = true;
            }
            //===== 12 ===== build command center at natural
            else if (step == 12 && observation().getMinerals() >= 100) {
                stepReady = true;
            }
            //===== 14 ===== build 2nd bunker
            else if (step == 14 && observation().getMinerals() >= 100) {
                stepReady = true;
            }
//            //===== 15 ===== build 2nd bunker
//            else if (step == 15 && observation().getMinerals() >= 100) {
//                actions().unitCommand(scv1Tag, Abilities.BUILD_BUNKER, Point2d.of(128.5f, 51.5f), false);
//                step = 16;
//            }

            if (stepReady) {
                stepReady = false;
                System.out.println("step: " + step);
                switch (step) {
                    //===== 2 ===== rally cc to mineral line after first worker
                    case 2:
                        Unit mineralPatch = observation().getUnit(mineralPatch1Tag).unit();
                        actions().unitCommand(cc1Tag, Abilities.RALLY_COMMAND_CENTER, mineralPatch, false);
                        break;
                    //===== 3 ===== save first scv
                    case 3:
                        //save scv id done within onUnitIdle()
                        break;
                    //===== 4 ===== build 1st depot
                    case 4:
                        toBuild.add(new StructureToCreate(observation(), Units.TERRAN_SUPPLY_DEPOT, LocationConstants.DEPOT1));
                        //actions().unitCommand(scv1Tag, Abilities.BUILD_SUPPLY_DEPOT, LocationConstants.DEPOT1, false);
                        break;
                    //===== 5 ===== build 1st barracks after first depot
                    case 5:
                        actions().unitCommand(scv1Tag, Abilities.BUILD_BARRACKS, LocationConstants.BARRACKS, false);
                        actions().unitCommand(scv1Tag, Abilities.MOVE, Point2d.of(128.5f, 51.5f), true);
                        break;
                    //===== 6 ===== rally cc to expansion location
                    case 6:
                        actions().unitCommand(cc1Tag, Abilities.RALLY_COMMAND_CENTER, Point2d.of(133.5f, 43.5f), false);
                        break;
                    //===== 7 ===== rally cc back to mineral patch
                    case 7:
                        Unit mineralPatchUnit = observation().getUnit(mineralPatch1Tag).unit();
                        actions().unitCommand(cc1Tag, Abilities.SMART, mineralPatchUnit, false);
                        break;
                    //===== 8 ===== waiting for 400min for cc
                    case 8:
                        break;
                    //===== 9 ===== build command center at natural
                    case 9:
                        actions().unitCommand(tempTag, Abilities.BUILD_COMMAND_CENTER, Point2d.of(133.5f, 43.5f),false);
                        tempTag = null;
                        break;
                   //===== 10 ===== waiting for scv to finish to start OC, and rally barracks to natural ramp
                    case 10:
                        actions().unitCommand(barracksTag, Abilities.RALLY_UNITS, Point2d.of(128.5f, 51.5f), false);
                        break;
                    //===== 11 ===== build OC
                    case 11:
                        actions().unitCommand(cc1Tag, Abilities.MORPH_ORBITAL_COMMAND, false);
                        tempTag = null;
                        break;
                    //===== 12 ===== waiting for 100min to build first bunker
                    case 12:
                        actions().unitCommand(scv1Tag, Abilities.BUILD_BUNKER, Point2d.of(128.5f, 51.5f), false);
                        break;
                    //===== 13 ===== rally all marines and barracks to the bunker, and save bunker tag
                    case 13:
                        Unit bunker = observation().getUnit(bunker1Tag).unit();
                        actions().unitCommand(barracksTag, Abilities.RALLY_UNITS, bunker, false); //rally barracks to bunker
                        List<UnitInPool> unitsInPool = observation().getUnits(Alliance.SELF, m -> m.unit().getType() == Units.TERRAN_MARINE);
                        List<Unit> marines = new ArrayList<>();
                        for (UnitInPool u : unitsInPool) {
                            marines.add(u.unit());
                        }
                        actions().unitCommand(marines, Abilities.SMART, bunker, false); //load marines in bunker
                        break;
                    //===== 14 ===== build 2nd bunker
                    case 14:
                        Unit scv = findScvNearestBase(observation().getUnit(cc1Tag).unit());
                        actions().unitCommand(scv, Abilities.HARVEST_RETURN, false)
                                .unitCommand(scv, Abilities.BUILD_BUNKER, Point2d.of(128.5f, 51.5f), true);
                        break;
                } //end switch
                step++;
            } // end if
        } // end method

        @Override
        public void onUnitIdle(UnitInPool unitInPool) {
            Unit unit = unitInPool.unit();
            switch ((Units) unit.getType()) {
                case TERRAN_COMMAND_CENTER:
                    //===== 11 ===== build OC
                    if (step == 11) {
                        stepReady = true;
                    }
                case TERRAN_ORBITAL_COMMAND:
                    //train scvs
                    if (step != 11) {
                        actions().unitCommand(unit, Abilities.TRAIN_SCV, false);
                    }
                    break;
                case TERRAN_SCV:
                    //===== 3 ===== save first scv
                    if (step == 3 && beforeTime("0:30")) {
                        stepReady = true;
                        scv1Tag = unit.getTag();
                    }

                    //===== 8 ===== build command center at natural
                    else if (step == 8 && beforeTime("1:30") && observation().getFoodUsed() == 18) {
                        tempTag = unit.getTag();
                        stepReady = true;
                    }
                    break;

               case TERRAN_BARRACKS:
                   //===== 10 ===== flag OC to start and save barracks tag
                    if (step == 10 && beforeTime("1:45")) {
                        stepReady = true;
                    }
                    //maintain 4 marine count for the first 5min of the game
                    else if (beforeTime("5:00") &&
                           ((bunker1Tag == null)?0:observation().getUnit(bunker1Tag).unit().getCargoSpaceTaken().get())
                           + observation().getUnits(Alliance.SELF, m -> m.unit().getType() == Units.TERRAN_MARINE).size() < 4) {
                        actions().unitCommand(unit, Abilities.TRAIN_MARINE, false);
                    }
                    break;
            }
        }

        private boolean tryBuildSupplyDepot() {
            // If we are not supply capped, don't build a supply depot.
            if (observation().getFoodUsed() <= observation().getFoodCap() - 2) {
                return false;
            }

            // Try and build a depot. Find a random TERRAN_SCV and give it the order.
            return tryBuildStructure(Abilities.BUILD_SUPPLY_DEPOT, Units.TERRAN_SCV);
        }

        private boolean tryBuildStructure(Ability abilityTypeForStructure, UnitType unitType) {
            // If a unit already is building a supply structure of this type, do nothing.
            if (!observation().getUnits(Alliance.SELF, doesBuildWith(abilityTypeForStructure)).isEmpty()) {
                return false;
            }

            // Just try a random location near the unit.
            Optional<UnitInPool> unitInPool = getRandomUnit(unitType);
            if (unitInPool.isPresent()) {
                Unit unit = unitInPool.get().unit();
                actions().unitCommand(
                        unit,
                        abilityTypeForStructure,
                        unit.getPosition().toPoint2d().add(Point2d.of(getRandomScalar(), getRandomScalar()).mul(15.0f)),
                        false);
                return true;
            } else {
                return false;
            }

        }

        //return true if going to build
        private boolean buildStructure(StructureToCreate buildMe) {
            UnitTypeData structureData = observation().getUnitTypeData(false).get(buildMe.structureType);
            Ability buildAction = structureData.getAbility().get();
            if (observation().getMinerals() >= structureData.getMineralCost().get() && observation().getVespene() >= structureData.getVespeneCost().get()) {
                actions()
                        .unitCommand(buildMe.getScv(), buildAction, buildMe.position, false)
                        .unitCommand(buildMe.getScv(), Abilities.SMART, buildMe.getRallyUnit(), true);
                return true;
            }
            else {
                return false;
            }
        }

        private Predicate<UnitInPool> doesBuildWith(Ability abilityTypeForStructure) {
            return unitInPool -> unitInPool.unit()
                    .getOrders()
                    .stream()
                    .anyMatch(unitOrder -> abilityTypeForStructure.equals(unitOrder.getAbility()));
        }

        private Optional<UnitInPool> getRandomUnit(UnitType unitType) {
            List<UnitInPool> units = observation().getUnits(Alliance.SELF, UnitInPool.isUnit(unitType));
            return units.isEmpty()
                    ? Optional.empty()
                    : Optional.of(units.get(ThreadLocalRandom.current().nextInt(units.size())));
        }

        private float getRandomScalar() {
            return ThreadLocalRandom.current().nextFloat() * 2 - 1;
        }

        private Optional<Unit> findNearestMineralPatch(Point2d start) {
            List<UnitInPool> units = observation().getUnits(Alliance.NEUTRAL);
            double distance = Double.MAX_VALUE;
            Unit target = null;
            for (UnitInPool unitInPool : units) {
                Unit unit = unitInPool.unit();
                if (unit.getType().equals(Units.NEUTRAL_MINERAL_FIELD)) {
                    double d = unit.getPosition().toPoint2d().distance(start);
                    if (d < distance) {
                        distance = d;
                        target = unit;
                    }
                }
            }
            mineralPatch1Tag = target.getTag();
            return Optional.ofNullable(target);
        }

        private boolean tryBuildBarracks() {
            if (countUnitType(Units.TERRAN_SUPPLY_DEPOT) < 1) {
                return false;
            }

            if (countUnitType(Units.TERRAN_BARRACKS) > 0) {
                return false;
            }

            return tryBuildStructure(Abilities.BUILD_BARRACKS, Units.TERRAN_SCV);
        }





        private int countUnitType(Units unitType) {
            return observation().getUnits(Alliance.SELF, UnitInPool.isUnit(unitType)).size();
        }

        // Tries to find a random location that can be pathed to on the map.
        // Returns Point2d if a new, random location has been found that is pathable by the unit.
        private Optional<Point2d> findEnemyPosition() {
            ResponseGameInfo gameInfo = observation().getGameInfo();

            Optional<StartRaw> startRaw = gameInfo.getStartRaw();
            if (startRaw.isPresent()) {
                Set<Point2d> startLocations = new HashSet<>(startRaw.get().getStartLocations());
                startLocations.remove(observation().getStartLocation().toPoint2d());
                if (startLocations.isEmpty()) return Optional.empty();
                return Optional.of(new ArrayList<>(startLocations)
                        .get(ThreadLocalRandom.current().nextInt(startLocations.size())));
            } else {
                return Optional.empty();
            }
        }
    }






    public static class StructureToCreate { //TODO: add rally point
        private Unit scv;
        private Units structureType;
        private Unit rallyUnit; //what to rally to afterwards (typically a mineral patch)
        private Point2d position;
        private boolean isPositionImportant;

        public static final Map<Units, Abilities> structureToActionMap;
        static {
            Hashtable<Units, Abilities> tmp = new Hashtable<Units, Abilities>();
            tmp.put(Units.TERRAN_BARRACKS, Abilities.BUILD_BARRACKS);
            tmp.put(Units.TERRAN_COMMAND_CENTER, Abilities.BUILD_COMMAND_CENTER);
            tmp.put(Units.TERRAN_SUPPLY_DEPOT, Abilities.BUILD_SUPPLY_DEPOT);
            tmp.put(Units.TERRAN_BUNKER, Abilities.BUILD_BUNKER);
            tmp.put(Units.TERRAN_ARMORY, Abilities.BUILD_ARMORY);
            tmp.put(Units.TERRAN_ENGINEERING_BAY, Abilities.BUILD_ENGINEERING_BAY);
            tmp.put(Units.TERRAN_FACTORY, Abilities.BUILD_FACTORY);
            tmp.put(Units.TERRAN_FUSION_CORE, Abilities.BUILD_FUSION_CORE);
            tmp.put(Units.TERRAN_GHOST_ACADEMY, Abilities.BUILD_GHOST_ACADEMY);
            tmp.put(Units.TERRAN_MISSILE_TURRET, Abilities.BUILD_MISSILE_TURRET);
            tmp.put(Units.TERRAN_REFINERY, Abilities.BUILD_REFINERY);
            tmp.put(Units.TERRAN_SENSOR_TOWER, Abilities.BUILD_SENSOR_TOWER);
            tmp.put(Units.TERRAN_STARPORT, Abilities.BUILD_STARPORT);
            structureToActionMap = Collections.unmodifiableMap(tmp);
        }

        //===== Constructors =====
        public StructureToCreate(ObservationInterface obs, Units structureType, Unit rallyUnit, Point2d position) {
            this(obs, null, structureType, rallyUnit, position, true);
        }
        public StructureToCreate(ObservationInterface obs, Units structureType, Point2d position) {
            this(obs, null, structureType, null, position, true);
        }
        public StructureToCreate(ObservationInterface obs, Units structureType, Unit rallyUnit) {
            this(obs, null, structureType, rallyUnit, null, false);
        }
        public StructureToCreate(ObservationInterface obs, Units structureType) {
            this(obs, null, structureType, null, null, false);
        }
        public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Point2d position) {
            this(obs, scv, structureType, null, position, true);
        }
        public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit) {
            this(obs, scv, structureType, rallyUnit, null, false);
        }
        public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType) {
            this(obs, scv, structureType, null, null, false);
        }
        public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit, Point2d position) {
            this(obs, scv, structureType, rallyUnit, position, true);
        }
        public StructureToCreate(ObservationInterface obs, Unit scv, Units structureType, Unit rallyUnit, Point2d position, boolean isPositionImportant) {
            this.scv = scv;
            this.structureType = structureType;
            this.rallyUnit = rallyUnit;
            this.position = position;
            this.isPositionImportant = isPositionImportant;

            if (this.scv == null) {
                findNearestScv(obs);
            }

            if (this.position == null) {

            }
        }

        //===== Getters/Setters =====
        public Unit getScv() {
            return scv;
        }

        public void setScv(Unit scv) {
            this.scv = scv;
        }

        public Units getStructureType() {
            return structureType;
        }

        public void setStructureType(Units structureType) {
            this.structureType = structureType;
        }

        public Unit getRallyUnit() {
            return rallyUnit;
        }

        public void setRallyUnit(Units rallyUnit) {
            rallyUnit = rallyUnit;
        }

        public Point2d getPosition() {
            return position;
        }

        public void setPosition(Point2d position) {
            this.position = position;
        }

        public boolean isPositionImportant() {
            return isPositionImportant;
        }

        public void setPositionImportant(boolean positionImportant) {
            isPositionImportant = positionImportant;
        }



        //========= methods =========

        //sets this.scv and this.rallyUnit fields if null in constructor
        private void findNearestScv(ObservationInterface obs) {  //TODO: null handling
            List<UnitInPool> scvList;
            scvList = obs.getUnits(Alliance.SELF, scv -> scv.unit().getType() == Units.TERRAN_SCV && //is scv
                    !scv.unit().getOrders().isEmpty() && //not idle
                    scv.unit().getOrders().get(0).getAbility() != Abilities.HARVEST_RETURN && // is not returning minerals
                    (obs.getUnit(scv.unit().getOrders().get(0).getTargetedUnitTag().get()).unit().getType() == Units.NEUTRAL_MINERAL_FIELD ||
                    obs.getUnit(scv.unit().getOrders().get(0).getTargetedUnitTag().get()).unit().getType() == Units.NEUTRAL_MINERAL_FIELD750 ||
                    obs.getUnit(scv.unit().getOrders().get(0).getTargetedUnitTag().get()).unit().getType() == Units.NEUTRAL_RICH_MINERAL_FIELD ||
                    obs.getUnit(scv.unit().getOrders().get(0).getTargetedUnitTag().get()).unit().getType() == Units.NEUTRAL_RICH_MINERAL_FIELD750)); //is mining minerals

            UnitInPool closestScv = scvList.get(0);
            double closestDistance = this.position.distance(closestScv.unit().getPosition().toPoint2d());
            scvList.remove(0);
            for (UnitInPool scv : scvList) {
                double curDistance = this.position.distance(scv.unit().getPosition().toPoint2d());
                if (curDistance < closestDistance) {
                    closestScv = scv;
                    closestDistance = curDistance;
                }
            }
            this.scv = closestScv.unit();

            if (closestScv.unit().getOrders().isEmpty()) {
                this.rallyUnit = findNearestMineralPatch(obs).get();
            }
            else {
                this.rallyUnit = obs.getUnit(
                        closestScv.unit().getOrders().get(0).getTargetedUnitTag().get()
                ).unit();
            }
        }

        //finds nearest mineral patch to the structure position
        private Optional<Unit> findNearestMineralPatch(ObservationInterface obs) {
            List<UnitInPool> units = obs.getUnits(Alliance.NEUTRAL);
            double distance = Double.MAX_VALUE;
            Unit target = null;
            for (UnitInPool unitInPool : units) {
                Unit unit = unitInPool.unit();
                if (unit.getType().equals(Units.NEUTRAL_MINERAL_FIELD)) {
                    double d = unit.getPosition().toPoint2d().distance(this.position);
                    if (d < distance) {
                        distance = d;
                        target = unit;
                    }
                }
            }
            return Optional.ofNullable(target);
        }
    } //end StructureToCreate class

/*    private static class MapCoordinates {
        public MapCoordinates(String mapName, String spawm) {
            if mapName.equals(MapNames.TRITON) {
                //TODO: spawn location

            }
        }
    }
*/
    }