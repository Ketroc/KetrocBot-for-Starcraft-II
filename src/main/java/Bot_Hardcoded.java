import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.response.ResponseGameInfo;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

class Bot_Hardcoded extends S2Agent {
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
        boolean isTopSpawn = (cc1Unit.getPosition().getY() > 100) ? true : false;
        LocationConstants.init(MapNames.TRITON, isTopSpawn);

        //===== 1 ===== save closest mineral patch
        findNearestMineralPatch(cc1Unit.getPosition().toPoint2d()).ifPresent(mineralPatch ->
                actions().unitCommand(cc1Unit, Abilities.RALLY_COMMAND_CENTER, mineralPatch, false));

        //===== 1 ===== rally cc to ramp
        actions().unitCommand(cc1Unit, Abilities.RALLY_COMMAND_CENTER, LocationConstants.DEPOT1, false);

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
            //===== 15 ===== build 2nd bunker
            else if (step == 15 && observation().getMinerals() >= 100) {
                actions().unitCommand(scv1Tag, Abilities.BUILD_BUNKER, LocationConstants.BUNKER2, false);
                step = 16;
            }

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
                    toBuild.add(new StructureToCreate(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.DEPOT1));
                    //actions().unitCommand(scv1Tag, Abilities.BUILD_SUPPLY_DEPOT, LocationConstants.DEPOT1, false);
                    break;
                //===== 5 ===== build 1st barracks after first depot
                case 5:
                    actions().unitCommand(scv1Tag, Abilities.BUILD_BARRACKS, LocationConstants.BARRACKS, false);
                    actions().unitCommand(scv1Tag, Abilities.MOVE, LocationConstants.BUNKER1, true);
                    break;
                //===== 6 ===== rally cc to expansion location
                case 6:
//                    actions().unitCommand(cc1Tag, Abilities.RALLY_COMMAND_CENTER, LocationConstants.CC2, false);
                    break;
                //===== 7 ===== rally cc back to mineral patch
                case 7:
                    Unit mineralPatchUnit = observation().getUnit(mineralPatch1Tag).unit();
//                    actions().unitCommand(cc1Tag, Abilities.SMART, mineralPatchUnit, false);
                    break;
                //===== 8 ===== waiting for 400min for cc
                case 8:
                    break;
                //===== 9 ===== build command center at natural
                case 9:
 //                   actions().unitCommand(tempTag, Abilities.BUILD_COMMAND_CENTER, LocationConstants.CC2,false);
                    tempTag = null;
                    break;
               //===== 10 ===== waiting for scv to finish to start OC, and rally barracks to natural ramp
                case 10:
                    actions().unitCommand(barracksTag, Abilities.RALLY_UNITS, LocationConstants.BUNKER2, false);
                    break;
                //===== 11 ===== build OC
                case 11:
                    actions().unitCommand(cc1Tag, Abilities.MORPH_ORBITAL_COMMAND, false);
                    tempTag = null;
                    break;
                //===== 12 ===== waiting for 100min to build first bunker
                case 12:
                    actions().unitCommand(scv1Tag, Abilities.BUILD_BUNKER, LocationConstants.BUNKER1, false);
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
                            .unitCommand(scv, Abilities.BUILD_BUNKER, LocationConstants.BUNKER2, true);
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
        UnitTypeData structureData = observation().getUnitTypeData(false).get(buildMe.getStructureType());
        Ability buildAction = structureData.getAbility().get();
        if (observation().getMinerals() >= structureData.getMineralCost().get() && observation().getVespene() >= structureData.getVespeneCost().get()) {
            actions()
                    .unitCommand(buildMe.getScv(), buildAction, buildMe.getPosition(), false)
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
