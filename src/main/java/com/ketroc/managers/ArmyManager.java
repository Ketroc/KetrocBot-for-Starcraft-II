package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.GameResult;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.micro.Target;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.MarineAllIn;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.ProxyBunkerDefense;
import com.ketroc.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ArmyManager {
    public static boolean doOffense;
    public static Point2d retreatPos;

    public static Point2d attackGroundPos;
    public static Point2d attackAirPos;
    public static UnitInPool leadTank;
    public static Point2d attackEitherPos;
    public static Point2d attackCloakedPos;

    public static Unit attackUnit;
    public static boolean isAttackUnitRetreating;

    public static List<Unit> armyGoingHome;
    public static List<Unit> armyGroundAttacking;
    public static List<Unit> armyDetectorAttacking;
    public static List<Unit> armyAirAttacking;

    public static Point2d groundAttackersMidPoint;
    public static Point2d vikingMidPoint;

    public static long prevSeekerFrame;
    public static long prevScanFrame;
    public static int numAutoturretsAvailable;
    public static int turretsCast;
    public static int queriesMade;

    public static final int[] BASE_DEFENSE_INDEX_ORDER = {2, 4, 1, 3, 5, 6, 7, 8, 9, 10, 11, 12};

    public static void onStep() {
        setArmyMidpoints();
        setDoOffense();
        if (doOffense) {
            setGroundTarget();
        }
        else {
            setDefensePosition();
        }
        setLeadTank();
        setAirTarget();
        sendAirKillSquad();
        setIsAttackUnitRetreating();
        setAirOrGroundTarget();
        setCloakedTarget();

        // lift & lower depot walls
        raiseAndLowerDepots();

        //respond to nydus
        nydusResponse();

        //positioning siege tanks && tank targetting
        if (!BunkerContain.requiresTanks()) {
            positionTanks();
        }

        //position liberators
        positionLiberators();

        //empty bunker after PF at natural is done
        salvageBunkerAtNatural();

        //position marines
        if (BunkerContain.proxyBunkerLevel == 0) {
            if (!UnitUtils.isOutOfGas()) {
                addMarines();
                positionMarines();
            }
        }

        //TODO: this is a temporary test
        UnitUtils.getMyUnitsOfType(Units.TERRAN_CYCLONE).forEach(cyclone -> {
            UnitMicroList.add(new Cyclone(cyclone, LocationConstants.insideMainWall));
        });

        //repair station
        manageRepairBay();

        //maintain repair scvs on offense with tanks
        manageTankRepairScvs();

        //if searching for last structures
        if (attackGroundPos == null && Switches.finishHim) {
            searchForLastStructures();
        }
        else {
            armyGoingHome = new ArrayList<>();
            armyGroundAttacking = new ArrayList<>();
            armyAirAttacking = new ArrayList<>();
            armyDetectorAttacking = new ArrayList<>();

            hellionMicro();
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
            if (!armyDetectorAttacking.isEmpty()) {
                Point2d targetPos = attackCloakedPos != null ? attackCloakedPos : attackGroundPos;
                ActionHelper.unitCommand(armyDetectorAttacking, Abilities.ATTACK, targetPos, false);
            }

            pfTargetting();
            libTargetting();
            autoturretTargetting();
        }

        //send out marine+hellbat army
        sendMarinesHellbats();
    }

    private static void setLeadTank() {
        leadTank = UnitMicroList.getUnitSubList(TankOffense.class)
                .stream()
                .min(Comparator.comparing(tank -> UnitUtils.getDistance(tank.unit.unit(), attackGroundPos)))
                .map(tankOffense -> tankOffense.unit)
                .orElse(null);
    }

    //new marines get a marine object TODO: move
    private static void addMarines() {
        if (Strategy.gamePlan == GamePlan.MARINE_RUSH) {
            UnitUtils.getMyUnitsOfType(Units.TERRAN_MARINE).forEach(unit -> {
                UnitMicroList.add(new MarineOffense(unit, LocationConstants.insideMainWall));
            });
        }
        else {
            UnitUtils.getMyUnitsOfType(Units.TERRAN_MARINE).forEach(unit -> {
                UnitMicroList.add(new MarineBasic(unit, LocationConstants.insideMainWall));
            });
        }
    }

    //add scvs when tanks move out, or when tanks need repair on defense
    private static void manageTankRepairScvs() {
        List<TankOffense> tankList = UnitMicroList.getUnitSubList(TankOffense.class);
        if ((doOffense && !tankList.isEmpty()) ||
                tankList.stream().anyMatch(tankOffense -> tankOffense.unit.isAlive() &&
                        UnitUtils.getHealthPercentage(tankOffense.unit.unit()) < 99)) {
            int numScvsToAdd = Strategy.NUM_OFFENSE_SCVS - UnitMicroList.getUnitSubList(ScvRepairer.class).size();
            if (!tankList.isEmpty()) {
                for (int i=0; i<numScvsToAdd; i++) {
                    UnitInPool closestAvailableScv = WorkerManager.getClosestAvailableScv(
                            tankList.get(0).unit.unit().getPosition().toPoint2d());
                    if (closestAvailableScv == null) {
                        return;
                    }
                    UnitMicroList.add(new ScvRepairer(closestAvailableScv));
                }
            }
        }
    }

    private static void setIsAttackUnitRetreating() {
        if (attackUnit == null) {
            isAttackUnitRetreating = false;
            return;
        }

        isAttackUnitRetreating = UnitUtils.isEnemyRetreating(attackUnit, groundAttackersMidPoint);
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
        spreadArmy(UnitUtils.getMyUnitsOfType(UnitUtils.HELLION_TYPE));
    }

    private static void sendMarinesHellbats() {
        if (UnitUtils.isOutOfGas()) {
            List<Unit> army = UnitUtils.getMyUnitsOfType(Units.TERRAN_MARINE);
            army.addAll(UnitUtils.getMyUnitsOfType(Units.TERRAN_HELLION_TANK));
            if (Bot.OBS.getFoodUsed() >= 198 || Cost.isMineralBroke(50)) {
                if (army.stream().anyMatch(unit -> ActionIssued.getCurOrder(unit).isPresent())) {
                    ActionHelper.unitCommand(army, Abilities.ATTACK, attackGroundPos, false);
                }
            }
        }
    }

    //shoot best target, otherwise changelings, otherwise let autoattack happen
    private static void autoturretTargetting() {
        UnitUtils.getMyUnitsOfType(Units.TERRAN_AUTO_TURRET).stream()
                .filter(turret -> UnitUtils.isWeaponAvailable(turret))
                .forEach(turret -> {
                    selectTarget(turret).ifPresentOrElse(target -> ActionHelper.unitCommand(turret, Abilities.ATTACK, target, false),
                            () -> {
                                UnitUtils.getEnemyTargetsNear(turret, 8).stream()
                                        .findFirst()
                                        .ifPresent(target -> ActionHelper.unitCommand(turret, Abilities.ATTACK, target.unit(), false));
                            });
                });
    }

    private static Optional<Unit> selectTarget(Unit turret) {
        List<UnitInPool> enemiesInRange = UnitUtils.getEnemyTargetsNear(turret, 8);

        Target bestTarget = new Target(null, Float.MIN_VALUE, Float.MAX_VALUE); //best target will be lowest hp unit without barrier
        for (UnitInPool enemy : enemiesInRange) {
            if (UnitUtils.CREEP_TUMOR_TYPES.contains(enemy.unit().getType())) { //shoot creep tumors first
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

    private static void hellionMicro() {
        for (Unit hellion : UnitUtils.getMyUnitsOfType(Units.TERRAN_HELLION)) {
            giveHellionCommand(hellion);
        }
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
                            UnitUtils.scan(Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -5));
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
        //attack if no mining (prevents draws) (ignore start of game)
        if (Bot.OBS.getScore().getDetails().getCollectionRateMinerals() == 0 &&
                Bot.OBS.getScore().getDetails().getCollectionRateVespene() == 0 &&
                Time.nowFrames() > Time.toFrames("12:00")) {
            doOffense = true;
            return;
        }

        if (ProxyBunkerDefense.isProxyBunker) {
            doOffense = true;
            return;
        }

        if (Strategy.gamePlan == GamePlan.MARINE_RUSH) {
            doOffense = MarineAllIn.getDoOffense();
            return;
        }

        if (BunkerContain.proxyBunkerLevel > 0 || UnitUtils.isOutOfGas()) {
            doOffense = true;
            return;
        }

        //testing 4hellion early attack TvZ
//        if (LocationConstants.opponentRace == Race.ZERG && UnitUtils.numMyUnits(Units.TERRAN_HELLION, false) >= 4) {
//            doOffense = true;
//            return;
//        }

        //TODO: testing cyclones
        //move out with 4+ cyclones and an air unit
        if (Strategy.DO_USE_CYCLONES &&
                (UnitMicroList.getUnitSubList(Cyclone.class).size() > 4 ||
                        (!GameCache.bansheeList.isEmpty() || !GameCache.vikingList.isEmpty() || !GameCache.ravenList.isEmpty()))) {
            doOffense = true;
            return;
        }

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
            return;
        }
        boolean oldDoOffense = doOffense;
        doOffense = GameCache.bansheeList.size() +
                (UnitMicroList.getUnitSubList(TankOffense.class).size() * 0.75) +
                (GameCache.ravenList.size() * 0.34) >= 6 &&
                !isOutnumberedInVikings();
        if (doOffense != oldDoOffense) {
            Chat.chat("doOffense " + doOffense);
            Chat.chat("Army score: = " + (GameCache.bansheeList.size() +
                    (UnitMicroList.getUnitSubList(TankOffense.class).size() * 0.75) +
                    (GameCache.ravenList.size() * 0.34)));
            if (LocationConstants.opponentRace == Race.TERRAN) {
                Chat.chat("My frontline vikings = " + GameCache.vikingList.stream()
                        .filter(viking -> UnitUtils.getDistance(viking, attackAirPos) < 20 &&
                                UnitUtils.getHealthPercentage(viking) > Strategy.RETREAT_HEALTH)
                        .count());
                Chat.chat("Enemy vikings = " + UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).stream()
                        .filter(enemyViking -> enemyViking.getLastSeenGameLoop() + Time.toFrames(5) > Time.nowFrames())
                        .count());
            }
            //KetrocBot.printCurrentGameInfo();
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

    //set defensePos to closestEnemy to an injured base cc
    private static void setDefensePosition() {
        attackUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(u -> GameCache.baseList.stream()
                                .filter(base -> base.isMyBase() || base.isNatBaseAndHasBunker())
                                .anyMatch(base -> UnitUtils.getDistance(u.unit(), base.getCcPos()) < 25) && //close to any of my bases
                        !UnitUtils.NO_THREAT_ENEMY_AIR.contains(u.unit().getType()) &&
                        u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED && //ignore cloaked units
                        u.unit().getDisplayType() != DisplayType.HIDDEN && //ignore undetected cloaked/burrowed units
                        !UnitUtils.IGNORED_TARGETS.contains(u.unit().getType()) && //ignore eggs/autoturrets/etc
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
//        else if (GameCache.baseList.get(2).isMyBase()) {
//            attackGroundPos = GameCache.baseList.get(2).getResourceMidPoint();
//        }
        else {
            attackGroundPos = getArmyRallyPoint();
        }
    }

    private static Point2d getArmyRallyPoint() {
        if (Strategy.gamePlan == GamePlan.MARINE_RUSH && MarineAllIn.isInitialBuildUp) {
            return GameCache.baseList.get(0).getResourceMidPoint();
        }

        //protect 3rd if OC
        if (Strategy.NUM_BASES_TO_OC >= 3 && GameCache.baseList.get(2).isMyBase()) {
            return GameCache.baseList.get(2).inFrontPos();
        }
        //protect nat if OC or if no base but bunker is up there
        if (!UnitUtils.isWallUnderAttack() &&
                ((Strategy.NUM_BASES_TO_OC >= 2 && GameCache.baseList.get(1).isMyBase()) ||
                UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL, 1))) {
            return Position.towards(LocationConstants.BUNKER_NATURAL, GameCache.baseList.get(1).getCcPos(), 3);
        }
        //protect main ramp
        return LocationConstants.insideMainWall;
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
                Chat.tag("PHANTOM_UNIT");
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
            //go on to next base after a banshee, viking, and raven have arrived
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
        UnitInPool closestEnemyAir = getClosestEnemyAirUnit();
        List<TankOffense> tankList = UnitMicroList.getUnitSubList(TankOffense.class);

        //attack closest enemy air unit, but stay close to tanks if TankOffense units are in use
        if (closestEnemyAir != null &&
                (Base.nearOneOfMyBases(closestEnemyAir.unit(), 25) ||
                        tankList.isEmpty() ||
                        tankList.stream().anyMatch(tankOffense ->
                                UnitUtils.getDistance(tankOffense.unit.unit(), closestEnemyAir.unit()) < 20))) {
            attackAirPos = closestEnemyAir.unit().getPosition().toPoint2d();
            return;
        }

        //cover lead siege tank
        if (leadTank != null) {
            attackAirPos = Position.towards(leadTank.unit().getPosition().toPoint2d(),
                    attackGroundPos,
                    leadTank.unit().getType() == Units.TERRAN_SIEGE_TANK ? 8f : 4.5f);
            return;
        }

        //attack ground target pos
        attackAirPos = attackGroundPos;

    }

    //set to whichever position is closer to my main base
    private static void setAirOrGroundTarget() {
        if (attackGroundPos == null) {
            attackEitherPos = attackAirPos;
        }
        else if (attackAirPos == null) {
            attackEitherPos = attackGroundPos;
        }
        else if (LocationConstants.mainBaseMidPos.distance(attackGroundPos) <
                LocationConstants.mainBaseMidPos.distance(attackAirPos)) {
            attackEitherPos = attackGroundPos;
        }
        else {
            attackEitherPos = attackAirPos;
        }
    }

    //set the target of a unit that may require detection
    private static void setCloakedTarget() {
        attackCloakedPos = GameCache.allVisibleEnemiesList.stream()
                .filter(enemy -> enemy.unit().getType() != Units.ZERG_CREEP_TUMOR_BURROWED &&
                        UnitUtils.requiresDetection(enemy.unit()))
                .min(Comparator.comparing(enemy ->
                        UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(1).getCcPos())))
                .map(enemy -> enemy.unit().getPosition().toPoint2d())
                .orElse(null);
    }

    //snipe unprotected enemy air units that are wandering away from protection TODO: check all enemy air units, not just closest
    private static void sendAirKillSquad() {
        if (GameCache.vikingList.isEmpty()) {
            return;
        }

        GameCache.allEnemiesList.stream()
                .filter(enemyAirUip -> UnitUtils.VIKING_PEEL_TARGET_TYPES.contains(enemyAirUip.unit().getType()) &&
                        !enemyAirUip.unit().getHallucination().orElse(false) &&
                        enemyAirUip.getLastSeenGameLoop() + 24 >= Time.nowFrames() &&
                        !Ignored.contains(enemyAirUip.getTag()) &&
                        InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, enemyAirUip.unit().getPosition().toPoint2d())
                                < AirUnitKillSquad.MAX_THREAT)
                .min(Comparator.comparing(enemyAirUip ->
                        UnitUtils.getDistance(enemyAirUip.unit(), LocationConstants.baseLocations.get(0))))
                .ifPresent(enemyAirUip -> AirUnitKillSquad.add(enemyAirUip));
    }

    private static UnitInPool getClosestEnemyGroundUnit() {
        UnitInPool closestEnemyGroundUnit = GameCache.allEnemiesList.stream()
                .filter(u -> u.getLastSeenGameLoop() + 24 >= Time.nowFrames() &&
                        !u.unit().getFlying().orElse(false) && //ground unit
                        (!GameCache.ravenList.isEmpty() || u.unit().getDisplayType() != DisplayType.HIDDEN) && //TODO: handle with scan?
                        !UnitUtils.IGNORED_TARGETS.contains(u.unit().getType()) &&
                        u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                        !u.unit().getHallucination().orElse(false)) //ignore hallucs
                .min(Comparator.comparing(u ->
                        UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), groundAttackersMidPoint) +
                        ((attackGroundPos != null && UnitUtils.getDistance(u.unit(), attackGroundPos) < 5) ? -3 : 0))) //preference to maintaining similar target preventing wiggling
                .orElse(null);
        return closestEnemyGroundUnit;
    }

    private static UnitInPool getClosestEnemyAirUnit() {
        return GameCache.allEnemiesList.stream()
                .filter(u -> u.getLastSeenGameLoop() + 24 >= Time.nowFrames() &&
                        (!Ignored.contains(u.getTag()) || AirUnitKillSquad.containsWithNoVikings(u.getTag())) &&
                        u.unit().getFlying().orElse(false) && //air unit
                        (!GameCache.ravenList.isEmpty() || u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED) && //ignore cloaked units with no raven TODO: handle banshees DTs etc with scan
                        u.unit().getType() != Units.ZERG_PARASITIC_BOMB_DUMMY &&
                        !u.unit().getHallucination().orElse(false)) //ignore hallucs
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), LocationConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), vikingMidPoint)))
                .orElse(null);
    }

    //send scvs, unsieged tanks, and bio to kill nydus in my main base
    private static void nydusResponse() {
        Optional<UnitInPool> nydusWorm = UnitUtils.getEnemyUnitsOfType(Units.ZERG_NYDUS_CANAL).stream().findFirst();
        if (nydusWorm.stream()
                .anyMatch(nydusUIP -> InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, nydusUIP.unit().getPosition().toPoint2d()))) {
            GameResult.setNydusRushed(); //TODO: temp for Spiny
            List<Unit> nydusDivers = new ArrayList<>();
            nydusDivers.addAll(UnitUtils.getMyUnitsOfType(Units.TERRAN_MARINE));
            nydusDivers.addAll(UnitUtils.getMyUnitsOfType(Units.TERRAN_MARAUDER));
            nydusDivers.addAll(UnitUtils.getMyUnitsOfType(Units.TERRAN_SIEGE_TANK));
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
        List<Unit> pfList = UnitUtils.getMyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS).stream()
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
                //DebugHelper.draw3dBox(bestTargetPos, Color.YELLOW, 0.5f);
                //get enemy Unit near bestTargetPos
                UnitInPool enemyTarget = Bot.OBS.getUnits(Alliance.ENEMY, enemy ->
                        enemy.unit().getDisplayType() == DisplayType.VISIBLE &&
                        UnitUtils.getDistance(pf, bestTargetPos) < 9.5f && !enemy.unit().getFlying().orElse(false))
                        .stream()
                        .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), bestTargetPos)))
                        .orElse(null);

                if (enemyTarget != null) {
                    bestTargetUnit = enemyTarget.unit();
                    //DebugHelper.draw3dBox(bestTargetUnit.getPosition().toPoint2d(), Color.RED, 0.4f);
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
        //don't set target if Marine All-in code is handling it
        if (Strategy.MARINE_ALLIN && (MarineAllIn.doAttack || MarineAllIn.isInitialBuildUp)) {
            return;
        }

        //if main/nat under attack, empty natural bunker and target enemy
        Optional<UnitInPool> bunkerAtNatural = UnitUtils.getNatBunker();
        Unit enemyInMyBase = (!GameCache.baseList.get(1).isMyBase() && bunkerAtNatural.isEmpty()) ?
                getEnemyInMain() :
                getEnemyInMainOrNatural(bunkerAtNatural.isPresent());
        if (enemyInMyBase != null) {
            bunkerAtNatural
                    .filter(bunker -> UnitUtils.getDistance(bunker.unit(), enemyInMyBase) > 8)
                    .ifPresent(bunker -> {
                        if (bunker.unit().getCargoSpaceTaken().orElse(0) > 0) {
                            ActionHelper.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
                        }
                    });
            MarineBasic.setTargetPos(enemyInMyBase.getPosition().toPoint2d());
            return;
        }

        //if bunker in production, head out front of bunker (protects bunker scv, and gives chance to snipe overlord)
        if (bunkerAtNatural.isPresent() && bunkerAtNatural.get().unit().getBuildProgress() < 1) {
            Point2d inFrontOfBunkerPos = Position.towards(LocationConstants.BUNKER_NATURAL, LocationConstants.enemyMineralPos, 1.8f);
            MarineBasic.setTargetPos(inFrontOfBunkerPos);
            return;
        }

        //if bunker exists, head to bunker and enter
        if (bunkerAtNatural.isPresent()) {
            MarineBasic.setTargetPos(LocationConstants.BUNKER_NATURAL);
            return;
        }

        //cover lead tank
        if (leadTank != null) {
            Point2d marineAttackPos = Position.towards(
                    leadTank.unit().getPosition().toPoint2d(),
                    attackGroundPos,
                    leadTank.unit().getType() == Units.TERRAN_SIEGE_TANK ? 3.5f : 1.5f);
            MarineBasic.setTargetPos(marineAttackPos);
            return;
        }

        //attack when doOffense is true
        if (ArmyManager.doOffense) {
            MarineBasic.setTargetPos(ArmyManager.attackEitherPos);
            return;
        }

//        //cover natural ramp if nat is an OC base
//        if (GameCache.baseList.get(1).getCc() != null && Strategy.NUM_BASES_TO_OC > 1) {
//            MarineBasic.setTargetPos(LocationConstants.BUNKER_NATURAL);
//            return;
//        }

        //otherwise, go to top of ramp
        MarineBasic.setTargetPos(LocationConstants.insideMainWall);
    }

    private static boolean isBehindMainOrNat(Point2d pos) {
        return GameCache.baseList.get(0).getResourceMidPoint().distance(pos) < 1 ||
                GameCache.baseList.get(1).getResourceMidPoint().distance(pos) < 1;
    }

    private static void positionLiberators() { //positions only 1 liberator per game loop
        Unit idleLib = UnitUtils.getMyUnitsOfType(Units.TERRAN_LIBERATOR).stream()
                .filter(unit -> ActionIssued.getCurOrder(unit).isEmpty())
                .findFirst().orElse(null);

        if (idleLib == null) {
            return;
        }

        //send available liberator to siege an expansion
        //for (Base base : GameCache.baseList) {
        for (int i=0; i<BASE_DEFENSE_INDEX_ORDER.length && i<GameCache.baseList.size(); i++) {
            Base base = GameCache.baseList.get(BASE_DEFENSE_INDEX_ORDER[i]);
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
        if (bunkerUnnecessary()) {
            Unit bunker = bunkerList.get(0).unit();
            ActionHelper.unitCommand(bunker, Abilities.UNLOAD_ALL_BUNKER, false); //rally is already set to top of inside main wall
            ActionHelper.unitCommand(bunker, Abilities.EFFECT_SALVAGE, false);
        }
    }

    //return true when bunker is no longer needed
    private static boolean bunkerUnnecessary() {
        if (Strategy.DO_LEAVE_UP_BUNKER && Bot.OBS.getFoodUsed() < 175) {
            return false;
        }

        //dump when first moving out
        if (doOffense) {
            return true;
        }

        //dump bunker if natural is a PF
        return GameCache.baseList.get(1).getCc() != null &&
                GameCache.baseList.get(1).getCc().unit().getType() == Units.TERRAN_PLANETARY_FORTRESS;
    }

    private static void raiseAndLowerDepots() {
        List<Unit> depots = UnitUtils.getMyUnitsOfType(UnitUtils.SUPPLY_DEPOT_TYPE);

        for (Unit depot : depots) {
            boolean isDepotLowered = depot.getType() == Units.TERRAN_SUPPLY_DEPOT_LOWERED;

            //WALL DEPOTS
            if (UnitUtils.isWallStructure(depot)) {
                //Raise
                if (isDepotLowered) {
                    if (InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depot.getPosition().toPoint2d())) {
                        ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
                    }
                }
                //Lower - if any enemy near depot or on the ramp
                else {
                    if (!InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depot.getPosition().toPoint2d()) &&
                            !InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, LocationConstants.myRampPos)) {
                        ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
                    }
                }
            }

            //REAPER WALL DEPOTS
            else if (LocationConstants.opponentRace == Race.TERRAN && UnitUtils.isReaperWallStructure(depot)) {
                boolean isReaperNearby = !UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_REAPER,
                        depot.getPosition().toPoint2d(), 12).isEmpty();
                //Raise
                if (isDepotLowered && isReaperNearby) {
                    ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
                }
                //Lower
                else if (!isDepotLowered && !isReaperNearby) {
                    ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
                }
            }

            //OTHER DEPOTS
            else if (depot.getType() == Units.TERRAN_SUPPLY_DEPOT) {
                ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
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
        float numVikings = 0;
        boolean hasMobileDetector = false;
        boolean hasTempests = false;
        for (UnitInPool enemy : GameCache.allEnemiesList) {
            switch ((Units)enemy.unit().getType()) {
                case TERRAN_RAVEN: case ZERG_OVERSEER: case PROTOSS_OBSERVER:
                    numVikings += 0.5;
                    hasMobileDetector = true;
                    break;
                case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG: case TERRAN_BANSHEE:
                case ZERG_MUTALISK: case ZERG_VIPER: case ZERG_BROODLORD_COCOON: case ZERG_BROODLORD:
                case PROTOSS_ORACLE:
                    numVikings += 0.5;
                    break;
                case TERRAN_VIKING_FIGHTER: case TERRAN_VIKING_ASSAULT: case ZERG_CORRUPTOR:
                    numVikings += 1.3;
                    break;
                case PROTOSS_PHOENIX:
                    numVikings += 2;
                    break;
                case PROTOSS_VOIDRAY:
                    numVikings += 1.5;
                    break;
                case TERRAN_BATTLECRUISER: case PROTOSS_CARRIER:
                    numVikings += 3.67;
                    break;
                case PROTOSS_TEMPEST:
                    hasTempests = true;
                    numVikings += 2;
                    break;
                case PROTOSS_MOTHERSHIP:
                    numVikings += 4;
                    break;
            }
        }
        if (hasTempests) { //minimum 10 vikings at all times if enemy has a tempest
            numVikings = Math.max(10, numVikings);
        }
        else if (hasMobileDetector && Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) && UnitUtils.numMyUnits(Units.TERRAN_BANSHEE, true) > 0) {
            numVikings = Math.max(3, numVikings); //minimum vikings if he has a detector
        }
        else if (Switches.enemyCanProduceAir) { //set minimum vikings if enemy can produce air
            numVikings = Math.max(2, numVikings);
        }

        if (UnitUtils.numMyUnits(Units.TERRAN_CYCLONE, false) > 0) {
            numVikings--;
        }
        numVikings = Math.max(numVikings, GameCache.bansheeList.size() * Strategy.VIKING_BANSHEE_RATIO); //at least 1 safety viking for every 5 banshees
        return (int)numVikings;
    }

    public static void giveHellionCommand(Unit hellion) {
        ArmyCommands lastCommand = getCurrentCommand(hellion);
        boolean isUnsafe = InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, hellion.getPosition().toPoint2d());
        boolean isInHellionRange = InfluenceMaps.getValue(InfluenceMaps.pointInHellionRange, hellion.getPosition().toPoint2d());
        boolean canAttack = UnitUtils.isWeaponAvailable(hellion) &&
                InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, hellion.getPosition().toPoint2d()) < 200;

        //always flee if locked on by cyclone
        if (hellion.getBuffs().contains(Buffs.LOCK_ON)) {
            retreatUnitFromCyclone(hellion);
        }
        //shoot when available
        else if (canAttack && isInHellionRange) {
            //attack
            if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(hellion);
        }
        else if (isUnsafe) {
            //retreat
            new BasicUnitMicro(hellion, retreatPos, MicroPriority.SURVIVAL).onStep();
        }
        else {
            if (isInHellionRange) {
                //retreat
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(hellion);
            }
            else {
                //attack
                if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(hellion);
            }
        }
    }

    public static void giveBansheeCommand(Unit banshee) {
        ArmyCommands lastCommand = getCurrentCommand(banshee);
        int x = InfluenceMaps.toMapCoord(banshee.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(banshee.getPosition().getY());
        boolean isUnsafe = (Switches.isDivingTempests) ?
                false :
                InfluenceMaps.pointThreatToAirValue[x][y] > 2 || InfluenceMaps.pointDamageToGroundValue[x][y] > banshee.getHealth().orElse(150f);
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke() &&
                UnitUtils.isRepairBaySafe();
        boolean isInDetectionRange = InfluenceMaps.pointDetected[x][y];
        boolean isInBansheeRange = InfluenceMaps.pointInBansheeRange[x][y];
        boolean canAttack = UnitUtils.isWeaponAvailable(banshee) && InfluenceMaps.pointThreatToAirValue[x][y] < 200;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0f) > Strategy.ENERGY_BEFORE_CLOAKING &&
                Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK);
        boolean isParasitic = banshee.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways
        boolean hasDecloakBuff = UnitUtils.hasDecloakBuff(banshee);
        boolean isAnyBaseUnderAttack = doOffense ?
                (attackUnit != null && UnitUtils.isInMyMainOrNat(attackUnit)) :
                UnitUtils.isAnyBaseUnderAttack();
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
                kiteBackAirUnit(banshee, lastCommand);
            }
            else if (cloakState != CloakState.NOT_CLOAKED &&
                    banshee.getEnergy().get() > 3 + ((UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() > 2) ? 2 : 0)) { //additional energy for time to flee tempest range
                if (isInBansheeRange) { //maintain max range
                    //retreat
                    new BasicUnitMicro(banshee, retreatPos, MicroPriority.SURVIVAL).onStep();
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
            }
        }
        //staying in repair bay if not full health
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
            return retreatPos;
        }
        Point2d enemyPos = closestEnemy.getPosition().toPoint2d();
        Point2d kiteBackPos = Position.towards(myUnit.getPosition().toPoint2d(), enemyPos, -3.5f);
        if (Position.isOutOfBounds(kiteBackPos)) { //ignore when kited to the edge of the map
            return null;
        }
        if (!myUnit.getFlying().orElse(true) && !Bot.OBS.isPathable(kiteBackPos)) { //ignore unpathable positions
            return null;
        }
        return kiteBackPos;
    }

    private static void retreatUnitFromCyclone(Unit myUnit) {
        Point2d cyclonePos = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_CYCLONE).stream()
                .map(u -> u.unit().getPosition().toPoint2d())
                .filter(cyclone -> UnitUtils.getDistance(myUnit, cyclone) <= 16.5)
                .min(Comparator.comparing(cyclone -> UnitUtils.getDistance(myUnit, cyclone)))
                .orElse(null);

        //if no cyclone visible in range
        if (cyclonePos == null) {
            armyGoingHome.add(myUnit);
        }
        else {
            //retreat command away from nearest cyclone position
            ActionHelper.unitCommand(myUnit, Abilities.MOVE, Position.towards(myUnit.getPosition().toPoint2d(), cyclonePos, -3f), false);
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
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke() &&
                UnitUtils.isRepairBaySafe();
        boolean stayBack = false;
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : Strategy.RETREAT_HEALTH;


        //keep vikings back vs tempests or vikings until ready to engage
        if (doStayBackFromTempests() ||
                (isOutnumberedInVikings() && Bot.OBS.getFoodUsed() <= 198)) {
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
                kiteBackAirUnit(viking, lastCommand);
            }
        }
        //Under 100% health and at repair bay
        else if (canRepair &&
                UnitUtils.getHealthPercentage(viking) < 100 &&
                UnitUtils.getDistance(viking, retreatPos) < 3) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
        }
        //in range then back up
        else if (isInVikingRange) {
            //if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
            kiteBackAirUnit(viking, lastCommand);
        }
        //go home if low health
        else if (canRepair && UnitUtils.getHealthPercentage(viking) < healthToRepair) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(viking);
        }
        //out of range, then move in
        else {
            if (lastCommand != ArmyCommands.ATTACK) armyAirAttacking.add(viking);
        }
    }

    private static void kiteBackAirUnit(Unit myAirUnit, ArmyCommands lastCommand) {
        //try going home direction to save apm
        Point2d retreatingPos = Position.towards(myAirUnit.getPosition().toPoint2d(), retreatPos, 2);
        if (!InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, retreatingPos)) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(myAirUnit);
            return;
        }

        //try kiting straight back from nearest enemy threat
        Point2d kiteBackPos = getKiteBackPos(myAirUnit);
        if (kiteBackPos != null && !InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, kiteBackPos)) {
            ActionHelper.unitCommand(myAirUnit, Abilities.MOVE, kiteBackPos, false);
            return;
        }

        //otherwise pathfind to safety
        new BasicUnitMicro(myAirUnit, retreatPos, MicroPriority.SURVIVAL).onStep();
    }

    //is outnumbered if enemy has 20% more vikings in total, than I have in nearby vikings
    private static boolean isOutnumberedInVikings() {
        if (LocationConstants.opponentRace != Race.TERRAN) {
            return false;
        }

        int numVikingsOnFrontLine = (int)GameCache.vikingList.stream()
                .filter(viking -> UnitUtils.getDistance(viking, attackAirPos) < 20 &&
                        UnitUtils.getHealthPercentage(viking) > Strategy.RETREAT_HEALTH)
                .count();

        int enemyVikings = (int)UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).stream()
                .filter(enemyViking -> enemyViking.getLastSeenGameLoop() + Time.toFrames(5) > Time.nowFrames())
                .count();
        return numVikingsOnFrontLine * 1.2f < enemyVikings;
    }

    private static boolean doStayBackFromTempests() {
        return !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty() && //TODO: change below to use Gamestate.allVisibleEnemiesMap
                Bot.OBS.getUnits(Alliance.ENEMY,
                        e -> (e.unit().getType() == Units.PROTOSS_PHOENIX || e.unit().getType() == Units.PROTOSS_VOIDRAY || e.unit().getType() == Units.PROTOSS_INTERCEPTOR)
                        && !e.unit().getHallucination().orElse(false)).isEmpty();
    }

    //return true if autoturret cast
    private static void giveRavenCommand(Unit raven, boolean doCastTurrets) {

        //wait for raven to auto-turret before giving a new command, if auto-turret pos is still in range of enemies
        if (UnitUtils.getOrder(raven) == Abilities.EFFECT_AUTO_TURRET &&
                !UnitUtils.getEnemyTargetsNear(ActionIssued.getCurOrder(raven).get().targetPos, 7).isEmpty()) {
            return;
        }

        ArmyCommands lastCommand = getCurrentCommand(raven);
        boolean[][] threatMap = (raven.getEnergy().orElse(0f) >= (Strategy.DO_MATRIX ? 75 : Strategy.AUTOTURRET_AT_ENERGY))
                ? InfluenceMaps.pointThreatToAir
                : (Strategy.MASS_RAVENS ? InfluenceMaps.pointVikingsStayBack : InfluenceMaps.pointThreatToAirPlusBuffer);
        boolean isUnsafe = InfluenceMaps.getValue(threatMap, raven.getPosition().toPoint2d());

        //if maxed on mass raven, let them turret more aggressively
//        if (GameCache.ravenList.size() > 15 &&
//                (Bot.OBS.getFoodUsed() > 190 || GameCache.gasBank > 3000) &&
//                //raven.getEnergy().orElse(0f) > 195) {
//                raven.getEnergy().orElse(0f) > Strategy.AUTOTURRET_AT_ENERGY) {
//            isUnsafe = false; //FIXME: testing aggressive autoturretting to prevent tie games
//            Chat.tag("AGGRESSIVE_RAVENS");
//        }
        Optional<UnitInPool> turretTarget = getRavenTurretTarget(raven);
        //boolean inRange = InfluenceMaps.getValue(InfluenceMaps.pointInRavenCastRange, raven.getPosition().toPoint2d());
        //boolean inRange = attackUnit != null && UnitUtils.getDistance(attackUnit, raven) < Strategy.RAVEN_CAST_RANGE;
        boolean inRange = turretTarget.isPresent();
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke() &&
                UnitUtils.isRepairBaySafe();
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
        else if (canRepair && UnitUtils.getHealthPercentage(raven) < 100 && UnitUtils.getDistance(raven, retreatPos) < 3) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
        }

        //go home to repair if low TODO: kite first
        else if (canRepair && UnitUtils.getHealthPercentage(raven) < healthToRepair) {
//            if (!doCastTurrets) || !doAutoTurretOnRetreat(raven)) {
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
//            }
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
                    if (!doCastTurrets || !doAutoTurret(raven, turretTarget)) {
//                        if (isUnsafe) {
                            kiteBackAirUnit(raven, lastCommand);
//                        }
                    }
                }
            }
        }
        //go forward if not in range
        else if (lastCommand != ArmyCommands.ATTACK) {
            if (attackCloakedPos != null) {
                armyDetectorAttacking.add(raven);
            }
            else if (raven.getEnergy().orElse(0f) > 175) {
                armyGroundAttacking.add(raven);
            }
            else {
                armyAirAttacking.add(raven);
            }
        }
    }

    private static Optional<UnitInPool> getRavenTurretTarget(Unit raven) {
        List<UnitInPool> targets = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                !enemy.unit().getType().toString().contains("CHANGELING") &&
                isTargetImportant(enemy.unit().getType()) &&
                UnitUtils.getDistance(raven, enemy.unit()) < 11); //radius 1, cast range 2, attack range 7, 1 enemy radius/buffer
        if (targets.isEmpty() && raven.getEnergy().orElse(0f) > 175) {
            targets = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                    !enemy.unit().getType().toString().contains("CHANGELING") &&
                    UnitUtils.getDistance(raven, enemy.unit()) < 10); //radius 1, cast range 2, attack range 7
        }
        return targets.stream()
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(raven, enemy.unit())));
    }

//    //drop auto-turrets near enemy before going home to repair
//    private static boolean doAutoTurretOnRetreat(Unit raven) {
//        if (raven.getEnergy().orElse(0f) >= 50 &&
//                !raven.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE) &&
//                UnitUtils.getDistance(raven, attackGroundPos) < 12 &&
//                attackUnit != null) {
//            return castAutoTurret(raven, 0);
//        }
//        return false;
//    }

    //drop auto-turrets near enemy
    private static boolean doAutoTurret(Unit raven, Optional<UnitInPool> turretTarget) {
        if (turretTarget.isEmpty()) {
            return false;
        }
        if (!UnitUtils.isEnemyRetreating(turretTarget.get().unit(), raven.getPosition().toPoint2d()) &&
                raven.getEnergy().orElse(0f) >= Strategy.AUTOTURRET_AT_ENERGY &&
                !raven.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE)) {
            return castAutoTurret(raven, turretTarget.get().unit());
        }
        return false;
    }

    private static boolean isAttackUnitImportant() {
        return attackUnit != null && isTargetImportant(attackUnit.getType());
    }

    private static boolean isTargetImportant(UnitType targetType) {
        return !UnitUtils.isStructure(targetType) || UnitUtils.canAttack(targetType);
    }

    private static boolean castAutoTurret(Unit raven, Unit enemyTarget) {
        //see how close we can safely cast from
        Point2d ravenPos = raven.getPosition().toPoint2d();
        Point2d enemyTargetPos = enemyTarget.getPosition().toPoint2d();
        float distanceToTarget = UnitUtils.getDistance(raven, enemyTargetPos);
        Point2d turretPos = ravenPos;
        if (!raven.getBuffs().contains(Buffs.FUNGAL_GROWTH)) { //cast at raven if fungaled
            for (int i = 1; i < distanceToTarget - 1; i++) {
                Point2d testPos = Position.towards(ravenPos, enemyTargetPos, i);
                if (testSafetyOfPos(testPos, enemyTarget)) {
                    break;
                }
                turretPos = testPos;
            }
        }
        turretPos = Position.toWholePoint(turretPos);

        //get list of nearby placeable positions
        List<Point2d> posList = Position.getSpiralList(turretPos, 3).stream()
                .filter(p -> p.distance(enemyTargetPos) < 7)
                .filter(p -> Bot.OBS.isPlacable(p))
                .sorted(Comparator.comparing(p -> p.distance(enemyTargetPos)))
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
            Point2d placementPos = posList.get(placementList.indexOf(true));
            ActionHelper.unitCommand(raven, Abilities.EFFECT_AUTO_TURRET, placementPos, false);
            turretsCast++;
            return true;
        }
//        else if (attackUnit != null && !UnitUtils.canMove(attackUnit) && towardsEnemyDistance < 4) {
//            castAutoTurret(raven, 4);
//        }
        return false;
    }

    //hacky way to deal with stuck ravens (for 4sec every 3 minutes, the ravens will power in past 14 threat to attack structures/thors
    private static boolean testSafetyOfPos(Point2d testPos, Unit enemyTarget) {
        int threatThreshold = 0;
        //with mass raven... periodically let them go deep into threat range to lay turrets
        if ((UnitUtils.isStructure(enemyTarget.getType()) || UnitUtils.THOR_TYPE.contains(enemyTarget.getType())) &&
                UnitUtils.numMyUnits(Units.TERRAN_RAVEN, false) >= 10 &&
                Time.periodic(3, 96)) {
            threatThreshold = 14;
        }
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToAirValue, testPos) <= threatThreshold;
    }

    private static boolean castSeeker(Unit raven) {
        //cast seeker only once every 4sec
        if (Time.nowFrames() < prevSeekerFrame + 96) {
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

        //calculate point from detector TODO: use Position.towards?? and modernize all of this?
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
        if (numVikingsNearby < Math.min(Strategy.MAX_VIKINGS_TO_DIVE_TEMPESTS, (int)(ArmyManager.calcNumVikingsNeeded() * 0.75))) {
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
        List<Unit> bio = UnitUtils.getMyUnitsOfType(Units.TERRAN_MARINE);
        bio.addAll(UnitUtils.getMyUnitsOfType(Units.TERRAN_MARAUDER));
        if (!bio.isEmpty()) {
            ActionHelper.unitCommand(bio, Abilities.ATTACK, expansionPos, true);
        }
    }

    //TODO: exclude ground units around reaper jump areas
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

    public static Unit getEnemyInMainOrNatural(boolean doExcludeBunkerRange) {
        boolean[][] pointInNat = doExcludeBunkerRange ? InfluenceMaps.pointInNatExcludingBunkerRange : InfluenceMaps.pointInNat;
        return Bot.OBS.getUnits(Alliance.ENEMY).stream()
                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()) ||
                        InfluenceMaps.getValue(pointInNat, enemy.unit().getPosition().toPoint2d())))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(0).getCcPos())))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    public static Unit getEnemyInMain() {
        return Bot.OBS.getUnits(Alliance.ENEMY).stream()
                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(0).getCcPos()) +
                        (!UnitUtils.canAttack(enemy.unit().getType()) ? 1000 : 0)))
                .map(UnitInPool::unit)
                .orElse(null);
    }
}
