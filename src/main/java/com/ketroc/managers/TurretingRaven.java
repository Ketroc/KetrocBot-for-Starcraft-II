package com.ketroc.managers;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.geometry.Position;
import com.ketroc.micro.MicroPriority;
import com.ketroc.micro.Raven;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.PlacementMap;
import com.ketroc.utils.UnitUtils;

import java.util.*;
import java.util.stream.Collectors;

public class TurretingRaven {
    public static Set<TurretingRaven> list = new HashSet<>();
    private static float attackRange = 7f;
    private static float castRange = 2.5f;

    private Unit raven;
    private List<Point2d> posList;


    public TurretingRaven(Unit raven, Point2d targetPos) {
        this.raven = raven;
        posList = getPotentialPositions(targetPos);
    }

    public List<Point2d> getPotentialPositions(Point2d targetPos) {
        List<Point2d> posList = new ArrayList<>();
        float distanceToTarget = UnitUtils.getDistance(raven, targetPos);
        float towardsDistance = distanceToTarget > attackRange + castRange ? attackRange : distanceToTarget - castRange;
        Point2d placementPos = Position.towards(targetPos, raven.getPosition().toPoint2d(), towardsDistance);
        int xPos = (int)placementPos.getX();
        int yPos = (int)placementPos.getY();
        int startX = xPos-4;
        int endX = xPos+4;
        int startY = yPos-4;
        int endY = yPos+4;

        //add every position that is placeable and within attack range
        for (int x=startX; x<=endX; x++) {
            for (int y=startY; y<=endY; y++) {
                if (PlacementMap.canFit2x2(x, y)) {
                    Point2d pos = Point2d.of(x, y);
                    if (targetPos.distance(pos) < attackRange) {
                        posList.add(pos);
                    }
                }
            }
        }

        //sort by closest to target (with positions in cast range first)
        posList.sort(Comparator.comparing(p -> p.distance(targetPos) + (UnitUtils.getDistance(raven, p) > castRange ? 99 : 0)));
        return posList;
    }

    public static void onHiSecComplete() {
        attackRange = 8f;
    }

    public static void onStepStart() {
        list.clear();
    }

    public static void onStepEnd() {
        if (list.isEmpty()) {
            return;
        }

        List<Point2d> validPosList = getAllValidPos();
        for (TurretingRaven turRaven : list) {
            turRaven.posList.stream()
                    .filter(pos -> validPosList.contains(pos))
                    .findFirst()
                    .ifPresentOrElse(
                            pos -> {
                                ActionHelper.unitCommand(turRaven.raven, Abilities.EFFECT_AUTO_TURRET, pos, false);
                                //remove all valid positions blocked by this autoturret
                                validPosList.remove(pos);
                                validPosList.remove(pos.add(1, 0));
                                validPosList.remove(pos.add(0, 1));
                                validPosList.remove(pos.add(1, 1));
                                validPosList.remove(pos.add(-1, 0));
                                validPosList.remove(pos.add(0, -1));
                                validPosList.remove(pos.add(-1, -1));
                            },
                            () -> new Raven(turRaven.raven, ArmyManager.retreatPos, MicroPriority.SURVIVAL)
                    );
        }
    }

    public static List<Point2d> getAllValidPos() {
        //build list of all potential positions from all ravens (no duplicates)
        List<Point2d> allPosList = list.stream()
                .flatMap(turretingRaven -> turretingRaven.posList.stream())
                .distinct()
                .collect(Collectors.toList());
        if (allPosList.isEmpty()) {
            return allPosList;
        }

        //do batch query of all positions
        List<QueryBuildingPlacement> queryList = allPosList.stream()
                .map(p -> QueryBuildingPlacement
                        .placeBuilding()
                        .useAbility(Abilities.EFFECT_AUTO_TURRET)
                        .on(p).build())
                .collect(Collectors.toList());
        List<Boolean> isValidList = Bot.QUERY.placement(queryList);

        //remove all positions with invalid placement
        for (int i=isValidList.size()-1; i>=0; i--) {
            if (!isValidList.get(i)) {
                allPosList.remove(i);
            }
        }

        return allPosList;
    }

    public static void add(Unit raven, Point2d targetPos) {
        list.add(new TurretingRaven(raven, targetPos));
    }
}
