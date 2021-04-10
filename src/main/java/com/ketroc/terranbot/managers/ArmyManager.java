package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.micro.*;
import com.ketroc.terranbot.micro.Target;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.strategies.CannonRushDefense;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.MarineAllIn;
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

        //TODO: this is a temporary test
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_CYCLONE).forEach(cyclone -> {
            UnitMicroList.add(new Cyclone(cyclone, LocationConstants.insideMainWall));
        });

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
                ActionHelper.unitCommand(armyGoingHome, Abilities.MOVE, retreatPos, false);
            }
            if (!armyGroundAttacking.isEmpty()) {
                Point2d targetPos = attackGroundPos;
                ActionHelper.unitCommand(armyGroundAttacking, Abilities.ATTACK, targetPos, false);
            }
            if (!armyAirAttacking.isEmpty()) {
                Point2d targetPos = attackAirPos;
                ActionHelper.unitCommand(armyAirAttacking, Abilities.ATTACK, targetPos, false);
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
        if (attackUnit != null && UnitUtils.canMove(attackUnit)) {
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
                    if (lib.getType() == Units.TERRAN_LIBERATOR_AG && UnitUtils.isWeaponAvailable(lib)) {
                        Point2d libZone = Position.towards(lib.getPosition().toPoint2d(), base.getCcPos(), 5);
                        Unit targetUnit = getLibTarget(lib, libZone);
                        if (targetUnit != null) {
                            ActionHelper.unitCommand(lib, Abilities.ATTACK, targetUnit, false);
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
                if (army.stream().anyMatch(unit -> ActionIssued.getCurOrder(unit).isPresent())) {
                    ActionHelper.unitCommand(army, Abilities.ATTACK, attackGroundPos, false);
                }
            }
        }
    }

    private static void autoturretTargetting() {
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_AUTO_TURRET).stream()
                .filter(turret -> UnitUtils.isWeaponAvailable(turret))
                .forEach(turret -> {
                    selectTarget(turret).ifPresent(target ->
                            ActionHelper.unitCommand(turret, Abilities.ATTACK, target, false));
                });
    }

    private static Optional<Unit> selectTarget(Unit turret) {
        List<UnitInPool> enemiesInRange = UnitUtils.getEnemyTargetsNear(turret, 8);

        Target bestTarget = new Target(null, Float.MIN_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
        for (UnitInPool enemy : enemiesInRange) {
            if (UnitUtils.CREEP_TUMOR.contains(enemy.unit().getType())) { //shoot creep tumors first
                return Optional.of(enemy.unit());
            }
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
            float enemyValue = (enemyCost*damageMultiple)/enemyHP;
            if (enemyValue > bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
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
                if (UnitUtils.isInFogOfWar(Switches.vikingDiveTarget)) { //TODO: handle it when vikings arrive at last known tempest location and still can't find the tempest
                    moveVikings.addAll(GameCache.vikingDivers);
                }
                else {
                    for (Unit viking : GameCache.vikingDivers) {
                        if (UnitUtils.isWeaponAvailable(viking) && UnitUtils.getDistance(viking, Switches.vikingDiveTarget.unit()) < 8.5) {
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
                            ActionHelper.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -5), false);
                        }
                        ActionHelper.unitCommand(attackVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
                    }
                    else {
                        ActionHelper.unitCommand(attackVikings, Abilities.ATTACK, Switches.vikingDiveTarget.unit(), false);
                    }
                }
                if (!moveVikings.isEmpty()) {
                    ActionHelper.unitCommand(moveVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
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
        //TODO: testing cyclones
        if (Strategy.DO_USE_CYCLONES &&
                (!GameCache.bansheeList.isEmpty() || !GameCache.vikingList.isEmpty() || !GameCache.ravenList.isEmpty())) {
            doOffense = true;
        }
        else if (Strategy.MASS_RAVENS) {
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
            doOffense = Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                    GameCache.bansheeList.size() > 3 &&
                    GameCache.vikingList.size() * 1.34 > UnitUtils.getEnemyUnitsOfTypes(UnitUtils.VIKING_TYPE).size();
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
                        u.unit().getDisplayType() != DisplayType.HIDDEN && //ignore undetected cloaked/burrowed units
                        u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                        u.unit().getType() != Units.ZERG_BROODLING && //ignore broodlings
                        !u.unit().getHallucination().orElse(false) && //ignore hallucs
                        !UnitUtils.isInFogOfWar(u)) //ignore units in the fog
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

        float attackRange = Bot.OBS.getUnitTypeData(false).get(divers.get(0).getType()).getWeapons().iterator().next().getRange();
        List<Unit> attackers = new ArrayList<>();
        List<Unit> retreaters = new ArrayList<>();
        for (Unit diver : divers) {
            boolean canAttack = UnitUtils.isWeaponAvailable(diver);
            boolean inRange = UnitUtils.getDistance(diveTarget.unit(), diver) < attackRange;
            if (canAttack || !inRange) {
                attackers.add(diver);
            }
            else {
                retreaters.add(diver);
            }
        }
        if (!attackers.isEmpty()) {
            ActionHelper.unitCommand(attackers, Abilities.ATTACK, diveTarget.unit(), false);
        }
        if (!retreaters.isEmpty()) {
            ActionHelper.unitCommand(retreaters, Abilities.MOVE, retreatPos, false);
        }
        return true;
    }

    private static void manageRepairBay() {
        int numInjured = getNumRepairBayUnits();
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

    public static int getNumRepairBayUnits() {
        return Bot.OBS.getUnits(Alliance.SELF, u -> { //get number of injured army units in dock
            return (u.unit().getType() == Units.TERRAN_VIKING_FIGHTER || u.unit().getType() == Units.TERRAN_BANSHEE || u.unit().getType() == Units.TERRAN_RAVEN) &&
                    UnitUtils.getHealthPercentage(u.unit()) < 100 &&
                    UnitUtils.getDistance(u.unit(), GameCache.baseList.get(0).getResourceMidPoint()) < 2.5;
        }).size();
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
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(base.getCcPos(), 9, true, true);
                if (!availableScvs.isEmpty()) {
                    repairScvs.addAll(availableScvs);
                    if (repairScvs.size() >= numScvsToSend) {
                        return repairScvs.subList(0, numScvsToSend);
                    }
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
                Print.print("\n\n=============== PHANTOM ENEMY FOUND ===============\n");
                Print.print("closestEnemyGround.isAlive() = " + closestEnemyGround.isAlive());
                Print.print("closestEnemyGround.unit().getType() = " + closestEnemyGround.unit().getType());
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
                attackGroundPos = LocationConstants.getNextBaseAttackPos();
            }
        }
    }

    private static void setAirTarget() {
        //set air attack pos
        UnitInPool closestEnemyAir = getClosestEnemyAirUnit();
        if (closestEnemyAir == null) {
            attackAirPos = attackGroundPos;
            return;
        }
        attackAirPos = closestEnemyAir.unit().getPosition().toPoint2d();

        //send kill squad if criteria met TODO: track into fog
        if (!InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, attackAirPos) &&
                UnitUtils.VIKING_PEEL_TARGET_TYPES.contains(closestEnemyAir.unit().getType()) &&
                attackAirPos.distance(LocationConstants.pointOnEnemyRamp) > 40) {
            if (!GameCache.vikingList.isEmpty()) {
                attackAirPos = attackGroundPos;
            }
            AirUnitKillSquad.add(closestEnemyAir);
        }
    }

    private static UnitInPool getClosestEnemyGroundUnit() {
        UnitInPool closestEnemyGroundUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(u -> //(Switches.finishHim || u.unit().getDisplayType() != DisplayType.SNAPSHOT) && //ignore snapshot unless finishHim is true
                        !u.unit().getFlying().orElse(false) && //ground unit
                                (!GameCache.ravenList.isEmpty() || u.unit().getDisplayType() != DisplayType.HIDDEN) && //TODO: handle with scan?
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
                        (!Ignored.contains(u.getTag()) || AirUnitKillSquad.containsWithNoVikings(u.getTag())) &&
                        u.unit().getFlying().orElse(false) && //air unit
                        (!GameCache.ravenList.isEmpty() || u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED) && //ignore cloaked units with no raven TODO: handle banshees DTs etc with scan
                        u.unit().getType() != Units.ZERG_PARASITIC_BOMB_DUMMY &&
                        !u.unit().getHallucination().orElse(false) && !UnitUtils.isInFogOfWar(u)) //ignore hallucs and units in the fog
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
                ActionHelper.unitCommand(nydusDivers, Abilities.ATTACK, ArmyManager.attackUnit, false);
            }

            //also set banshee dive target to nydus
            if (!GameCache.bansheeList.isEmpty()) {
                GameCache.bansheeDivers.addAll(GameCache.bansheeList);
                Switches.bansheeDiveTarget = nydusWorm.get();
            }
        }
    }

    public static void pfTargetting() {
        List<Unit> pfList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS).stream()
                .filter(unit -> unit.getBuildProgress() == 1 &&
                        UnitUtils.isWeaponAvailable(unit) &&
                        InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, unit.getPosition().toPoint2d()))
                .collect(Collectors.toList());

        for (Unit pf : pfList) {
            float range;
            float x_pf = pf.getPosition().getX();
            float y_pf = pf.getPosition().getY();

            //pf range + 2.5 for PF radius +1 for hisec
            if (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                range = 9.5f;
            } else {
                range = 8.5f;
            }

            int xMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getX();
            int xMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX());
            int yMin = 0; //(int) LocationConstants.SCREEN_BOTTOM_LEFT.getY();
            int yMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY());
            int xStart = Math.max(Math.round(2 * (x_pf - range)), xMin);
            int yStart = Math.max(Math.round(2 * (y_pf - range)), yMin);
            int xEnd = Math.min(Math.round(2 * (x_pf + range)), xMax);
            int yEnd = Math.min(Math.round(2 * (y_pf + range)), yMax);


            //get x,y of max value
            int bestValueX = -1;
            int bestValueY = -1;
            int bestValue = 0;
            for (int x = xStart; x <= xEnd; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    if (InfluenceMaps.pointPFTargetValue[x][y] > bestValue &&
                            Position.distance(x / 2f, y / 2f, x_pf, y_pf) < range) {
                        bestValueX = x;
                        bestValueY = y;
                        bestValue = InfluenceMaps.pointPFTargetValue[x][y];

                    }
                }
            }

            Unit bestTargetUnit = null;
            if (bestValue == 0) {
                if (LocationConstants.opponentRace == Race.ZERG) {
                    bestTargetUnit = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, Units.ZERG_CHANGELING_MARINE, pf.getPosition().toPoint2d());
                }
            } else {
                Point2d bestTargetPos = Point2d.of(bestValueX / 2f, bestValueY / 2f);
                DebugHelper.draw3dBox(bestTargetPos, Color.YELLOW, 0.5f);
                //get enemy Unit near bestTargetPos
                UnitInPool enemyTarget = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        UnitUtils.getDistance(pf, bestTargetPos) < 9.5f && !enemy.unit().getFlying().orElse(false))
                        .stream()
                        .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), bestTargetPos)))
                        .orElse(null);

                if (enemyTarget != null) {
                    bestTargetUnit = enemyTarget.unit();
                    DebugHelper.draw3dBox(bestTargetUnit.getPosition().toPoint2d(), Color.RED, 0.4f);
                }
                Bot.DEBUG.sendDebug();
                int w = 234;
            }

            //attack
            if (bestTargetUnit != null) {
                ActionHelper.unitCommand(pf, Abilities.ATTACK, bestTargetUnit, false);
            }
        }
    }

    private static void positionMarines() {
        //new marines get a marine object
        UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).forEach(unit -> {
            UnitMicroList.add(new MarineBasic(unit, LocationConstants.insideMainWall));
        });

        //don't set target if Marine All-in code is handling it
        if (Strategy.MARINE_ALLIN && (MarineAllIn.doAttack || MarineAllIn.isInitialBuildUp)) {
            return;
        }

        //if main/nat under attack, empty natural bunker and target enemy
        Unit bunker = UnitUtils.getCompletedNatBunker();
        Unit enemyInMyBase = getEnemyInMainOrNatural();
        if (enemyInMyBase != null) {
            if (bunker != null) {
                ActionHelper.unitCommand(bunker, Abilities.UNLOAD_ALL_BUNKER, false);
            }
            MarineBasic.setTargetPos(enemyInMyBase.getPosition().toPoint2d());
            return;
        }

        //if scv is building a CC on a base location, target behindCC
        Point2d newMarinePos = StructureScv.scvBuildingList.stream()
                .filter(structureScv -> structureScv.structureType == Units.TERRAN_COMMAND_CENTER &&
                        structureScv.getStructureUnit() != null &&
                        Base.getBase(structureScv.structurePos) != null)
                .findFirst()
                .map(structureScv -> Base.getBase(structureScv.structurePos).getResourceMidPoint())
                .orElse(null);
        if (newMarinePos != null &&
                (bunker == null || !isBehindMainOrNat(newMarinePos))) { //skip if this base is protected behind the bunker
            MarineBasic.setTargetPos(newMarinePos);
            return;
        }

        //if CC is floating to a base location, target behindCC
        newMarinePos = FlyingCC.flyingCCs.stream()
                .filter(flyingCC -> !flyingCC.makeMacroOC)
                .findFirst()
                .map(flyingCC -> Base.getBase(flyingCC.destination).getResourceMidPoint())
                .orElse(null);
        if (newMarinePos != null &&
                (bunker == null || !isBehindMainOrNat(newMarinePos))) { //skip if this base is protected behind the bunker
            MarineBasic.setTargetPos(newMarinePos);
            return;
        }

        //if bunker exists and isn't full, head to bunker and enter
        if (bunker != null && bunker.getCargoSpaceTaken().orElse(4) < 4) {
            MarineBasic.setTargetPos(LocationConstants.BUNKER_NATURAL);
            return;
        }

        //try to kill off the marines as maxed supply is approaching
        if (Bot.OBS.getFoodUsed() >= 160) {
            MarineBasic.setTargetPos(ArmyManager.attackAirPos);
            return;
        }

        //otherwise, go to top of ramp
        MarineBasic.setTargetPos(LocationConstants.insideMainWall);
    }

    private static boolean isBehindMainOrNat(Point2d pos) {
        return GameCache.baseList.get(0).getResourceMidPoint().distance(pos) < 1 ||
                GameCache.baseList.get(1).getResourceMidPoint().distance(pos) < 1;
    }

    private static void positionLiberators() { //positions only 1 liberator per game loop
        Unit idleLib = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_LIBERATOR).stream()
                .filter(unit -> ActionIssued.getCurOrder(unit).isEmpty())
                .findFirst().orElse(null);

        if (idleLib == null) {
            return;
        }

        //send available liberator to siege an expansion
        for (Base base : GameCache.baseList) {
            if (base.isMyBase() && !base.isMyMainBase() && !base.isDryedUp()) { //my expansion bases only
                for (DefenseUnitPositions libPos : base.getLiberators()) {
                    if (libPos.getUnit() == null) {
                        libPos.setUnit(Bot.OBS.getUnit(idleLib.getTag()));
                        Point2d libZonePos = Position.towards(libPos.getPos(), base.getCcPos(), Liberator.castRange);
                        UnitMicroList.add(new LibToPosition(libPos.getUnit(), libPos.getPos(), libZonePos));
                        return;
                    }
                }
            }
        }

        //if no bases need a liberator, put it on offense
        UnitMicroList.add(new LibOffense(Bot.OBS.getUnit(idleLib.getTag()), ArmyManager.attackGroundPos));

    }

    private static void positionTanks() { //positions only 1 tank per game loop
        if (GameCache.siegeTankList.isEmpty()) {
            return;
        }

        //send available tank to siege an expansion
        Unit idleTank = GameCache.siegeTankList.get(0);
        if (Strategy.DO_DEFENSIVE_TANKS) {
            for (Base base : GameCache.baseList) {
                if (base.isMyBase() && !base.isMyMainBase() && !base.isDryedUp()) { //my expansion bases only
                    for (DefenseUnitPositions tankPos : base.getTanks()) {
                        if (tankPos.getUnit() == null) {
                            tankPos.setUnit(Bot.OBS.getUnit(idleTank.getTag()));
                            UnitMicroList.add(new TankToPosition(tankPos.getUnit(), tankPos.getPos(), MicroPriority.SURVIVAL));
                            return;
                        }
                    }
                }
            }
        }

        //if no bases need a tank, put it on offense
        UnitMicroList.add(new TankOffense(Bot.OBS.getUnit(idleTank.getTag()), ArmyManager.attackGroundPos));

//        //if nowhere to send tank and no expansions available, a-move tank to its death
//        if (!isTankPlaced && allButEnemyStarterBases.stream().noneMatch(base -> base.isUntakenBase() && !base.isDryedUp())) {
//            GameCache.baseList.stream()
//                    .filter(base -> base.isEnemyBase)
//                    .forEach(base -> ActionHelper.unitCommand(idleTank, Abilities.ATTACK, base.getCcPos(), true));
//            ActionHelper.unitCommand(idleTank, Abilities.ATTACK, GameCache.baseList.get(GameCache.baseList.size()-1).getCcPos(), true);
//        }
    }

    private static void salvageBunkerAtNatural() {
        List<UnitInPool> bunkerList = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BUNKER &&
                UnitUtils.getDistance(u.unit(), LocationConstants.BUNKER_NATURAL) < 1);
        if (bunkerList.isEmpty()) {
            return;
        }
        Unit bunker = bunkerList.get(0).unit();
        if (UnitUtils.getHealthPercentage(bunker) < 40 || bunkerUnnecessary()) {
            ActionHelper.unitCommand(bunker, Abilities.UNLOAD_ALL_BUNKER, false); //rally is already set to top of inside main wall
            ActionHelper.unitCommand(bunker, Abilities.EFFECT_SALVAGE, false);
        }
    }

    //return true when bunker is no longer needed
    private static boolean bunkerUnnecessary() {
        return GameCache.baseList.get(1).getCc() != null &&
                GameCache.baseList.get(1).getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS &&
                //take down bunker when 1st defense banshee is out, or when floating to 3rd base
                (!Strategy.DO_LEAVE_UP_BUNKER || !GameCache.bansheeList.isEmpty() ||
                        FlyingCC.flyingCCs.stream()
                                .anyMatch(flyingCC -> flyingCC.destination.distance(GameCache.baseList.get(2).getCcPos()) < 1));
    }

    private static void raiseAndLowerDepots() {
        for(Unit depot : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT)) {
            Point2d depotPos = depot.getPosition().toPoint2d();
            if (!InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos)) {
                ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
            }
        }
        for(Unit depot : UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT_LOWERED)) {
            Point2d depotPos = depot.getPosition().toPoint2d();
            if (InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos) &&
                    (CannonRushDefense.cannonRushStep == 0 || !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_ZEALOT).isEmpty())) {
                ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
            }
        }
    }

    public static void spreadArmy(List<Unit> army) {
        for (Unit unit : army) {
            if (ActionIssued.getCurOrder(unit).isEmpty()) {
                ActionHelper.unitCommand(unit, Abilities.ATTACK, Bot.OBS.getGameInfo().findRandomLocation(), false);
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
                    answer += 0.5;
                    hasDetector = true;
                    break;
                case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG: case TERRAN_BANSHEE:
                case ZERG_MUTALISK: case ZERG_VIPER: case ZERG_BROODLORD_COCOON: case ZERG_BROODLORD:
                case PROTOSS_ORACLE:
                    answer += 0.5;
                    break;
                case TERRAN_VIKING_FIGHTER: case TERRAN_VIKING_ASSAULT:
                    answer += 1.67;
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
        boolean isUnsafe = (Switches.isDivingTempests) ? false : InfluenceMaps.pointThreatToAirValue[x][y] > 2;
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke();
        boolean isInDetectionRange = InfluenceMaps.pointDetected[x][y];
        boolean isInBansheeRange = InfluenceMaps.pointInBansheeRange[x][y];
        boolean canAttack = UnitUtils.isWeaponAvailable(banshee) && InfluenceMaps.pointThreatToAirValue[x][y] < 200;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0f) > Strategy.ENERGY_BEFORE_CLOAKING &&
                Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK);
        boolean isParasitic = banshee.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways
        boolean hasDecloakBuff = UnitUtils.hasDecloakBuff(banshee);
        boolean isAnyBaseUnderAttack = !doOffense && attackUnit != null;
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : Strategy.RETREAT_HEALTH;

        //always flee if locked on by cyclone
        if (banshee.getBuffs().contains(Buffs.LOCK_ON)) {
            if (!isInDetectionRange && canCloak && !hasDecloakBuff) {
                ActionHelper.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
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
            ActionHelper.unitCommand(banshee, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
        }
        else if (isUnsafe) {
            if (isInDetectionRange) {
                //retreat
                //retreatMyUnit(banshee);
                Point2d kiteBackPos = getKiteBackPos(banshee);
                if (kiteBackPos == null) {
                    kiteBackPos = retreatPos;
                }
                if (!InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, kiteBackPos)) {
                    ActionHelper.unitCommand(banshee, Abilities.MOVE, kiteBackPos, false);
                }
                else {
                    new BasicUnitMicro(banshee, retreatPos, MicroPriority.SURVIVAL).onStep();
                }
                //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
            }
            else if (cloakState != CloakState.NOT_CLOAKED &&
                    banshee.getEnergy().get() > 3 + ((UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() > 2) ? 2 : 0)) { //additional energy for time to flee tempest range
                if (isInBansheeRange) { //maintain max range
                    //retreat
                    new BasicUnitMicro(banshee, retreatPos, MicroPriority.SURVIVAL).onStep();
                    //retreatMyUnit(banshee);
                    //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
                }
                else {
                    //attack
                    if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(banshee);
                }
            }
            else if (canCloak && !hasDecloakBuff) {
                //cloak
                ActionHelper.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            }
            else {
                //retreat
                new BasicUnitMicro(banshee, retreatPos, MicroPriority.SURVIVAL).onStep();
                //retreatMyUnit(banshee);
                //if (lastCommand != ArmyCommands.RETREAT) armyGoingHome.add(banshee);
            }
        }
        //staying in repair bay if not full health and if needing energy
        else if (canRepair && //not broke
                UnitUtils.getDistance(banshee, retreatPos) < 3 && //at repair bay
                UnitUtils.getHealthPercentage(banshee) < 100) { //wait for heal
            if (cloakState == CloakState.CLOAKED_ALLIED && !isUnsafe) {
                ActionHelper.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_OFF_BANSHEE, false);
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

    private static Point2d getKiteBackPos(Unit myUnit) {
        Unit closestEnemy = UnitUtils.getClosestEnemyThreat(myUnit);
        if (closestEnemy == null) {
            return null;
        }
        Point2d enemyPos = closestEnemy.getPosition().toPoint2d();
        Point2d retreatPos = Position.towards(myUnit.getPosition().toPoint2d(), enemyPos, -4);
        if (Position.isOnBoundary(retreatPos)) { //ignore when kited to the edge of the map
            return null;
        }
        if (!myUnit.getFlying().orElse(true) && !Bot.OBS.isPathable(retreatPos)) { //ignore unpathable positions
            return null;
        }
        return retreatPos;
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
            ActionHelper.unitCommand(myUnit, Abilities.MOVE, Position.towards(myUnit.getPosition().toPoint2d(), cyclonePos, -4f), false);
        }
    }

    private static boolean isWaitingForCloak(boolean canCloak, CloakState cloakState) {
        return !Strategy.MASS_RAVENS && !canCloak && cloakState != CloakState.CLOAKED_ALLIED;
        //not mass ravens AND can't cloak AND not cloaked
    }

    private static ArmyCommands getCurrentCommand(Unit unit) {
        ArmyCommands currentCommand = ArmyCommands.EMPTY;
        if (ActionIssued.getCurOrder(unit).isPresent()) {
            ActionIssued curAction = ActionIssued.getCurOrder(unit).get();
            if (curAction.ability == Abilities.ATTACK) {
                if (curAction.targetPos != null &&
                        curAction.targetPos.distance(attackGroundPos) < 1) {
                    currentCommand = ArmyCommands.ATTACK;
                }
                else if (curAction.targetTag != null) {
                    currentCommand = ArmyCommands.DIVE;
                }
            }
            else if (curAction.ability == Abilities.MOVE &&
                    curAction.targetPos != null &&
                    curAction.targetPos.distance(ArmyManager.retreatPos) < 1) {
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
        boolean stayBack = false;
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : Strategy.RETREAT_HEALTH;


        //keep vikings back vs tempests or vikings until ready to engage
        if (doStayBackFromTempests() || isOutnumberedInVikings()) {
            isUnsafe = InfluenceMaps.pointVikingsStayBack[x][y];
            stayBack = true;
        }

        boolean isInVikingRange = InfluenceMaps.enemyInVikingRange[x][y];
        boolean canAttack = UnitUtils.isWeaponAvailable(viking);
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
            ActionHelper.unitCommand(viking, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
        }
        //in enemy attack range, then back up
        else if (isUnsafe) {
            if (stayBack) {
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
            }
            else {
                Point2d kiteBackPos = getKiteBackPos(viking);
                if (kiteBackPos == null) {
                    kiteBackPos = retreatPos;
                }
                if (InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirFromGround, kiteBackPos) == 0) {
                    ActionHelper.unitCommand(viking, Abilities.MOVE, kiteBackPos, false);
                }
                else {
                    new BasicUnitMicro(viking, retreatPos, MicroPriority.SURVIVAL).onStep();
                }
            }
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

    private static boolean isOutnumberedInVikings() {
        return GameCache.vikingList.size() < UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).size();
    }

    private static boolean doStayBackFromTempests() {
        return !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty() && //TODO: change below to use Gamestate.allVisibleEnemiesMap
                Bot.OBS.getUnits(Alliance.ENEMY,
                        e -> (e.unit().getType() == Units.PROTOSS_PHOENIX || e.unit().getType() == Units.PROTOSS_VOIDRAY || e.unit().getType() == Units.PROTOSS_INTERCEPTOR)
                        && !e.unit().getHallucination().orElse(false)).isEmpty();
    }

    //return true if autoturret cast
    private static void giveRavenCommand(Unit raven, boolean doCastTurrets) {

        //wait for raven to auto-turret before giving a new command
        if (!isAttackUnitRetreating && UnitUtils.getOrder(raven) == Abilities.EFFECT_AUTO_TURRET) {
            return;
        }

        ArmyCommands lastCommand = getCurrentCommand(raven);
        boolean[][] threatMap = (raven.getEnergy().orElse(0f) >= (Strategy.DO_MATRIX ? 75 : Strategy.AUTOTURRET_AT_ENERGY))
                ? InfluenceMaps.pointThreatToAir
                : (Strategy.MASS_RAVENS) ? InfluenceMaps.pointVikingsStayBack : InfluenceMaps.pointThreatToAirPlusBuffer;
        boolean isUnsafe = InfluenceMaps.getValue(threatMap, raven.getPosition().toPoint2d());
        boolean inRange = InfluenceMaps.getValue(InfluenceMaps.pointInRavenCastRange, raven.getPosition().toPoint2d());
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
            ActionHelper.unitCommand(raven, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size()-1), false);
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
                raven.getEnergy().orElse(0f) < 35) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
        }

        //back up if in range
        else if (isUnsafe || inRange) {
            if (!Strategy.DO_SEEKER_MISSILE || !castSeeker(raven)) {
                if (!Strategy.DO_MATRIX || !castMatrix(raven)) {
                    if (!doCastTurrets || !doAutoTurret(raven)) {
                        if (isUnsafe) {
                            Point2d kiteBackPos = getKiteBackPos(raven);
                            if (kiteBackPos == null) {
                                kiteBackPos = Position.towards(raven.getPosition().toPoint2d(), retreatPos, -4);
                            }
                            if (!InfluenceMaps.getValue(threatMap, kiteBackPos)) {
                                ActionHelper.unitCommand(raven, Abilities.MOVE, kiteBackPos, false);
                            } else {
                                new BasicUnitMicro(raven, retreatPos, MicroPriority.SURVIVAL).onStep();
                            }
                        } else if (lastCommand != ArmyCommands.HOME) {
                            armyGoingHome.add(raven);
                        }
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
        if (raven.getEnergy().orElse(0f) >= 50 &&
                !raven.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE) &&
                UnitUtils.getDistance(raven, attackGroundPos) < 12 &&
                attackUnit != null) {
            return castAutoTurret(raven, 0);
        }
        return false;
    }

    //drop auto-turrets near enemy
    private static boolean doAutoTurret(Unit raven) {
        if (!isAttackUnitRetreating &&
                raven.getEnergy().orElse(0f) >= Strategy.AUTOTURRET_AT_ENERGY &&
                !raven.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE)) {
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
            ActionHelper.unitCommand(raven, Abilities.EFFECT_AUTO_TURRET, placementPos, false);
            turretsCast++;
            return true;
        }
        else if (attackUnit != null && !UnitUtils.canMove(attackUnit) && towardsEnemyDistance < 4) {
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
            ActionHelper.unitCommand(raven, Abilities.EFFECT_ANTI_ARMOR_MISSILE, targetUnitList.get(0).unit(), false);
            prevSeekerFrame = Time.nowFrames();
            return true;
        }
        return false;
    }

    private static boolean castMatrix(Unit raven) {
        //cast matrix only once every 1sec TODO: track matrices
        if (Time.nowFrames() < prevSeekerFrame + 24) {
            return false;
        }
        float ravenEnergy = raven.getEnergy().orElse(0f);
        if (ravenEnergy >= 75) {
            Unit targetUnit = findMatrixTarget(raven);
            if (targetUnit == null) {
                return false;
            }
            ActionHelper.unitCommand(raven, Abilities.EFFECT_INTERFERENCE_MATRIX, targetUnit, false);
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

    private static Unit findMatrixTarget(Unit raven) {
        List<UnitInPool> enemyMatrixTargetsInRange = Bot.OBS.getUnits(Alliance.ENEMY, u ->
                u.unit().getType() == Units.TERRAN_SIEGE_TANK_SIEGED &&
                !u.unit().getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE) &&
                UnitUtils.getDistance(u.unit(), raven) <= 9);
        if (enemyMatrixTargetsInRange.isEmpty()) {
            return null;
        }
        else {
            return enemyMatrixTargetsInRange.get(0).unit();
        }
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
        return numAttackersNearby >= numNeededToDive(enemy, InfluenceMaps.pointThreatToAirValue[x][y]);
    }

    public static boolean shouldDiveTempests(Point2d closestTempest, int numVikingsNearby) {
        //if not enough vikings to deal with the tempests
        if (numVikingsNearby < Math.min(Strategy.MAX_VIKINGS_TO_DIVE_TEMPESTS, ArmyManager.calcNumVikingsNeeded() * 0.7)) {
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
            ActionHelper.unitCommand(bio, Abilities.ATTACK, expansionPos, true);
        }
    }

    public static boolean isEnemyInMain() {
        if (attackUnit == null) {
            return false;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, attackUnit.getPosition().toPoint2d());
    }

    public static boolean isEnemyInNatural() {
        if (attackUnit == null) {
            return false;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointInNat, attackUnit.getPosition().toPoint2d());
    }

    public static boolean enemyInNaturalPastBunker() {
        if (attackUnit == null) {
            return false;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointInNatExcludingBunkerRange, attackUnit.getPosition().toPoint2d());
    }

    public static Unit getEnemyInMainOrNatural() {
        return Bot.OBS.getUnits(Alliance.ENEMY).stream()
                .filter(enemy ->
                        InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()) ||
                        InfluenceMaps.getValue(InfluenceMaps.pointInNatExcludingBunkerRange, enemy.unit().getPosition().toPoint2d()))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(0).getCcPos())))
                .map(UnitInPool::unit)
                .orElse(null);
    }
}
