package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.micro.*;
import com.ketroc.terranbot.micro.Target;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.DefenseUnitPositions;
import com.ketroc.terranbot.models.StructureScv;
import com.ketroc.terranbot.strategies.CannonRushDefense;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.*;

import java.util.*;
import java.util.stream.Collectors;

public class ArmyManager {
    public static boolean doOffense;
    public static Point2d retreatPos;
    public static Point2d attackGroundPos;
    public static Point2d attackAirPos;
    public static Unit attackUnit;
    public static boolean isAttackUnitRetreating;

    public static List<Unit> armyGoingHome;
    public static List<Unit> armyGroundAttacking;
    public static List<Unit> armyAirAttacking;

    public static Point2d groundAttackersMidPoint;
    public static Point2d vikingMidPoint;

    public static long prevSeekerFrame;
    public static int numAutoturretsAvailable;
    public static int turretsCast;
    public static int queriesMade;

    public static void onStep() {
        //set midpoints
        setArmyMidpoints();

        //set offense decision
        setDoOffense();

        //set defense position
        setAirTarget();
        if (doOffense) {
            setGroundTarget();
        }
        else {
            setDefensePosition();
        }
        setIsAttackUnitRetreating();

        // lift & lower depot walls
        raiseAndLowerDepots();

        //respond to nydus
        nydusResponse();

        //positioning siege tanks && tank targetting
        if (BunkerContain.proxyBunkerLevel != 2) {
            positionTanks();
        }

        //position liberators
        positionLiberators();

        //empty bunker after PF at natural is done
        salvageBunkerAtNatural();

        //position marines
        if (BunkerContain.proxyBunkerLevel == 0) {
            positionMarines();
        }

        //repair station
        manageRepairBay();

        //if searching for last structures
        if (attackGroundPos == null && Switches.finishHim) {
            searchForLastStructures();
        }
        else {
            armyGoingHome = new ArrayList<>();
            armyGroundAttacking = new ArrayList<>();
            armyAirAttacking = new ArrayList<>();

            bansheeMicro();
            vikingMicro();
            ravenMicro();

            //send actions
            if (!armyGoingHome.isEmpty()) {
                Bot.ACTION.unitCommand(armyGoingHome, Abilities.MOVE, retreatPos, false);
            }
            if (!armyGroundAttacking.isEmpty()) {
                Point2d targetPos = attackGroundPos;
                Bot.ACTION.unitCommand(armyGroundAttacking, Abilities.ATTACK, targetPos, false);
            }
            if (!armyAirAttacking.isEmpty()) {
                Point2d targetPos = attackAirPos;
                Bot.ACTION.unitCommand(armyAirAttacking, Abilities.ATTACK, targetPos, false);
            }

            pfTargetting();
            libTargetting();
            autoturretTargetting();
        }

        //send out marine+hellbat army
        sendMarinesHellbats();
    }

    private static void setIsAttackUnitRetreating() {
        isAttackUnitRetreating = false;
        if (attackUnit != null && UnitUtils.canMove(attackUnit.getType())) {
            float facing = (float)Math.toDegrees(attackUnit.getFacing());
            float attackAngle = Position.getAngle(attackUnit.getPosition().toPoint2d(), groundAttackersMidPoint);
            float angleDiff = Position.getAngleDifference(facing, attackAngle);
            isAttackUnitRetreating = angleDiff < 50;
        }
    }

    private static void libTargetting() {
        for (Base base : GameCache.baseList) {
            for (DefenseUnitPositions libPos : base.getLiberators()) {
                if (libPos.getUnit() != null && libPos.getUnit().isAlive()) {
                    Unit lib = libPos.getUnit().unit();
                    if (lib.getType() == Units.TERRAN_LIBERATOR_AG && lib.getWeaponCooldown().orElse(1f) == 0f) {
                        Point2d libZone = Position.towards(lib.getPosition().toPoint2d(), base.getCcPos(), 5);
                        Unit targetUnit = getLibTarget(lib, libZone);
                        if (targetUnit != null) {
                            Bot.ACTION.unitCommand(lib, Abilities.ATTACK, targetUnit, false);
                        }
                    }
                }
            }
        }
    }

    private static Unit getLibTarget(Unit lib, Point2d libZone) {
        List<UnitInPool> enemyTargets = UnitUtils.getEnemyTargetsNear(libZone, 5);
        int libDamage = getLibDamage(lib);
        float bestRemainder = Float.MAX_VALUE;
        Unit bestTargetUnit = null;
        for (UnitInPool enemy : enemyTargets) {
            //always target immortals (without barrier) first
            if (enemy.unit().getType() == Units.PROTOSS_IMMORTAL) {
                return enemy.unit();
            }

            //subtract enemy armor (assume max upgrades) TODO: determine enemy upgrades
            libDamage -= (Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType()).getArmor().orElse(0f) + 3);
            float enemyHp = enemy.unit().getHealth().orElse(1f) + enemy.unit().getShield().orElse(0f);
            float remainder = enemyHp % libDamage;
            if (enemyHp > libDamage) { //preference to 1-shot kills
                remainder += 15;
            }
            if (remainder < bestRemainder) {
                bestRemainder = remainder;
                bestTargetUnit = enemy.unit();
            }
        }
        return bestTargetUnit;
    }

    private static int getLibDamage(Unit lib) {
        if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3)) {
            return 90;
        }
        else if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3)) {
            return 85;
        }
        else if (Bot.OBS.getUpgrades().contains(Upgrades.TERRAN_SHIP_WEAPONS_LEVEL3)) {
            return 80;
        }
        else {
            return 75;
        }
    }

    private static void setArmyMidpoints() {
        vikingMidPoint = (!GameCache.vikingList.isEmpty())
                ? Position.midPointUnitsMedian(GameCache.vikingList)
                : LocationConstants.baseLocations.get(0);
        List<Unit> groundAttackers = (Strategy.MASS_RAVENS) ? GameCache.ravenList : GameCache.bansheeList;
        groundAttackersMidPoint = (!groundAttackers.isEmpty())
                ? Position.midPointUnitsMedian(groundAttackers)
                : LocationConstants.baseLocations.get(0);
    }

    private static void searchForLastStructures() {
        spreadArmy(GameCache.bansheeList);
        spreadArmy(GameCache.vikingList);
    }

    private static void sendMarinesHellbats() {
        if (Cost.isGasBroke()) {
            List<Unit> army = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
            army.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_HELLION_TANK));
            if (army.size() >= 20 || Bot.OBS.getFoodUsed() >= 199 || Cost.isMineralBroke()) {
                if (army.stream().anyMatch(unit -> !unit.getOrders().isEmpty())) {
                    Bot.ACTION.unitCommand(army, Abilities.ATTACK, attackGroundPos, false);
                }
            }
        }
    }

    private static void autoturretTargetting() {
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_AUTO_TURRET).stream()
                .filter(turret -> turret.getWeaponCooldown().orElse(1f) == 0)
                .forEach(turret -> {
                    selectTarget(turret).ifPresent(target ->
                            Bot.ACTION.unitCommand(turret, Abilities.ATTACK, target, false));
                });
    }

    private static Optional<Unit> selectTarget(Unit turret) {
        List<UnitInPool> enemiesInRange = UnitUtils.getEnemyTargetsNear(turret, 8);

        Target bestTarget = new Target(null, Float.MAX_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
        for (UnitInPool enemy : enemiesInRange) {
            float enemyHP = enemy.unit().getHealth().orElse(0f) +
                    (enemy.unit().getShield().orElse(0f) * 1.5f);
            float damageMultiple = getDamageMultiple(enemy.unit());
            UnitTypeData enemyData = Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType());
            float enemyCost;
            if (enemy.unit().getType() == UnitUtils.enemyWorkerType) { //inflate value of workers as they impact income
                enemyCost = 75;
            }
            else {
                enemyCost = enemyData.getMineralCost().orElse(1) + (enemyData.getVespeneCost().orElse(1) * 1.2f); //value gas more than minerals
            }
            float enemyValue = enemyHP/(enemyCost*damageMultiple);
            if (enemyValue < bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                bestTarget.update(enemy, enemyValue, enemyHP);
            }
        }
        return (bestTarget.unit == null) ? Optional.empty() : Optional.of(bestTarget.unit.unit());
    }

    private static float getDamageMultiple(Unit enemy) {
        switch ((Units)enemy.getType()) {
            case PROTOSS_IMMORTAL:
                return 2;
            case ZERG_BANELING:
                return 2;
            case ZERG_INFESTOR: case ZERG_INFESTOR_BURROWED:
                return 1.5f;
            case TERRAN_GHOST:
                return 1.5f;
        }
        return 1;
    }

    private static void bansheeMicro() {
        //give banshee divers command
        if (Switches.bansheeDiveTarget != null) {
            if (!giveDiversCommand(GameCache.bansheeDivers, Switches.bansheeDiveTarget)) {
                Switches.bansheeDiveTarget = null;
            }
        }

        //give normal banshees their commands
        for (Unit banshee : GameCache.bansheeList) {
            giveBansheeCommand(banshee);
        }
    }

    private static void vikingMicro() {
        //give viking divers commands
        vikingDiverMicro();

        //give normal banshees their commands
        for (Unit viking : GameCache.vikingList) {
            giveVikingCommand(viking);
        }
    }

    private static void vikingDiverMicro() {
        if (Switches.vikingDiveTarget != null) {
            if (Switches.isDivingTempests) {
                List<Unit> moveVikings = new ArrayList<>();
                List<Unit> attackVikings = new ArrayList<>();
                if (!UnitUtils.isVisible(Switches.vikingDiveTarget)) { //TODO: handle it when vikings arrive at last known tempest location and still can't find the tempest
                    moveVikings.addAll(GameCache.vikingDivers);
                }
                else {
                    for (Unit viking : GameCache.vikingDivers) {
                        if (viking.getWeaponCooldown().get() == 0 && UnitUtils.getDistance(viking, Switches.vikingDiveTarget.unit()) < 8.5) {
                            attackVikings.add(viking);
                        } else {
                            moveVikings.add(viking);
                        }
                    }
                }
                if (!attackVikings.isEmpty()) {
                    if (Switches.vikingDiveTarget.unit().getCloakState().get() == CloakState.CLOAKED) {
                        //scan behind the tempest
                        if (UnitUtils.canScan()) {
                            List<Unit> orbitals = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
                            Bot.ACTION.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -5), false);
                        }
                        Bot.ACTION.unitCommand(attackVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
                    }
                    else {
                        Bot.ACTION.unitCommand(attackVikings, Abilities.ATTACK, Switches.vikingDiveTarget.unit(), false);
                    }
                }
                if (!moveVikings.isEmpty()) {
                    Bot.ACTION.unitCommand(moveVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
                }
            }
            else {
                if (!giveDiversCommand(GameCache.vikingDivers, Switches.vikingDiveTarget)) {
                    Switches.vikingDiveTarget = null;
                }
            }
        }
    }

    private static void setDoOffense() {
        if (Strategy.MASS_RAVENS) {
            numAutoturretsAvailable = GameCache.ravenList.stream()
                    .mapToInt(raven -> raven.getEnergy().orElse(0f).intValue() / 50)
                    .sum();
            if (!doOffense && numAutoturretsAvailable > 25) {
                doOffense = true;
            }
            else if (doOffense && numAutoturretsAvailable < 6) {
                doOffense = false;
            }
        }
        else {
            doOffense = (Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                    GameCache.bansheeList.size() > 1);
        }
    }

    private static void ravenMicro() {
        turretsCast = 0;
        queriesMade = 0;

        //give ravens their commands
        for (Unit raven : GameCache.ravenList) {
            giveRavenCommand(raven, queriesMade <= 6);
        }
    }

    //set defensePos to closestEnemy to an injured PF
    private static void setDefensePosition() {
        attackUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(u -> GameCache.baseList.stream()
                                .filter(Base::isMyBase)
                                .anyMatch(base -> UnitUtils.getDistance(u.unit(), base.getCcPos()) < 25) && //close to any of my bases
                        !UnitUtils.NO_THREAT_ENEMY_AIR.contains(u.unit().getType()) &&
                        u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED && //ignore cloaked units
                        !u.unit().getBurrowed().orElse(false) && //ignore burrowed units
                        u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                        u.unit().getType() != Units.ZERG_BROODLING && //ignore broodlings
                        !u.unit().getHallucination().orElse(false) && //ignore hallucs
                        UnitUtils.isVisible(u)) //ignore units in the fog
                .map(UnitInPool::unit)
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u, LocationConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u, groundAttackersMidPoint)))
                .orElse(null);

        if (attackUnit != null) {
            attackGroundPos = attackUnit.getPosition().toPoint2d();
        }
        else if (GameCache.baseList.get(2).isMyBase()) {
            attackGroundPos = GameCache.baseList.get(2).getResourceMidPoint();
        }
        else {
            attackGroundPos = LocationConstants.insideMainWall;
        }
    }


    private static boolean giveDiversCommand(List<Unit> divers, UnitInPool diveTarget) {
        //return false if diver list is empty
        if (divers.isEmpty()) {
            return false;
        }

        boolean canAttack = divers.get(0).getWeaponCooldown().orElse(1f) == 0f;
        float attackRange = Bot.OBS.getUnitTypeData(false).get(divers.get(0).getType()).getWeapons().iterator().next().getRange();
        List<Unit> attackers = new ArrayList<>();
        List<Unit> retreaters = new ArrayList<>();
        for (Unit diver : divers) {
            boolean inRange = UnitUtils.getDistance(diveTarget.unit(), diver) < attackRange;
            if (canAttack || !inRange) {
                attackers.add(diver);
            }
            else {
                retreaters.add(diver);
            }
        }
        if (!attackers.isEmpty()) {
            Bot.ACTION.unitCommand(attackers, Abilities.ATTACK, diveTarget.unit(), false);
        }
        if (!retreaters.isEmpty()) {
            Bot.ACTION.unitCommand(retreaters, Abilities.MOVE, retreatPos, false);
        }
        return true;
    }

    private static void manageRepairBay() {
        int numInjured = Bot.OBS.getUnits(Alliance.SELF, u -> { //get number of injured army units in dock
            return (u.unit().getType() == Units.TERRAN_VIKING_FIGHTER || u.unit().getType() == Units.TERRAN_BANSHEE || u.unit().getType() == Units.TERRAN_RAVEN) &&
                    UnitUtils.getHealthPercentage(u.unit()) < 100 &&
                    UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(0)) < 5;
        }).size();
        if (numInjured > 0) {
            int numRepairingScvs = getNumRepairingScvs();
            DebugHelper.addInfoLine("numRepairingScvs: " + numRepairingScvs);
            int numScvsToSend = Strategy.NUM_SCVS_REPAIR_STATION - numRepairingScvs;
            if (numScvsToSend > 0) {
                List<UnitInPool> repairScvs = getRepairBayScvs(numScvsToSend);
                repairScvs.stream().forEach(scv -> {
                    UnitMicroList.add(new RepairBayScv(scv));
                });
            }
        }
    }

    private static int getNumRepairingScvs() {
        return (int)UnitMicroList.unitMicroList.stream()
                .filter(basicUnitMicro -> basicUnitMicro instanceof RepairBayScv)
                .count();

    }

    private static List<UnitInPool> getRepairBayScvs(int numScvsToSend) {
        List<UnitInPool> repairScvs = new ArrayList<>();
        for (Base base : GameCache.baseList) {
            if (base.isMyBase()) {
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(base.getCcPos(), 10, true, true);
                if (availableScvs.size() >= numScvsToSend) {
                    repairScvs.addAll(availableScvs.subList(0, numScvsToSend));
                    break;
                }
                else if (!availableScvs.isEmpty()) {
                    repairScvs.addAll(availableScvs);
                }
            }
        }
        return repairScvs;
    }

    private static void setGroundTarget() {
        UnitInPool closestEnemyGround = getClosestEnemyGroundUnit();
        if (closestEnemyGround != null) {
            attackGroundPos = closestEnemyGround.unit().getPosition().toPoint2d();
            attackUnit = closestEnemyGround.unit();
            //TODO: below is hack to "hopefully" handle unknown bug of air units getting stuck on unchanging attackPos
            if (!UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BANSHEE, attackGroundPos, 1).isEmpty() &&
                    !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, attackGroundPos, 1).isEmpty() &&
                    !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_RAVEN, attackGroundPos, 1).isEmpty()) {
                System.out.println("\n\n=============== PHANTOM ENEMY FOUND ===============\n");
                System.out.println("Time.nowClock() = " + Time.nowClock());
                System.out.println("closestEnemyGround.isAlive() = " + closestEnemyGround.isAlive());
                System.out.println("closestEnemyGround.unit().getType() = " + closestEnemyGround.unit().getType());
                GameCache.allEnemiesList.remove(closestEnemyGround);
            }
        }
        else if (Switches.finishHim) {
            attackGroundPos = null; //flag to spread army
        }
        else {
            attackGroundPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
            //gone on to next base after a banshee, viking, and raven have arrived
            if (Bot.OBS.getVisibility(attackGroundPos) == Visibility.VISIBLE &&
                    (GameCache.bansheeList.isEmpty() || UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_BANSHEE, attackGroundPos, 3)) &&
                    (GameCache.vikingList.size() < 3 || UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, attackGroundPos, 3)) &&
                    (GameCache.ravenList.isEmpty() || GameCache.ravenList.stream()
                            .noneMatch(raven ->
                                    UnitUtils.getHealthPercentage(raven) >= Strategy.RETREAT_HEALTH &&
                                            UnitUtils.getDistance(raven, retreatPos) > 10) ||
                            UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_RAVEN, attackGroundPos, 3))) {
                LocationConstants.rotateBaseAttackIndex();
                attackGroundPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
            }
        }
    }

    private static void setAirTarget() {
        //send single vikings at the closest non-threatening air, and set the main air attack target
        UnitInPool closestEnemyAir;
        do {
            closestEnemyAir = getClosestEnemyAirUnit();
        } while (doPeelOffVikingForEasyTarget(closestEnemyAir));
        attackAirPos = (closestEnemyAir != null) ? closestEnemyAir.unit().getPosition().toPoint2d() : attackGroundPos;
    }

    private static boolean doPeelOffVikingForEasyTarget(UnitInPool closestEnemyAir) {
        //TODO: turn on when viking peel is good vs observers and smart not to die
//        if (closestEnemyAir == null) {
//            return false;
//        }
//        if (UnitUtils.NO_THREAT_ENEMY_AIR.contains(closestEnemyAir.unit().getType())) {
//            Unit closestViking = GameCache.vikingList.stream()
//                    .min(Comparator.comparing(unit -> UnitUtils.getDistance(closestEnemyAir.unit(), unit)))
//                    .orElse(null);
//            if (closestViking != null) {
//                Bot.ACTION.unitCommand(closestViking, Abilities.ATTACK, closestEnemyAir.unit(), false);
//                GameCache.vikingList.remove(closestViking);
//            }
//            GameCache.allVisibleEnemiesList.remove(closestEnemyAir);
//            return true;
//        }
        return false;
    }

    private static UnitInPool getClosestEnemyGroundUnit() {
        UnitInPool closestEnemyGroundUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(u -> //(Switches.finishHim || u.unit().getDisplayType() != DisplayType.SNAPSHOT) && //ignore snapshot unless finishHim is true
                        !u.unit().getFlying().orElse(false) && //ground unit
                                u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED && //ignore cloaked units TODO: handle banshees DTs etc with scan
                                !u.unit().getBurrowed().orElse(false) && //ignore burrowed units TODO: handle with scan
                                !UnitUtils.IGNORED_TARGETS.contains(u.unit().getType()) &&
                                u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                                !u.unit().getHallucination().orElse(false)) //ignore hallucs
                                //UnitUtils.isVisible(u)) //ignore units in the fog
                .min(Comparator.comparing(u ->
                        UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), groundAttackersMidPoint) +
                        ((UnitUtils.getDistance(u.unit(), attackGroundPos) < 5) ? -3 : 0))) //preference to maintaining similar target preventing wiggling
                .orElse(null);
//        if (closestEnemyGroundUnit == null) {
//            return null;
//        }
//
//        //swap closest enemy base if it's closer than closest enemy unit
//        UnitInPool closestEnemyBase = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.enemyCommandStructures.contains(enemy.unit().getType()))
//                .stream()
//                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), LocationConstants.myMineralPos)))
//                .orElse(null);
//        if (closestEnemyBase != null &&
//                UnitUtils.getDistance(closestEnemyBase.unit(), LocationConstants.myMineralPos) <
//                        UnitUtils.getDistance(closestEnemyGroundUnit.unit(), LocationConstants.myMineralPos)) {
//            return closestEnemyBase;
//        }

        return closestEnemyGroundUnit;
    }

    private static UnitInPool getClosestEnemyAirUnit() {
        return GameCache.allVisibleEnemiesList.stream()
                .filter(u -> (Switches.finishHim || u.unit().getDisplayType() != DisplayType.SNAPSHOT) && //ignore snapshot unless finishHim is true
                        u.unit().getFlying().orElse(false) && //air unit
                        (!GameCache.ravenList.isEmpty() || u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED) && //ignore cloaked units TODO: handle banshees DTs etc with scan
                        u.unit().getType() != Units.ZERG_PARASITIC_BOMB_DUMMY &&
                        !u.unit().getHallucination().orElse(false) && UnitUtils.isVisible(u)) //ignore hallucs and units in the fog
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), vikingMidPoint)))
                .orElse(null);
    }

    private static void nydusResponse() {
        //send scvs, unsieged tanks, and bio to kill nydus
        Optional<UnitInPool> nydusWorm = UnitUtils.getEnemyUnitsOfType(Units.ZERG_NYDUS_CANAL).stream().findFirst();
        if (nydusWorm.isPresent()) {
            GameResult.setNydusRushed(); //TODO: temp for Spiny
            List<Unit> nydusDivers = new ArrayList<>();
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE));
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARAUDER));
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK));
            //add 10 close scvs
            List<UnitInPool> scvs = Bot.OBS.getUnits(Alliance.SELF, scv ->
                    scv.unit().getType() == Units.TERRAN_SCV &&
                            Position.isSameElevation(scv.unit().getPosition(), nydusWorm.get().unit().getPosition()) &&
                            UnitUtils.getDistance(scv.unit(), nydusWorm.get().unit()) < 35 &&
                            !StructureScv.isScvProducing(scv.unit()))
                    .stream()
                    .sorted(Comparator.comparing(scv -> Bot.QUERY.pathingDistance(scv.unit(), nydusWorm.get().unit().getPosition().toPoint2d())))
                    .collect(Collectors.toList());
            if (scvs.size() > 10) {
                scvs.subList(10, scvs.size()).clear();
            }
            nydusDivers.addAll(UnitUtils.toUnitList(scvs));
            attackGroundPos = nydusWorm.get().unit().getPosition().toPoint2d();
            attackUnit = nydusWorm.get().unit();
            if (!nydusDivers.isEmpty()) {
                Bot.ACTION.unitCommand(nydusDivers, Abilities.ATTACK, ArmyManager.attackUnit, false);
            }

            //also set banshee dive target to nydus
            if (!GameCache.bansheeList.isEmpty()) {
                GameCache.bansheeDivers.addAll(GameCache.bansheeList);
                Switches.bansheeDiveTarget = nydusWorm.get();
            }
        }
    }

    public static void pfTargetting() {
        List<Unit> pfsAndTanks = new ArrayList<>();
        pfsAndTanks.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK_SIEGED));
        pfsAndTanks.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS));
        pfsAndTanks = pfsAndTanks.stream()
                .filter(unit -> unit.getBuildProgress() == 1 &&
                        unit.getWeaponCooldown().orElse(1f) == 0 &&
                        InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, unit.getPosition().toPoint2d()))
                .collect(Collectors.toList());

        for (Unit pfTank : pfsAndTanks) {
            int range;
            float x_pfTank = pfTank.getPosition().getX();
            float y_pfTank = pfTank.getPosition().getY();

            //siege tank range - 1 for rounding
            if (pfTank.getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                range = 12;
            }
            //pf range + 2.5 for PF radius +1 for hisec - 0.5 for rounding
            else if (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                range = 9;
            }
            else {
                range = 8;
            }

            int xMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getX();
            int xMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX());
            int yMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getY();
            int yMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY());
            int xStart = Math.max(Math.round(2*(x_pfTank - range)), xMin);
            int yStart = Math.max(Math.round(2*(y_pfTank - range)), yMin);
            int xEnd = Math.min(Math.round(2*(x_pfTank + range)), xMax);
            int yEnd = Math.min(Math.round(2*(y_pfTank + range)), yMax);


            //get x,y of max value
            int bestValueX = -1;
            int bestValueY = -1;
            int bestValue = 0;
            for (int x = xStart; x <= xEnd; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    if (InfluenceMaps.pointPFTargetValue[x][y] > bestValue &&
                            Position.distance(x/2f, y/2f, x_pfTank, y_pfTank) < range) {
                        bestValueX = x;
                        bestValueY = y;
                        bestValue = InfluenceMaps.pointPFTargetValue[x][y];

                    }
                }
            }

            Unit bestTargetUnit = null;
            if (bestValue == 0) {
                if (LocationConstants.opponentRace == Race.ZERG) {
                    bestTargetUnit = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, Units.ZERG_CHANGELING_MARINE, pfTank.getPosition().toPoint2d());
                }
            }
            else {
                Point2d bestTargetPos = Point2d.of(bestValueX / 2f, bestValueY / 2f);

                //get enemy Unit near bestTargetPos
                List<UnitInPool> enemyTargets = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        UnitUtils.getDistance(enemy.unit(), bestTargetPos) < 1f && !enemy.unit().getFlying().orElse(false));
                if (!enemyTargets.isEmpty()) {
                    bestTargetUnit = enemyTargets.get(0).unit();
                }
            }

            //attack
            if (bestTargetUnit != null) {
                Bot.ACTION.unitCommand(pfTank, Abilities.ATTACK, bestTargetUnit, false);
            }
        }
    }

    private static void positionMarines() {
        if (enemyInMain()) {
            UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream()
                    .forEach(marine ->
                            new BasicUnitMicro(Bot.OBS.getUnit(marine.getTag()), attackGroundPos, false).onStep()
                    );
        }
        else {
            List<Unit> bunkerList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER);
            if (!bunkerList.isEmpty()) {
                for (Unit marine : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE)) {
                    if (marine.getOrders().isEmpty()) { //for each idle marine
                        Bot.ACTION.unitCommand(marine, Abilities.SMART, bunkerList.get(0), false);
                    }
                }
            }
        }
    }

    private static void positionLiberators() { //positions only 1 liberator per game loop
        Unit idleLib = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_LIBERATOR).stream()
                .filter(unit -> unit.getOrders().isEmpty())
                .findFirst().orElse(null);


        if (idleLib != null) {
            boolean isLibPlaced = false;

            //send available liberator to siege an expansion
            List<Base> allButEnemyStarterBases = GameCache.baseList.subList(0, GameCache.baseList.size()-BuildManager.getNumEnemyBasesIgnored());
            outer: for (Base base : allButEnemyStarterBases) {
                if (base.isMyBase() && !base.isMyMainBase() && !base.isDryedUp()) { //my expansion bases only
                    for (DefenseUnitPositions libPos : base.getLiberators()) {
                        if (libPos.getUnit() == null) {
                            libPos.setUnit(Bot.OBS.getUnit(idleLib.getTag()));
//                            Bot.ACTION.unitCommand(idleLib, Abilities.MOVE, Position.towards(libPos.getPos(), base.getCcPos(), -2), false)
//                                    .unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE, Position.towards(libPos.getPos(), base.getCcPos(), 5), true);
                            UnitMicroList.add(new LibDefender(libPos.getUnit(), libPos.getPos(), base.getCcPos()));
                            isLibPlaced = true;
                            break outer;
                        }
                    }
                }
            }

            //if nowhere to send lib and no expansions left, siege newest enemy base (or siege enemy 3rd base if no enemy bases are known)
            if (!isLibPlaced && allButEnemyStarterBases.stream().noneMatch(base -> base.isUntakenBase() && !base.isDryedUp())) {
                GameCache.baseList.stream()
                        .filter(base -> base.isEnemyBase)
                        .findFirst()
                        .ifPresentOrElse(base -> Bot.ACTION.unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE,
                                        Position.towards(base.getCcPos(), idleLib.getPosition().toPoint2d(), 1.7f), true),
                                () -> Bot.ACTION.unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE,
                                        Position.towards(GameCache.baseList.get(GameCache.baseList.size()-3).getCcPos(), idleLib.getPosition().toPoint2d(), 1.7f), true));
            }
        }
    }

    private static void positionTanks() { //positions only 1 tank per game loop
        //TODO: unsiege tanks on base.onEnemyBaseLost

        Unit idleTank = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK).stream()
                .filter(unit -> unit.getOrders().isEmpty())
                .findFirst().orElse(null);


        if (idleTank != null) {
            boolean isTankPlaced = false;

            //send available tank to siege an expansion
            List<Base> allButEnemyStarterBases = GameCache.baseList.subList(0, GameCache.baseList.size()-BuildManager.getNumEnemyBasesIgnored());
            outer: for (Base base : allButEnemyStarterBases) {
                if (base.isMyBase() && !base.isMyMainBase() && !base.isDryedUp()) { //my expansion bases only
                    for (DefenseUnitPositions tankPos : base.getTanks()) {
                        if (tankPos.getUnit() == null) {
                            tankPos.setUnit(Bot.OBS.getUnit(idleTank.getTag()));
                            UnitMicroList.add(new TankDefender(tankPos.getUnit(), tankPos.getPos()));
                            isTankPlaced = true;
                            break outer;
                        }
                    }
                }
            }

            //if nowhere to send tank and no expansions available, a-move tank to its death
            if (!isTankPlaced && allButEnemyStarterBases.stream().noneMatch(base -> base.isUntakenBase() && !base.isDryedUp())) {
                GameCache.baseList.stream()
                        .filter(base -> base.isEnemyBase)
                        .forEach(base -> Bot.ACTION.unitCommand(idleTank, Abilities.ATTACK, base.getCcPos(), true));
                Bot.ACTION.unitCommand(idleTank, Abilities.ATTACK, GameCache.baseList.get(GameCache.baseList.size()-1).getCcPos(), true);
            }
        }
    }

    private static void salvageBunkerAtNatural() {
        List<UnitInPool> bunkerList = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BUNKER &&
                UnitUtils.getDistance(u.unit(), LocationConstants.BUNKER_NATURAL) < 1);
        if (bunkerList.isEmpty()) {
            return;
        }
        Unit bunker = bunkerList.get(0).unit();
        if (UnitUtils.getHealthPercentage(bunker) < 40 || bunkerUnnecessary()) {
            Bot.ACTION.unitCommand(bunker, Abilities.UNLOAD_ALL_BUNKER, false); //rally is already set to top of inside main wall
            Bot.ACTION.unitCommand(bunker, Abilities.EFFECT_SALVAGE, false);
        }
    }

    //return true when bunker is no longer needed
    private static boolean bunkerUnnecessary() {
        return GameCache.baseList.get(1).getCc() != null &&
                GameCache.baseList.get(1).getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS &&
                (!Strategy.DO_LEAVE_UP_BUNKER || Time.nowFrames() > Time.toFrames("6:00"));
    }

    private static void raiseAndLowerDepots() {
        for(Unit depot : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT)) {
            Point2d depotPos = depot.getPosition().toPoint2d();
            if (!InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos)) {
                Bot.ACTION.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
            }
        }
        for(Unit depot : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT_LOWERED)) {
            Point2d depotPos = depot.getPosition().toPoint2d();
            if (InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos) && CannonRushDefense.cannonRushStep == 0) {
                Bot.ACTION.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
            }
        }
    }

    public static void spreadArmy(List<Unit> army) {
        for (Unit unit : army) {
            if (unit.getOrders().isEmpty()) {
                Bot.ACTION.unitCommand(unit, Abilities.ATTACK, Bot.OBS.getGameInfo().findRandomLocation(), false);
            }
        }
    }

    private static Point2d calculateTankPosition(Point2d ccPos) {//pick position away from enemy main base like a knight move (3x1)
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1).getX();
        float yEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 2;
        float yMove = 2;

        if (Math.abs(xDistance) > Math.abs(yDistance)) { //move 3x1
            xMove = 5f;
        }
        else { //move 1x3
            yMove = 5f;
        }
        if (xDistance > 0) {
            xMove *= -1;
        }
        if (yDistance > 0) {
            yMove *= -1;
        }
        return Point2d.of(xCC + xMove, yCC + yMove);
    }

    public static int calcNumVikingsNeeded() {
        float answer = 0;
        boolean hasDetector = false;
        boolean hasTempests = false;
        for (UnitInPool enemy : GameCache.allEnemiesList) {
            switch ((Units)enemy.unit().getType()) {
                case TERRAN_RAVEN: case ZERG_OVERSEER: case PROTOSS_OBSERVER:
                    answer += 0.3;
                    hasDetector = true;
                    break;
                case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG: case TERRAN_BANSHEE:
                case ZERG_MUTALISK: case ZERG_VIPER: case ZERG_BROODLORD_COCOON: case ZERG_BROODLORD:
                case PROTOSS_ORACLE:
                    answer += 0.5;
                    hasDetector = true;
                    break;
                case TERRAN_VIKING_FIGHTER: case TERRAN_VIKING_ASSAULT:
                    answer += 1.5;
                    break;
                case ZERG_CORRUPTOR:
                    answer += 1.3;
                    break;
                case PROTOSS_PHOENIX:
                    answer += 2;
                    break;
                case PROTOSS_VOIDRAY:
                    answer += 1.5;
                    break;
                case TERRAN_BATTLECRUISER: case PROTOSS_CARRIER:
                    answer += 3.67;
                    break;
                case PROTOSS_TEMPEST:
                    hasTempests = true;
                    answer += 2;
                    break;
                case PROTOSS_MOTHERSHIP:
                    answer += 4;
                    break;
            }
        }
        if (hasTempests) { //minimum 10 vikings at all times if enemy has a tempest
            answer = Math.max(10, answer);
        }
        else if (Switches.enemyCanProduceAir) { //set minimum vikings if enemy can produce air
            answer = Math.max(2, answer);
        }
        else if (hasDetector && Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) && UnitUtils.getNumFriendlyUnits(Units.TERRAN_BANSHEE, true) > 0) {
            answer = Math.max((LocationConstants.opponentRace == Race.PROTOSS) ? 2 : 3, answer); //minimum vikings if he has a detector
        }
        answer = Math.max(answer, GameCache.bansheeList.size() / 5); //at least 1 safety viking for every 5 banshees
        return (int)answer;
    }

    public static void giveBansheeCommand(Unit banshee) {
        ArmyCommands lastCommand = getCurrentCommand(banshee);
        int x = InfluenceMaps.toMapCoord(banshee.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(banshee.getPosition().getY());
        boolean isUnsafe = (Switches.isDivingTempests) ? false : InfluenceMaps.pointThreatToAir[x][y] > 2;
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke();
        boolean isInDetectionRange = InfluenceMaps.pointDetected[x][y];
        boolean isInBansheeRange = InfluenceMaps.pointInBansheeRange[x][y];
        boolean canAttack = banshee.getWeaponCooldown().orElse(1f) < 0.1f && InfluenceMaps.pointThreatToAir[x][y] < 200;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0f) > Strategy.ENERGY_BEFORE_CLOAKING &&
                Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK);
        boolean isParasitic = banshee.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways
        boolean isDecloakBuffed = UnitUtils.hasDecloakBuff(banshee);
        boolean isAnyBaseUnderAttack = !doOffense && attackUnit != null;
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : Strategy.RETREAT_HEALTH;

        //always flee if locked on by cyclone
        if (banshee.getBuffs().contains(Buffs.LOCK_ON)) {
            if (!isInDetectionRange && canCloak && !isDecloakBuffed) {
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            }
            else {
                retreatUnitFromCyclone(banshee);
                //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
            }
        }
        //shoot when available
        else if (canAttack && isInBansheeRange) {
            //attack
            if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(banshee);
        }
        //fly to enemy main if parasitic'ed
        else if (isParasitic) {
            Bot.ACTION.unitCommand(banshee, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
        }
        else if (isUnsafe) {
            if (isInDetectionRange) {
                //retreat
                retreatMyUnit(banshee);
                //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
            }
            else if (cloakState == CloakState.CLOAKED_ALLIED &&
                    banshee.getEnergy().get() > 3 + ((UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() > 2) ? 2 : 0)) { //additional energy for time to flee tempest range
                if (isInBansheeRange) { //maintain max range
                    //retreat
                    retreatMyUnit(banshee);
                    //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
                }
                else {
                    //attack
                    if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(banshee);
                }
            }
            else if (canCloak && !isDecloakBuffed) {
                //cloak
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            }
            else {
                //retreat
                retreatMyUnit(banshee);
                //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
            }
        }
        //staying in repair bay if not full health and if needing energy
        else if (canRepair && //not broke
                UnitUtils.getDistance(banshee, retreatPos) < 3 && //at repair bay
                UnitUtils.getHealthPercentage(banshee) < 100) { //wait for heal
            if (cloakState == CloakState.CLOAKED_ALLIED && !isUnsafe) {
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_OFF_BANSHEE, false);
            }
            else {
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(banshee);
            }
        }
        //go home if low health
        else if (canRepair && UnitUtils.getHealthPercentage(banshee) < healthToRepair) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(banshee);
        }
        //go to repair bay when waiting on cloak and not needed for defense
        else if (isWaitingForCloak(canCloak, cloakState) && !isAnyBaseUnderAttack) {
            //retreat
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(banshee);
        }
        else {
            if (isInBansheeRange) {
                //retreat
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(banshee);
            }
            else {
                //attack
                if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(banshee);
            }
        }
    }

    private static void retreatMyUnit(Unit myUnit) {
        //get closest enemy effect
        Point2d enemyPos = Bot.OBS.getEffects().stream()
                .filter(e ->
                        (e.getEffect() == Effects.PSI_STORM_PERSISTENT || e.getEffect() == Effects.RAVAGER_CORROSIVE_BILE_CP) &&
                        e.getRadius().orElse(0f) + myUnit.getRadius() > UnitUtils.getDistance(myUnit, e.getPositions().iterator().next()))
                .findFirst()
                .map(e -> e.getPositions().iterator().next())
                .orElse(null);

        //get an enemy that is within its attack range
        if (enemyPos == null) {
            Unit closestEnemy = UnitUtils.getEnemyInRange(myUnit);
            if (closestEnemy != null) {
                enemyPos = closestEnemy.getPosition().toPoint2d();
            }
        }

        //if no enemy in range
        if (enemyPos == null) {
            armyGoingHome.add(myUnit);
        }
        else {
            //retreat command away from enemy position
            Bot.ACTION.unitCommand(myUnit, Abilities.MOVE, Position.towards(myUnit.getPosition().toPoint2d(), enemyPos, -4f), false);
        }
    }

    private static void retreatUnitFromCyclone(Unit myUnit) {
        Point2d cyclonePos = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_CYCLONE).stream()
                .map(u -> u.unit().getPosition().toPoint2d())
                .filter(cyclone -> UnitUtils.getDistance(myUnit, cyclone) <= 14.1)
                .min(Comparator.comparing(cyclone -> UnitUtils.getDistance(myUnit, cyclone)))
                .orElse(null);

        //if no cyclone visible in range
        if (cyclonePos == null) {
            armyGoingHome.add(myUnit);
        }
        else {
            //retreat command away from nearest cyclone position
            Bot.ACTION.unitCommand(myUnit, Abilities.MOVE, Position.towards(myUnit.getPosition().toPoint2d(), cyclonePos, -4f), false);
        }
    }

    private static boolean isWaitingForCloak(boolean canCloak, CloakState cloakState) {
        return !Strategy.MASS_RAVENS && !canCloak && cloakState != CloakState.CLOAKED_ALLIED;
        //not mass ravens AND can't cloak AND not cloaked
    }

    private static ArmyCommands getCurrentCommand(Unit unit) {
        ArmyCommands currentCommand = ArmyCommands.EMPTY;
        if (!unit.getOrders().isEmpty()) {
            UnitOrder order = unit.getOrders().get(0);
            if (order.getAbility() == Abilities.ATTACK) {
                if (order.getTargetedWorldSpacePosition().isPresent() &&
                        order.getTargetedWorldSpacePosition().get().toPoint2d().distance(attackGroundPos) < 1) {
                    currentCommand = ArmyCommands.ATTACK;
                }
                else if (order.getTargetedUnitTag().isPresent()) {
                    currentCommand = ArmyCommands.DIVE;
                }
            }
            else if (order.getAbility() == Abilities.MOVE &&
                    order.getTargetedWorldSpacePosition().isPresent() &&
                    order.getTargetedWorldSpacePosition().get().toPoint2d().distance(ArmyManager.retreatPos) < 1) {
                currentCommand = ArmyCommands.HOME;
            }
        }
        return currentCommand;
    }

    private static void giveVikingCommand(Unit viking) { //never kites outside of range of air units... always engages maintaining max range
        ArmyCommands lastCommand = getCurrentCommand(viking);
        int x = InfluenceMaps.toMapCoord(viking.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(viking.getPosition().getY());
        boolean isUnsafe = InfluenceMaps.pointThreatToAirFromGround[x][y] > 0; //don't fear enemy air units
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke();
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : Strategy.RETREAT_HEALTH;


        //keep vikings back if tempests are on the map, but no other toss air units are visible
        if (!UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty() && //TODO: change below to use Gamestate.allVisibleEnemiesMap
                Bot.OBS.getUnits(Alliance.ENEMY,
                        e -> (e.unit().getType() == Units.PROTOSS_PHOENIX || e.unit().getType() == Units.PROTOSS_VOIDRAY || e.unit().getType() == Units.PROTOSS_INTERCEPTOR)
                        && !e.unit().getHallucination().orElse(false)).isEmpty()) {
            isUnsafe = InfluenceMaps.pointVikingsStayBack[x][y];
        }
        //fear enemy vikings if he has more
        else if (GameCache.vikingList.size() < UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).size()) {
            isUnsafe = InfluenceMaps.pointThreatToAirPlusBuffer[x][y] > 2;
        }

        boolean isInVikingRange = InfluenceMaps.pointInVikingRange[x][y];
        boolean canAttack = viking.getWeaponCooldown().orElse(1f) < 0.1f;
        boolean isParasitic = viking.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways

        //always flee if locked on by cyclone
        if (viking.getBuffs().contains(Buffs.LOCK_ON)) {
            retreatUnitFromCyclone(viking);
            //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(viking);
        }
        //shoot when available
        else if (canAttack && isInVikingRange) {
            //attack
            if (lastCommand != ArmyCommands.ATTACK) armyAirAttacking.add(viking);
        }
        //fly to enemy main if parasitic'ed
        else if (isParasitic) {
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
        }
        //in enemy attack range, then back up
        else if (isUnsafe) {
            retreatMyUnit(viking);
            //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(viking);
        }
        //Under 100% health and at repair bay
        else if (canRepair &&
                UnitUtils.getHealthPercentage(viking) < 100 &&
                UnitUtils.getDistance(viking, retreatPos) < 3) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
        }
        //go home if low health
        else if (canRepair && UnitUtils.getHealthPercentage(viking) < healthToRepair) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
        }
        //in range then back up
        else if (isInVikingRange) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
        }
        //out of range, then move in
        else {
            if (lastCommand != ArmyCommands.ATTACK) armyAirAttacking.add(viking);
        }
    }

    //return true if autoturret cast
    private static void giveRavenCommand(Unit raven, boolean doCastTurrets) {

        //wait for raven to auto-turret before giving a new command
        if (!isAttackUnitRetreating && UnitUtils.getOrder(raven) == Abilities.EFFECT_AUTO_TURRET) {
            return;
        }

        ArmyCommands lastCommand = getCurrentCommand(raven);
        boolean isUnsafe = (raven.getEnergy().orElse(0f) >= Strategy.AUTOTURRET_AT_ENERGY)
                ? InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, raven.getPosition().toPoint2d()) > 0
                : InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirPlusBuffer, raven.getPosition().toPoint2d()) > 0;
        boolean inRange = InfluenceMaps.getValue(InfluenceMaps.pointAutoTurretTargets, raven.getPosition().toPoint2d());
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke();
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : (Strategy.RETREAT_HEALTH + 10);
        boolean isParasitic = raven.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways

        //always flee if locked on by cyclone
        if (raven.getBuffs().contains(Buffs.LOCK_ON)) {
            retreatUnitFromCyclone(raven);
            //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(raven);
        }

        //fly to enemy main if parasitic'ed
        else if (isParasitic) {
            Bot.ACTION.unitCommand(raven, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
        }

        //stay in repair bay if not on offensive or under 100% health
        else if (canRepair && UnitUtils.getHealthPercentage(raven) < 100 && raven.getPosition().toPoint2d().distance(retreatPos) < 3) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
        }

        //go home to repair if low
        else if (canRepair && UnitUtils.getHealthPercentage(raven) < healthToRepair) {
            if (!doCastTurrets || !doAutoTurretOnRetreat(raven)) {
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
            }
        }

        //go home to repair if mass raven strategy and no energy for autoturrets
        else if (Strategy.MASS_RAVENS && UnitUtils.getHealthPercentage(raven) < 100 &&
                raven.getEnergy().orElse(0f) < 45) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
        }

        //back up if in range
        else if (isUnsafe || inRange) {
            if (!Strategy.DO_SEEKER_MISSILE || !castSeeker(raven)) {
                if (!doCastTurrets || !doAutoTurret(raven)) {
                    if (isUnsafe) {
                        if (lastCommand != ArmyCommands.HOME) {
                            retreatMyUnit(raven);
                        }
                    }
                    else if (lastCommand != ArmyCommands.ATTACK) {
                        armyGroundAttacking.add(raven);
                    }
                }
            }
        }
        //go forward if not in range
        else if (lastCommand != ArmyCommands.ATTACK) {
            armyGroundAttacking.add(raven);
        }
    }

    //drop auto-turrets near enemy before going home to repair
    private static boolean doAutoTurretOnRetreat(Unit raven) {
        if (raven.getEnergy().orElse(0f) >= 50 && UnitUtils.getDistance(raven, attackGroundPos) < 12 && attackUnit != null) {
            return castAutoTurret(raven, 0);
        }
        return false;
    }

    //drop auto-turrets near enemy
    private static boolean doAutoTurret(Unit raven) {
        if (!isAttackUnitRetreating && raven.getEnergy().orElse(0f) >= Strategy.AUTOTURRET_AT_ENERGY) {
            return castAutoTurret(raven, 2);
        }
        return false;
    }

    private static boolean castAutoTurret(Unit raven, float towardsEnemyDistance) {
        Point2d turretPos = Position.toWholePoint(
                Position.towards(raven.getPosition().toPoint2d(), attackGroundPos, towardsEnemyDistance));
        List<Point2d> posList = Position.getSpiralList(turretPos, 3).stream()
                .filter(p -> p.distance(attackGroundPos) < 8)
                .filter(p -> Bot.OBS.isPlacable(p))
                .sorted(Comparator.comparing(p -> p.distance(attackGroundPos)))
                .collect(Collectors.toList());
        if (posList.isEmpty()) {
            return false;
        }

        List<QueryBuildingPlacement> queryList = posList.stream()
                .map(p -> QueryBuildingPlacement
                        .placeBuilding()
                        .useAbility(Abilities.EFFECT_AUTO_TURRET)
                        .on(p).build())
                .collect(Collectors.toList());

        List<Boolean> placementList = Bot.QUERY.placement(queryList);
        queriesMade++;

        if (placementList.contains(true)) {
//            int best = 0;
//            for (int i=0; i<posList.size(); i++) {
//                if (placementList.get(i)) {
//                    best++;
//                    DebugHelper.draw3dBox(posList.get(i), Color.GREEN, 0.5f);
//                    DebugHelper.drawText(String.valueOf(best), posList.get(i), Color.GREEN);
//                }
//            }
//            Bot.DEBUG.sendDebug();

            Point2d placementPos = posList.get(placementList.indexOf(true));
            Bot.ACTION.unitCommand(raven, Abilities.EFFECT_AUTO_TURRET, placementPos, false);
            turretsCast++;
            return true;
        }
        else if (attackUnit != null && !UnitUtils.canMove(attackUnit.getType()) && towardsEnemyDistance < 4) {
            castAutoTurret(raven, 4);
        }
        return false;
    }

    private static boolean castSeeker(Unit raven) {
        //cast seeker only once every 3sec
        if (Time.nowFrames() < prevSeekerFrame + 70) {
            return false;
        }
        float ravenEnergy = raven.getEnergy().orElse(0f);
        if (ravenEnergy >= 75) {
            Point2d targetPos = findSeekerTarget((int)raven.getPosition().getX(), (int)raven.getPosition().getY(), (ravenEnergy > 150));  //UnitUtils.towards(raven.getPosition().toPoint2d(), attackPos, 8.5f);
            if (targetPos == null) {
                return false;
            }
            List<UnitInPool> targetUnitList = Bot.OBS.getUnits(Alliance.ENEMY, unit -> unit.unit().getPosition().toPoint2d().distance(targetPos) < 0.5f);
            if (targetUnitList.isEmpty()) {
                return false;
            }
            Bot.ACTION.unitCommand(raven, Abilities.EFFECT_ANTI_ARMOR_MISSILE, targetUnitList.get(0).unit(), false);
            prevSeekerFrame = Time.nowFrames();
            return true;
        }
        return false;
    }

    private static Point2d findSeekerTarget(int ravenX, int ravenY, boolean isMaxEnergy) {
        int bestX = 0; int bestY = 0; float bestValue = 0;
        int xMin = Math.max(0, ravenX-Strategy.CAST_SEEKER_RANGE);
        int xMax = Math.min(InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX()), (ravenX+Strategy.CAST_SEEKER_RANGE)*2);
        int yMin = Math.max(0, ravenY-Strategy.CAST_SEEKER_RANGE);
        int yMax = Math.min(InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY()), (ravenY+Strategy.CAST_SEEKER_RANGE)*2);
        for (int x=xMin; x<xMax; x++) {
            for (int y=yMin; y<yMax; y++) {
                if (InfluenceMaps.pointSupplyInSeekerRange[x][y] > bestValue) {
                    bestX = x;
                    bestY = y;
                    bestValue = InfluenceMaps.pointSupplyInSeekerRange[x][y];
                }
            }
        }
        float minSupplyToSeeker = (isMaxEnergy) ? Strategy.MIN_SUPPLY_TO_SEEKER - 7 : Strategy.MIN_SUPPLY_TO_SEEKER;
        return (bestValue < minSupplyToSeeker) ? null : Point2d.of(bestX/2f, bestY/2f);
    }

    public static boolean shouldDive(Units unitType, Unit enemy) {
        int numAttackersNearby = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, unitType, enemy.getPosition().toPoint2d(), Strategy.DIVE_RANGE).size();
        if (numAttackersNearby < 2) {
            return false;
        }

        if (unitType == Units.TERRAN_VIKING_FIGHTER && UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).size() >= 6) {
            return false;
        }

        //calculate point from detector
        Point2d threatPoint = getPointFromA(enemy.getPosition().toPoint2d(), retreatPos, Bot.OBS.getUnitTypeData(false).get(unitType).getWeapons().iterator().next().getRange());

        int x = InfluenceMaps.toMapCoord(threatPoint.getX());
        int y = InfluenceMaps.toMapCoord(threatPoint.getY());

        //if 25%+ of the threat is from air units, don't dive vikings
//        if (unitType == Units.TERRAN_VIKING_FIGHTER && GameState.pointThreatToAirFromGround[x][y] < GameState.pointThreatToAir[x][y] * 0.75) {
//            return false;
//        }

        //calculate if I have enough units to dive in and snipe the detector
        return numAttackersNearby >= numNeededToDive(enemy, InfluenceMaps.pointThreatToAir[x][y]);
    }

    public static boolean shouldDiveTempests(Point2d closestTempest, int numVikingsNearby) {
        //if not enough vikings to deal with the tempests
        if (numVikingsNearby < Math.min(Strategy.MAX_VIKINGS_TO_DIVE_TEMPESTS, ArmyManager.calcNumVikingsNeeded() * 0.8)) {
            return false;
        }

        //if maxed, then send the vikings
        if (Bot.OBS.getFoodUsed() >= 197) {
            return true;
        }

        //check if too much support units
        //TODO: change to include stalkers out of vision??
        List<UnitInPool> aaThreats = Bot.OBS.getUnits(Alliance.ENEMY, u ->
                (u.unit().getType() == Units.PROTOSS_VOIDRAY || u.unit().getType() == Units.PROTOSS_STALKER ||
                        u.unit().getType() == Units.PROTOSS_INTERCEPTOR || u.unit().getType() == Units.PROTOSS_PHOENIX) &&
                        UnitUtils.getDistance(u.unit(), closestTempest) < 15);
        int threatTotal = 0;
        for (UnitInPool u : aaThreats) {
            Unit threat = u.unit();
            switch ((Units)threat.getType()) {
                case PROTOSS_VOIDRAY:
                    threatTotal += 4;
                    break;
                case PROTOSS_PHOENIX: case PROTOSS_STALKER:
                    threatTotal += 2;
                    break;
                case PROTOSS_INTERCEPTOR:
                    threatTotal += 1;
                    break;
            }
        }
        float ratio = (UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() < 3) ? 0.65f : 1.2f;
        return threatTotal < numVikingsNearby * ratio; //larger ratio = dive more frequently
    }

    private static int numNeededToDive(Unit enemy, int threatLevel) {
        float enemyHP = enemy.getHealth().orElse(60f) + enemy.getShield().orElse(0f); //60f to represent the 40hp +20shields of an observer
        if (enemy.getType() == Units.ZERG_OVERSEER) {
            enemyHP *= 0.71; //adjustment for +armored damage
        }
        threatLevel *= 1.3; //hack to be more scared of enemy aa, in general
        return Math.min(2, (int)((enemyHP*threatLevel+2500)/1500 + (enemyHP/500) + (threatLevel/20))) + 1;
    }

    public static Point2d getPointFromA(Point2d a, Point2d b, float distance) {
        double ratio = distance / a.distance(b);
        int newX = (int)(((b.getX() - a.getX()) * ratio) + a.getX());
        int newY = (int)(((b.getY() - a.getY()) * ratio) + a.getY());
        return Point2d.of(newX, newY);
    }

    public static void sendBioProtection(Point2d expansionPos) {
        List<Unit> bio = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
        bio.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARAUDER));
        if (!bio.isEmpty()) {
            Bot.ACTION.unitCommand(bio, Abilities.ATTACK, expansionPos, true);
        }
    }

    public static boolean enemyInMain() {
        if (attackUnit == null) {
            return false;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, attackUnit.getPosition().toPoint2d());
    }
}
