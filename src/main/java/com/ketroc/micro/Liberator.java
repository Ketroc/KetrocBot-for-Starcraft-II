package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.geometry.Position;
import com.ketroc.utils.Print;
import com.ketroc.utils.UnitUtils;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Liberator extends BasicUnitMicro {
    public static int castRange = 5;

    protected Point2d plannedLibZonePos;
    protected Point2d curLibZonePos;

    public Liberator(UnitInPool unit, Point2d targetPos, Point2d plannedLibZonePos) {
        super(unit, targetPos, MicroPriority.SURVIVAL);
        this.plannedLibZonePos = plannedLibZonePos;
    }

    @Override
    public UnitInPool selectTarget() {
        Unit lib = unit.unit();

        //use basic micro for unsieged liberator
        if (lib.getType() == Units.TERRAN_LIBERATOR) {
            return super.selectTarget();
        }

        List<UnitInPool> enemyTargets = UnitUtils.getEnemyGroundTargetsNear(curLibZonePos, 5);
        int libDamage = getLibDamage();
        float bestRemainder = Float.MAX_VALUE;
        UnitInPool bestTargetUnit = null;
        for (UnitInPool enemy : enemyTargets) {
            //ignore structures
            if (UnitUtils.isStructure((Units)enemy.unit().getType())) {
                continue;
            }

            //always target immortals (without barrier) first
            if (enemy.unit().getType() == Units.PROTOSS_IMMORTAL) {
                return enemy;
            }

            //subtract enemy armor (assume max upgrades) TODO: determine enemy upgrades
            libDamage -= (Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType()).getArmor().orElse(0f) + 3);
            float enemyHp = enemy.unit().getHealth().orElse(1f) + enemy.unit().getShield().orElse(0f);
            float remainder = enemyHp % libDamage;
            if (enemyHp > libDamage) { //hack to give preference to 1-shot kills
                remainder += 15;
            }
            if (remainder < bestRemainder) {
                bestRemainder = remainder;
                bestTargetUnit = enemy;
            }
        }
        return bestTargetUnit;
    }

    protected boolean siegeUpMicro() {
        //siege up in response to enemy spotted
        List<Point2d> enemiesInRange = getEnemiesInRange();
        if (!enemiesInRange.isEmpty()) {
            Point2d enemiesMidPoint = Position.midPoint(enemiesInRange);
            Point2d newLibZonePos = UnitUtils.getDistance(unit.unit(), enemiesMidPoint) < castRange + 1
                    ? enemiesMidPoint
                    : Position.towards(unit.unit().getPosition().toPoint2d(), enemiesMidPoint, castRange + 1);
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_LIBERATOR_AG_MODE, newLibZonePos, false);
            curLibZonePos = newLibZonePos;
            Print.print("liberator: " + unit.unit().getPosition().toPoint2d());
            Print.print("enemiesMidPoint: " + enemiesMidPoint);
            Print.print("newLibZonePos: " + newLibZonePos);
            return true;
        }

        //siege up at pre-determined position
        if (isLibAtPlannedPosition()) {
            plannedLibZonePos = Position.towards(targetPos, plannedLibZonePos, castRange); //update libZonePos (in case lib range finished en route)
            ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_LIBERATOR_AG_MODE, plannedLibZonePos, true);
            curLibZonePos = plannedLibZonePos;
            return true;
        }
        return false;
    }

    private boolean isLibAtPlannedPosition() {
        return UnitUtils.getDistance(unit.unit(), targetPos) <= 2.5f && plannedLibZonePos != null;
    }

    protected boolean unsiegeMicro() {
        //don't unsiege if at a planned position
        if (isLibAtPlannedPosition()) {
            return false;
        }

        //unsiege if no enemies left in libZone
        if (unit.unit().getWeaponCooldown().orElse(1f) == 0f &&
                UnitUtils.getDistance(unit.unit(), targetPos) > 1 &&
                getEnemiesInRange().isEmpty()) { //TODO: change to check lib zone only
            ActionHelper.unitCommand(unit.unit(), Abilities.MORPH_LIBERATOR_AA_MODE, false);
            return true;
        }
        return false;
    }

    protected List<Point2d> getEnemiesInRange() {
        int range = castRange + 5; //circle radius is 5

        Predicate<UnitInPool> enemiesInRangeFilter = enemy ->
                !UnitUtils.isStructure(enemy.unit().getType()) &&
                        enemy.unit().getType() != Units.TERRAN_AUTO_TURRET &&
                        UnitUtils.getDistance(enemy.unit(), unit.unit()) <= range + enemy.unit().getRadius() &&
                        !enemy.unit().getFlying().orElse(true) &&
                        !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        !enemy.unit().getHallucination().orElse(false) &&
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE;

        return Bot.OBS.getUnits(Alliance.ENEMY, enemiesInRangeFilter)
                .stream()
                .map(enemy -> enemy.unit().getPosition().toPoint2d())
                .collect(Collectors.toList());
    }

    protected boolean isMorphing() {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(unitOrder -> unitOrder.ability == Abilities.MORPH_LIBERATOR_AG_MODE ||
                        unitOrder.ability == Abilities.MORPH_LIBERATOR_AA_MODE) ||
                unit.unit().getOrders().stream()
                        .anyMatch(unitOrder -> unitOrder.getAbility() == Abilities.MORPH_LIBERATOR_AG_MODE ||
                                unitOrder.getAbility() == Abilities.MORPH_LIBERATOR_AA_MODE);
    }


    private static int getLibDamage() {
        if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3)) {
            return 90;
        }
        else if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL2)) {
            return 85;
        }
        else if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL1)) {
            return 80;
        }
        else {
            return 75;
        }
    }
}
