package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.Position;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Infestor {
    private UnitInPool infestor;
    private float prevEnergy;

    public static List<Infestor> infestorList = new ArrayList<>();
    public static List<IncomingFungal> incomingFungalList = new ArrayList<>();

    public Infestor(UnitInPool infestor) {
        this.infestor = infestor;
        prevEnergy = infestor.unit().getEnergy().orElse(0f);
    }

    public static void onStep() {
        //update prev energy & check if energy 74-75 less than prev frame
        for (Infestor inf : infestorList) {
            if (inf.prevEnergy - inf.infestor.unit().getEnergy().orElse(inf.prevEnergy) > 73 &&
                    inf.prevEnergy - inf.infestor.unit().getEnergy().get() < 76) {
                dodgefungal(inf.infestor.unit());
            }
            inf.prevEnergy = inf.infestor.unit().getEnergy().orElse(inf.prevEnergy);
        }

        //add new infestors
        List<Unit> visibleInfestors = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.INFESTOR_TYPE);
        visibleInfestors.stream()
                .filter(infestor -> !isInfestorInList(infestor))
                .forEach(unit -> infestorList.add(new Infestor(Bot.OBS.getUnit(unit.getTag()))));

        //remove expired fungals
        incomingFungalList = incomingFungalList.stream()
                .filter(fungal -> !fungal.isExpired())
                .collect(Collectors.toList());


        //map incoming fungals
        incomingFungalList.stream()
                .forEach(fungal -> GameCache.enemyMappingList.add(new EnemyUnit(fungal.position, true)));


    }

    private static void dodgefungal(Unit infestor) {
        //manual dodge
        //get all banshee/viking/raven in fungal radius
        Set<Units> airUnitTypes = Set.of(Units.TERRAN_BANSHEE, Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_RAVEN);
        List<UnitInPool> airUnitsInFungalRange = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, airUnitTypes, infestor.getPosition().toPoint2d(), 15);
        airUnitsInFungalRange.stream()
                .forEach(unit -> {
                    Ignored.add(new IgnoredFungalDodger(unit.getTag()));
                    Point2d dodgePoint = Position.towards(unit.unit().getPosition().toPoint2d(), infestor.getPosition().toPoint2d(), -3);
                    Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, dodgePoint, false);
                    switch ((Units)unit.unit().getType()) {
                        case TERRAN_BANSHEE:
                            GameCache.bansheeList.remove(unit.unit());
                            break;
                        case TERRAN_VIKING_FIGHTER:
                            GameCache.vikingList.remove(unit.unit());
                            break;
                        case TERRAN_RAVEN:
                            GameCache.ravenList.remove(unit.unit());
                            break;
                    }
                });



//        THIS CODE WAS FOR GUESSING FUNGAL_POSITION BASED ON THE INFESTOR'S getFacing()
//        Point2d fungalPos = calcFungalPos(infestor);
//
//        //add to enemy mapping list
//        incomingFungalList.add(new IncomingFungal(fungalPos));
//
//        //manual dodge
//        //get all banshee/viking/raven in fungal radius
//        List<Units> airUnitTypes = List.of(Units.TERRAN_BANSHEE, Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_RAVEN);
//        List<UnitInPool> airUnitsInFungal = Bot.OBS.getUnits(Alliance.SELF, unit -> {
//            float distance = UnitUtils.getDistance(unit.unit(), fungalPos);
//            return airUnitTypes.contains(unit.unit().getType()) &&
//                distance < 3 && distance > 0.5;
//        });
//        airUnitsInFungal.stream()
//                .forEach(unit -> {
//                    IgnoredUnit.ignoredUnits.add(new IgnoredFungalDodge(unit.getTag()));
//                    Point2d dodgePoint = Position.towards(unit.unit().getPosition().toPoint2d(), fungalPos, -3);
//                    Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, dodgePoint, false);
//                    switch ((Units)unit.unit().getType()) {
//                        case TERRAN_BANSHEE:
//                            GameCache.bansheeList.remove(unit.unit());
//                            break;
//                        case TERRAN_VIKING_FIGHTER:
//                            GameCache.vikingList.remove(unit.unit());
//                            break;
//                        case TERRAN_RAVEN:
//                            GameCache.ravenList.remove(unit.unit());
//                            break;
//                    }
//                });

    }

    private static Point2d calcFungalPos(Unit infestor) {
        Point2d infestorPos = infestor.getPosition().toPoint2d();
        double direction = Math.toDegrees(infestor.getFacing()); //TODO: check assumption that 0RAD == east
        float distance = 10;

        Point2d originPoint = Point2d.of(infestorPos.getX()+distance, infestorPos.getY());

        //find destination Point
        return Position.rotate(originPoint, infestorPos, direction);
    }


    public static boolean isInfestorInList(Unit newInfestor) {
        return infestorList.stream()
                .anyMatch(infestor -> infestor.infestor.unit().getTag().equals(newInfestor.getTag()));
    }
}
