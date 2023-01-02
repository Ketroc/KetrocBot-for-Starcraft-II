package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameResult;
import com.ketroc.Switches;
import com.ketroc.bots.Bot;
import com.ketroc.gamestate.GameCache;
import com.ketroc.geometry.Position;
import com.ketroc.micro.Target;
import com.ketroc.micro.*;
import com.ketroc.models.*;
import com.ketroc.strategies.BunkerContain;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.MarineAllIn;
import com.ketroc.strategies.Strategy;
import com.ketroc.strategies.defenses.ProxyBunkerDefense;
import com.ketroc.utils.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ArmyManager {
    public static boolean doOffense;
    public static Point2d retreatPos;

    public static Point2d attackGroundPos;
    public static Point2d attackAirPos;
    public static Point2d attackEitherPos;
    public static Point2d attackCloakedPos;
    public static UnitInPool leadTank;

    public static Unit attackUnit;
    public static Unit attackAirUnit;
    public static Unit attackGroundUnit;
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

    public static final int[] BASE_DEFENSE_INDEX_ORDER = {2, 4, 1, 3, 5, 6, 7, 8, 9, 10, 11};

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
        sendGroundKillSquad();
        setIsAttackUnitRetreating();
        setAirOrGroundTarget();
        setCloakedTarget();

        // lift & lower depot walls
        raiseAndLowerDepots();

        //respond to nydus
        nydusResponse();

        scanCloakedUnits();

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

        //FIXME: just for testing below
        if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) { //morph unused hellions back into hellbats
            UnitUtils.myUnitsOfType(Units.TERRAN_HELLION).forEach(hellion -> {
                ActionHelper.unitCommand(hellion, Abilities.MORPH_HELLBAT, false);
            });
        }

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_CYCLONE).forEach(cyclone -> {
            UnitMicroList.add(new Cyclone(cyclone, PosConstants.insideMainWall));
        });

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_THOR).forEach(thor -> {
            UnitMicroList.add(new Thor(thor));
        });

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_GHOST).forEach(ghost -> {
            UnitMicroList.add(new GhostBasic(ghost, PosConstants.insideMainWall));
        });
        if (UnitUtils.isNukeAvailable() && UnitMicroList.numOfUnitClass(GhostNuke.class) == 0) {
            GhostNuke.addGhost();
        }

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_MARAUDER).forEach(marauder -> {
            UnitMicroList.add(new Marauder(marauder, PosConstants.insideMainWall));
        });

        //FIXME: just for testing below
        if (Strategy.gamePlan == GamePlan.HELLBAT_ALL_IN) {
            UnitUtils.myUnitsOfType(Units.TERRAN_HELLION_TANK).forEach(hellbat -> {
                UnitMicroList.add(new Hellbat_FastTravel(hellbat));
            });
            UnitUtils.myUnitsOfType(Units.TERRAN_HELLION).forEach(hellion -> {
                UnitMicroList.add(new Hellbat_FastTravel(hellion));
            });
        }
        else {
            UnitUtils.myUnitsOfType(Units.TERRAN_HELLION_TANK).forEach(hellbat -> {
                UnitMicroList.add(new Hellbat(hellbat));
            });
        }

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_WIDOWMINE).forEach(mine -> {
            UnitMicroList.add(new WidowMine(mine, PosConstants.insideMainWall));
        });

        //FIXME: just for testing below
        UnitUtils.myUnitsOfType(Units.TERRAN_MEDIVAC).forEach(medivac -> {
            UnitMicroList.add(new MedivacScvHealer(medivac, PosConstants.REPAIR_BAY));
        });

        //FIXME: just for testing below
        if (Strategy.gamePlan == GamePlan.BC_RUSH) {
            UnitUtils.myUnitsOfType(Units.TERRAN_BATTLECRUISER).forEach(bc -> {
                UnitMicroList.add(new BattlecruiserHarass(bc));
            });
            //muleBCs();
        }

        //repair station
        manageRepairBay();

        //maintain repair scvs on offense with tanks
        manageTankRepairScvs();

        //if searching for last structures
        if (attackGroundPos == null && PosConstants.nextEnemyBase == null) {
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
                ActionHelper.unitCommand(armyGroundAttacking, Abilities.ATTACK, attackGroundPos, false);
            }
            if (!armyAirAttacking.isEmpty()) {
                ActionHelper.unitCommand(armyAirAttacking, Abilities.ATTACK, attackAirPos, false);
            }
            if (!armyDetectorAttacking.isEmpty()) {
                Point2d targetPos = attackCloakedPos != null ? attackCloakedPos : attackGroundPos;
                ActionHelper.unitCommand(armyDetectorAttacking, Abilities.ATTACK, targetPos, false);
            }

            pfBunkerTargetting();
            libTargetting();
            autoturretTargetting();
        }

        //send out marine+hellbat army
        sendMarinesHellbats();
    }

    private static void muleBCs() { //TODO
        callDownNewMules();
        giveMulesRepairTarget();
    }

    private static void callDownNewMules() {
        List<Unit> ocList = UnitUtils.myUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
        if (ocList.stream().anyMatch(oc -> oc.getEnergy().orElse(0f) >= 50)) {
            UnitMicroList.getUnitSubList(Battlecruiser.class).stream()
                    .map(bc -> bc.unit.unit())
                    .filter(bc -> bc.getHealth().get() < bc.getHealthMax().get() - 100)
                    .filter(bc -> !InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundPlusBuffer, bc.getPosition().toPoint2d()))
                    .findAny()
                    .ifPresent(bc -> ActionHelper.unitCommand(ocList, Abilities.EFFECT_CALL_DOWN_MULE, bc.getPosition().toPoint2d(), false));
        }
    }

    private static void giveMulesRepairTarget() {
        UnitUtils.myUnitsOfType(Units.TERRAN_MULE).stream()
                .filter(mule -> ActionIssued.getCurOrder(mule).isEmpty())
                .forEach(mule ->
                        UnitMicroList.getUnitSubList(Battlecruiser.class).stream()
                                .map(bc -> bc.unit.unit())
                                .filter(bc -> bc.getHealth().get() < bc.getHealthMax().get())
                                .min(Comparator.comparing(bc -> UnitUtils.getDistance(mule, bc)))
                                .ifPresent(bc -> ActionHelper.unitCommand(mule, Abilities.EFFECT_REPAIR, bc, false))
                );
    }

    //TODO: create influence maps for my units (need total damage vs cloaked unit)
    //TODO: smarter when other units around
    //TODO: include burrowed
    //for now, just a hack to scan obs with 11 marines nearby (marine 6dmg/5range, obs 60hp)
    private static void scanCloakedUnits() {
        if (!UnitUtils.canScan()) {
            return;
        }
        List<UnitInPool> obsList = UnitUtils.getEnemyUnitsOfType(UnitUtils.OBSERVER_TYPE);
        for (UnitInPool obs : obsList) {
            if (obs.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED) {
                continue;
            }
            int numNearbyMarines = Bot.OBS.getUnits(Alliance.SELF, u ->
                    u.unit().getType() == Units.TERRAN_MARINE &&
                            u.unit().getWeaponCooldown().orElse(1f) <= 0 &&
                            UnitUtils.getDistance(u.unit(), obs.unit()) < 6
            ).size();
            if (numNearbyMarines >= 11) {
                UnitUtils.scan(obs.unit().getPosition().toPoint2d());
                return;
            }
        }
    }

    private static void setLeadTank() {
        Point2d leadPos = attackGroundPos != null ? attackGroundPos : PosConstants.enemyMainBaseMidPos;
        leadTank = UnitMicroList.getUnitSubList(TankOffense.class)
                .stream()
                .min(Comparator.comparing(tank -> UnitUtils.getDistance(tank.unit.unit(), leadPos)))
                .map(tankOffense -> tankOffense.unit)
                .orElse(null);
    }

    //new marines get a marine object TODO: move
    private static void addMarines() {
        if (Strategy.gamePlan == GamePlan.MARINE_RUSH) {
            UnitUtils.myUnitsOfType(Units.TERRAN_MARINE).forEach(unit -> {
                UnitMicroList.add(new MarineOffense(unit, PosConstants.insideMainWall));
            });
        }
        else {
            UnitUtils.myUnitsOfType(Units.TERRAN_MARINE).forEach(unit -> {
                UnitMicroList.add(new MarineBasic(unit, PosConstants.insideMainWall));
            });
        }
    }

    //add scvs when tanks move out, or when tanks need repair on defense
    private static void manageTankRepairScvs() {
        if (!Strategy.DO_OFFENSIVE_TANKS) {
            return;
        }
        List<TankOffense> tankList = UnitMicroList.getUnitSubList(TankOffense.class);
        if ((doOffense && !tankList.isEmpty()) ||
                tankList.stream().anyMatch(tankOffense -> tankOffense.unit.isAlive() &&
                        UnitUtils.getHealthPercentage(tankOffense.unit.unit()) < 99)) {
            int numScvsToAdd = Strategy.NUM_OFFENSE_SCVS - UnitMicroList.numOfUnitClass(ScvTankSupporter.class);
            for (int i=0; i<numScvsToAdd; i++) {
                UnitInPool closestAvailableScv = WorkerManager.getScvEmptyHands(
                        tankList.get(0).unit.unit().getPosition().toPoint2d());
                if (closestAvailableScv == null) {
                    return;
                }
                UnitMicroList.add(new ScvTankSupporter(closestAvailableScv));
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
            for (DefenseUnitPositions libPos : base.getLiberatorPositions()) {
                if (libPos.getUnit() != null && libPos.getUnit().isAlive()) {
                    Unit lib = libPos.getUnit().unit();
                    if (lib.getType() == Units.TERRAN_LIBERATOR_AG && UnitUtils.isWeaponAvailable(lib)) {
                        Point2d libZone = Position.towards(lib, base.getCcPos(), 5);
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
                : PosConstants.baseLocations.get(0);
        List<Unit> groundAttackers = UnitUtils.toUnitList(
                Bot.OBS.getUnits(Alliance.SELF, u -> UnitUtils.GROUND_ARMY_ATTACKERS_TYPE.contains(u.unit().getType()))
        );
        groundAttackersMidPoint = (!groundAttackers.isEmpty())
                ? Position.midPointUnitsMedian(groundAttackers)
                : PosConstants.baseLocations.get(0);
    }

    private static void searchForLastStructures() {
        spreadArmy(GameCache.bansheeList);
        spreadArmy(GameCache.vikingList);
        spreadArmy(UnitUtils.myUnitsOfType(UnitUtils.HELLION_TYPE));
    }

    private static void sendMarinesHellbats() {
        if (UnitUtils.isOutOfGas()) {
            List<Unit> army = UnitUtils.myUnitsOfType(Units.TERRAN_MARINE);
            army.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_HELLION_TANK));
            if (Bot.OBS.getFoodUsed() >= 198 || Cost.isMineralBroke(50)) {
                if (army.stream().anyMatch(unit -> ActionIssued.getCurOrder(unit).isPresent())) {
                    ActionHelper.unitCommand(army, Abilities.ATTACK, attackGroundPos, false);
                }
            }
        }
    }

    //shoot best target, otherwise changelings, otherwise let autoattack happen
    private static void autoturretTargetting() {
        UnitUtils.myUnitsOfType(Units.TERRAN_AUTO_TURRET).stream()
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
        for (Unit hellion : UnitUtils.myUnitsOfType(Units.TERRAN_HELLION)) {
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

        //give normal vikings their commands
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
                            UnitUtils.scan(Position.towards(Switches.vikingDiveTarget.unit(), ArmyManager.retreatPos, -5));
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
        if (Strategy.gamePlan == GamePlan.BC_RUSH) {
            doOffense = Bot.OBS.getFoodUsed() > 170 || Base.numEnemyBases() == 0;
            return;
        }

        if (Strategy.gamePlan == GamePlan.HELLBAT_ALL_IN) {
            int numHellions = UnitUtils.numMyUnits(UnitUtils.HELLION_TYPE, false);
            if (!doOffense &&
                    UnitUtils.myUnitsOfType(Units.TERRAN_ARMORY).stream().anyMatch(armory -> armory.getBuildProgress() > 0.95) &&
                    numHellions >= 8) {
                doOffense = true;
            }
            if (doOffense && numHellions <= 4) {
                doOffense = false;
            }
            return;
        }

        if (Strategy.gamePlan == GamePlan.GHOST_HELLBAT) {
            int numHellbats = UnitUtils.numMyUnits(Units.TERRAN_HELLION_TANK, false);
            if (doOffense && Bot.OBS.getFoodUsed() < 190 && numHellbats < 6) {
                doOffense = false;
            }
            else if (!doOffense && (Bot.OBS.getFoodUsed() > 190 || numHellbats >= 6)) {
                doOffense = true;
            }
            return;
        }

        if (Strategy.gamePlan == GamePlan.MECH_ALL_IN) {
            int numTanks = UnitUtils.numMyUnits(UnitUtils.SIEGE_TANK_TYPE, false);
            int numTanksSieged = UnitUtils.numMyUnits(Units.TERRAN_SIEGE_TANK_SIEGED, false);

            //retreat home when all unsieged and numTanks < 1
            if (doOffense && Bot.OBS.getFoodUsed() < 190 && numTanks < 1 && numTanksSieged == 0) {
                doOffense = false;
            }
            //go on offense with 3 siege tanks
            else if (!doOffense && (Bot.OBS.getFoodUsed() > 190 || numTanks >= 3)) {
                doOffense = true;
            }
            return;
        }

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

        if (Strategy.MASS_MINE_OPENER && WidowMine.hasPermaCloak() && UnitUtils.hasUpgrade(Upgrades.DRILL_CLAWS)) {
            Chat.chatNeverRepeatInvisToHuman("Go go drilly bois!");
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
        //move out with 5 core attack units (with at least 1 air unit)
        if (Strategy.DO_USE_CYCLONES) {
            doOffense = UnitMicroList.numOfUnitClass(Cyclone.class) + UnitMicroList.numOfUnitClass(TankOffense.class) +
                            GameCache.bansheeList.size() + GameCache.ravenList.size() > 5 &&
                    (!GameCache.bansheeList.isEmpty() || !GameCache.vikingList.isEmpty() || !GameCache.ravenList.isEmpty());
            return;
        }

        if (Strategy.MASS_RAVENS) {
            boolean isAnyRavenNearFullEnergy = UnitUtils.myUnitsOfType(Units.TERRAN_RAVEN).stream().anyMatch(raven -> raven.getEnergy().orElse(0f) >= 180f);
            numAutoturretsAvailable = GameCache.ravenList.stream()
                    .mapToInt(raven -> raven.getEnergy().orElse(0f).intValue() / 50)
                    .sum();
            if (!doOffense && numAutoturretsAvailable > 20 && isAnyRavenNearFullEnergy) {
                doOffense = true;
            }
            else if (doOffense && numAutoturretsAvailable < 8) {
                doOffense = false;
            }
            return;
        }
        doOffense = GameCache.bansheeList.size() +
                (UnitMicroList.numOfUnitClass(TankOffense.class) * 0.75) +
                (GameCache.ravenList.size() * 0.34) >= 6 &&
                !isOutnumberedInVikings();
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
        Predicate<UnitInPool> enemyTargetFilter = u -> GameCache.baseList.stream()
                .filter(base -> base.isMyBase() || base.isNatBaseAndHasBunker())
                .anyMatch(base -> UnitUtils.getDistance(u.unit(), base.getCcPos()) < 25) && //close to any of my bases
                !UnitUtils.NO_THREAT_ENEMY_AIR.contains(u.unit().getType()) &&
                u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED && //ignore cloaked units
                u.unit().getDisplayType() != DisplayType.HIDDEN && //ignore undetected cloaked/burrowed units
                !UnitUtils.IGNORED_TARGETS.contains(u.unit().getType()) && //ignore eggs/autoturrets/etc
                u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                u.unit().getType() != Units.ZERG_BROODLING && //ignore broodlings
                !u.unit().getHallucination().orElse(false);
        attackAirUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(enemyTargetFilter)
                .filter(u -> u.unit().getFlying().orElse(true))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), PosConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), groundAttackersMidPoint)))
                .map(UnitInPool::unit)
                .orElse(null);
        attackGroundUnit = GameCache.allVisibleEnemiesList.stream()
                .filter(enemyTargetFilter) //ignore hallucs
                .filter(u -> !u.unit().getFlying().orElse(false))
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), PosConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), groundAttackersMidPoint)))
                .map(UnitInPool::unit)
                .orElse(null);

        attackGroundPos = (attackGroundUnit != null) ? attackGroundUnit.getPosition().toPoint2d() : getArmyRallyPoint();
        attackAirPos = (attackAirUnit != null) ? attackAirUnit.getPosition().toPoint2d() : attackGroundPos;
    }

    private static Point2d getArmyRallyPoint() {
        if (Strategy.gamePlan == GamePlan.MARINE_RUSH && MarineAllIn.isInitialBuildUp) {
            return Position.towards(
                    GameCache.baseList.get(0).getCcPos(),
                    GameCache.baseList.get(GameCache.baseList.size()-1).getCcPos(),
                    4
            );
        }

        //protect forward base
        Base forwardBase = getForwardBase();
        if (forwardBase.isMyBase()) {
            return forwardBase.inFrontPos();
        }
        //protect nat if OC or if no base but bunker is up there
        if (!UnitUtils.isWallUnderAttack() &&
                ((Strategy.DO_WALL_NAT && GameCache.baseList.get(1).isMyBase()) ||
                UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_BUNKER, PosConstants.BUNKER_NATURAL, 5))) {
            return UnitUtils.getBehindBunkerPos();
        }
        //protect main ramp
        return PosConstants.insideMainWall;
    }

    private static Base getForwardBase() {
        return GameCache.baseList.stream()
                .filter(base -> !base.isPocketBase())
                .collect(Collectors.toList())
                .get(2);
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
                    UnitMicroList.add(new ScvRepairBayMain(scv));
                });
            }
        }
    }

    //get number of injured army units in dock
    public static int getNumRepairBayUnits() {
        return Bot.OBS.getUnits(Alliance.SELF, u ->
                (u.unit().getType() == Units.TERRAN_VIKING_FIGHTER ||
                u.unit().getType() == Units.TERRAN_BANSHEE ||
                u.unit().getType() == Units.TERRAN_MEDIVAC ||
                u.unit().getType() == Units.TERRAN_BATTLECRUISER ||
                u.unit().getType() == Units.TERRAN_RAVEN) &&
                UnitUtils.getHealthPercentage(u.unit()) < 100 &&
                UnitUtils.getDistance(u.unit(), GameCache.baseList.get(0).getResourceMidPoint()) < 2.5)
        .size();
    }

    private static int getNumRepairingScvs() {
        return (int)UnitMicroList.unitMicroList.stream()
                .filter(basicUnitMicro -> basicUnitMicro instanceof ScvRepairBayMain)
                .count();

    }

    private static List<UnitInPool> getRepairBayScvs(int numScvsToSend) {
        List<UnitInPool> repairScvs = new ArrayList<>();

        for (int i=0; i<numScvsToSend; i++) {
            UnitInPool scv = WorkerManager.getScvEmptyHands(PosConstants.REPAIR_BAY);
            if (scv == null) {
                return repairScvs;
            }
            repairScvs.add(scv);
        }
        return repairScvs;
    }

    private static void setGroundTarget() {
        UnitInPool closestEnemyGround = getClosestEnemyGroundUnit();
        if (closestEnemyGround != null) {
            attackGroundPos = closestEnemyGround.unit().getPosition().toPoint2d();
            attackUnit = closestEnemyGround.unit();
        }
        else if (PosConstants.nextEnemyBase == null) {
            attackGroundPos = null; //flag to spread army
        }
        else {
            attackGroundPos = PosConstants.nextEnemyBase.getResourceMidPoint();
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
        if (Strategy.DO_OFFENSIVE_TANKS && leadTank != null) {
            attackAirPos = Position.towards(
                    leadTank.unit(),
                    attackGroundPos != null ? attackGroundPos : PosConstants.enemyMainBaseMidPos,
                    leadTank.unit().getType() == Units.TERRAN_SIEGE_TANK ? 8f : 4.5f
            );
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
        else if (PosConstants.mainBaseMidPos.distance(attackGroundPos) <
                PosConstants.mainBaseMidPos.distance(attackAirPos)) {
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

    //snipe unprotected enemy air units that are wandering away from protection
    private static void sendAirKillSquad() {
        if (GameCache.vikingList.isEmpty()) {
            return;
        }

        List<UnitInPool> enemyAirTargets = AirUnitKillSquad.getAvailableEnemyAirTargets();
        enemyAirTargets.stream()
                .filter(enemyAirUip -> AirUnitKillSquad.canAdd(enemyAirUip))
                .min(Comparator.comparing(enemyAirUip ->
                        UnitUtils.getDistance(enemyAirUip.unit(), PosConstants.baseLocations.get(0))))
                .ifPresent(enemyAirUip -> AirUnitKillSquad.add(enemyAirUip));
    }

    //peel off and snipe solo ground units
    private static void sendGroundKillSquad() {
        if (Bot.OBS.getFoodArmy() < 4) { //so that enemy workers aren't ignored at start of game
            return;
        }
        GameCache.allVisibleEnemiesList.stream()
                .filter(enemy -> GroundUnitKillSquad.isValidEnemyType((Units)enemy.unit().getType()) &&
                        !enemy.unit().getHallucination().orElse(false) &&
                        !Ignored.contains(enemy.getTag()) &&
                        UnitUtils.isEnemyUnitSolo(enemy.unit()))
                .min(Comparator.comparing(enemyAirUip ->
                        UnitUtils.getDistance(enemyAirUip.unit(), PosConstants.baseLocations.get(0))))
                .ifPresent(enemy -> GroundUnitKillSquad.add(enemy));
    }

    private static UnitInPool getClosestEnemyGroundUnit() {
        UnitInPool closestEnemyGroundUnit = GameCache.allEnemiesList.stream()
                .filter(u -> u.getLastSeenGameLoop() + 24 >= Time.nowFrames() &&
                        !u.unit().getFlying().orElse(false) && //ground unit
                        (!GameCache.ravenList.isEmpty() || u.unit().getDisplayType() != DisplayType.HIDDEN) && //TODO: handle with scan?
                        (!UnitUtils.IGNORED_TARGETS.contains(u.unit().getType()) ||
                                u.unit().getType() == Units.PROTOSS_ADEPT_PHASE_SHIFT && AdeptShadeTracker.shouldTargetShade(u)) &&
                        u.unit().getType() != Units.ZERG_CHANGELING_MARINE && //ignore changelings
                        !UnitUtils.CREEP_TUMOR_TYPES.contains(u.unit().getType()) && //FIXME: creep tumors turned off for probots
                        !u.unit().getHallucination().orElse(false)) //ignore hallucs
                .min(Comparator.comparing(u ->
                        UnitUtils.getDistance(u.unit(), PosConstants.baseLocations.get(0)) +
                        UnitUtils.getDistance(u.unit(), groundAttackersMidPoint) +
                        ((attackGroundPos != null && UnitUtils.getDistance(u.unit(), attackGroundPos) < 5) ? -3 : 0))) //preference to maintaining similar target preventing wiggling
                .orElse(null);
        return closestEnemyGroundUnit;
    }

    private static UnitInPool getClosestEnemyAirUnit() {
        return GameCache.allEnemiesList.stream()
                .filter(u -> u.getLastSeenGameLoop() + 24 >= Time.nowFrames() &&
                        (!Ignored.contains(u.getTag()) || AirUnitKillSquad.containsWithNoVikings(u.getTag())) &&
                        UnitUtils.isAir(u.unit()) &&
                        (!GameCache.ravenList.isEmpty() || u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED) && //ignore cloaked units with no raven TODO: handle banshees DTs etc with scan
                        u.unit().getType() != Units.ZERG_PARASITIC_BOMB_DUMMY &&
                        u.unit().getType() != Units.ZERG_OVERLORD && //FIXME: overlords turned off for probots
                        !u.unit().getHallucination().orElse(false)) //ignore hallucs
                .min(Comparator.comparing(u -> UnitUtils.getDistance(u.unit(), PosConstants.baseLocations.get(0)) +
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
            nydusDivers.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_MARINE));
            nydusDivers.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_MARAUDER));
            nydusDivers.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_SIEGE_TANK));
            //add 10 close scvs
            List<UnitInPool> scvs = Bot.OBS.getUnits(Alliance.SELF, scv ->
                    scv.unit().getType() == Units.TERRAN_SCV &&
                            Position.isSameElevation(scv.unit().getPosition(), nydusWorm.get().unit().getPosition()) &&
                            UnitUtils.getDistance(scv.unit(), nydusWorm.get().unit()) < 35 &&
                            !StructureScv.contains(scv.unit()))
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


    public static void pfBunkerTargetting() {
        List<Unit> pfBunkerList = UnitUtils.myUnitsOfType(Set.of(Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_BUNKER))
                .stream()
                .filter(u -> u.getBuildProgress() == 1 &&
                        ((u.getType() != Units.TERRAN_PLANETARY_FORTRESS || u.getWeaponCooldown().orElse(0f) > 8) || //keep target to let turret rotation complete
                                u.getWeaponCooldown().orElse(0f) == 0))
                .collect(Collectors.toList());

        pfBunkerList.forEach(pfBunker -> getBestPfTarget(pfBunker)
                .ifPresent(u -> ActionHelper.unitCommand(pfBunker, Abilities.ATTACK, u.unit(), false)));
    }

    private static Optional<UnitInPool> getBestPfTarget(Unit pf) {
        return UnitUtils.getEnemyTargetsInRange(pf).stream()
                .max(Comparator.comparing(target -> getPfTargetValue(pf, target)))
                .or(() -> UnitUtils.getIgnoredTargetInRange(pf))
                .or(() -> UnitUtils.getNeutralTargetInRange(pf));
    }

    private static int getPfTargetValue(Unit pf, UnitInPool target) {
        int targetValue = 0;
        //min value immortals with barrier, disabled units, or banes in detonate range
        if (target.unit().getBuffs().contains(Buffs.PROTECTIVE_BARRIER) ||
                !UnitUtils.canAttack(target.unit()) ||
                (target.unit().getType().toString().contains("BANELING") && UnitUtils.getDistance(pf, target.unit()) < 5.2)) { //bane range = 2.2, pf radius = 2.5, bane radius = ??
            return 1;
        }
        float targetHealth = target.unit().getHealth().orElse(9999f) +
                target.unit().getShield().orElse(0f);
        if (targetHealth <= 36) {
            targetValue += 1000;
        } else if (targetHealth <= 72) {
            targetValue += 100;
        }

        float dps = Math.max(10, UnitUtils.getDps(target.unit(), pf));
        if (target.unit().getType().toString().contains("BANELING")) {
            dps = 80;
        }
        targetValue += (int)(dps * 3);
        targetValue += 20 * UnitUtils.getEnemyGroundTargetsNear(target.unit().getPosition().toPoint2d(), 2).size()-1;
        UnitTypeData targetData = Bot.OBS.getUnitTypeData(false).get(target.unit().getType());
        targetValue += targetData.getMineralCost().orElse(0) / 10 +
                targetData.getVespeneCost().orElse(0) / 5;
        return targetValue;
    }

    private static void positionMarines() {
        //don't set target if Marine All-in code is handling it
        if (Strategy.MARINE_ALLIN && (MarineAllIn.doAttack || MarineAllIn.isInitialBuildUp)) {
            return;
        }

        if (OverlordHunter.overlordHunter != null && !OverlordHunter.overlordHunter.isAborting()) {
            UnitMicroList.getUnitSubList(MarineBasic.class)
                    .forEach(marine -> {
                        Unit overlord = OverlordHunter.overlordHunter.getOverlord().unit();
                        if (!Bot.OBS.isPathable(overlord.getPosition().toPoint2d())) {
                            Point2d reachableAttackPos = UnitUtils.getReachableAttackPos(
                                    overlord,
                                    marine.unit.unit());
                            if (reachableAttackPos != null) {
                                marine.targetPos = reachableAttackPos;
                            }
                        }
                        else {
                            marine.targetPos = OverlordHunter.overlordHunter.getOverlordPos();
                        }
                    });
            return;
        }

        List<UnitInPool> natBunkers = UnitUtils.getNatBunkers();
        boolean enemyInBunkerRange = natBunkers.stream()
                .anyMatch(bunker -> !UnitUtils.getEnemyTargetsInRange(bunker.unit()).isEmpty()); //enemies in range of bunker
        Unit enemyInMyBase = (!GameCache.baseList.get(1).isMyBase() && natBunkers.isEmpty()) ?
                getEnemyInMain() :
                getEnemyInMainOrNatural(!natBunkers.isEmpty());

        //if main/nat under attack and all enemies passed the bunker, then empty bunker and engage with marines
        if (enemyInMyBase != null && !enemyInBunkerRange) {
            natBunkers.stream()
                    .filter(bunker -> UnitUtils.getDistance(bunker.unit(), enemyInMyBase) > 8)
                    .forEach(bunker -> {
                        if (bunker.unit().getCargoSpaceTaken().orElse(0) > 0) {
                            ActionHelper.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
                        }
                    });
            MarineBasic.setTargetPos(enemyInMyBase.getPosition().toPoint2d());
            return;
        }

        if (UnitMicroList.numOfUnitClass(ScvDefender.class) > 0 && ArmyManager.attackGroundPos != null) {
            MarineBasic.setTargetPos(ArmyManager.attackGroundPos);
            return;
        }

        // go to bunker needing marines
        if (natBunkers.stream().anyMatch(bunker -> bunker.unit().getBuildProgress() == 1f && bunker.unit().getCargoSpaceTaken().orElse(4) != 4)) {
            Point2d bunkerPos = natBunkers.stream()
                    .min(Comparator.comparing(bunker -> bunker.unit().getCargoSpaceTaken().orElse(4) + (bunker.unit().getBuildProgress() < 1f ? 3.5f : 0)))
                    .get().unit().getPosition().toPoint2d();
            MarineBasic.setTargetPos(Position.towards(bunkerPos, GameCache.baseList.get(1).getCcPos(), 1.9f));
            return;
        }

        //cover lead tank
        if (leadTank != null && attackGroundPos != null) {
            Point2d marineAttackPos = Position.towards(
                    leadTank.unit(),
                    attackGroundPos,
                    leadTank.unit().getType() == Units.TERRAN_SIEGE_TANK ? 3.5f : 1.5f);
            MarineBasic.setTargetPos(marineAttackPos);
            return;
        }

        //otherwise, go to rally point
        MarineBasic.setTargetPos(attackEitherPos);
    }

    private static boolean isBehindMainOrNat(Point2d pos) {
        return GameCache.baseList.get(0).getResourceMidPoint().distance(pos) < 1 ||
                GameCache.baseList.get(1).getResourceMidPoint().distance(pos) < 1;
    }

    private static void positionLiberators() { //positions only 1 liberator per game loop
        Unit idleLib = UnitUtils.myUnitsOfType(Units.TERRAN_LIBERATOR).stream()
                .filter(unit -> ActionIssued.getCurOrder(unit).isEmpty())
                .findFirst().orElse(null);

        if (idleLib == null) {
            return;
        }

        //send available liberator to siege an expansion
        //for (Base base : GameCache.baseList) {
        for (int i=0; i<BASE_DEFENSE_INDEX_ORDER.length && i<GameCache.baseList.size(); i++) {
            if (BASE_DEFENSE_INDEX_ORDER[i] >= GameCache.baseList.size()) {
                continue;
            }
            Base base = GameCache.baseList.get(BASE_DEFENSE_INDEX_ORDER[i]);
            if (base.isMyBase() && !base.isMyMainBase() && !base.isDriedUp()) { //my expansion bases only
                for (DefenseUnitPositions libPos : base.getLiberatorPositions()) {
                    if (libPos.getUnit() == null) {
                        libPos.setUnit(Bot.OBS.getUnit(idleLib.getTag()), base);
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
                if (base.isMyBase() && !base.isMyMainBase() && !base.isDriedUp() && !base.isPocketBase()) { //my expansion bases only
                    for (DefenseUnitPositions tankPos : base.getInMineralLinePositions()) {
                        if (tankPos.getUnit() == null) {
                            tankPos.setUnit(Bot.OBS.getUnit(idleTank.getTag()), base);
                            UnitMicroList.add(new TankToPosition(tankPos.getUnit(), tankPos.getPos(), MicroPriority.SURVIVAL));
                            return;
                        }
                    }
                }
            }
        }

        //if no bases need a tank, put it on offense
        UnitMicroList.add(new TankOffense(Bot.OBS.getUnit(idleTank.getTag()), ArmyManager.attackGroundPos));
    }

    private static void salvageBunkerAtNatural() {
        List<UnitInPool> bunkerList = Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_BUNKER &&
                UnitUtils.getDistance(u.unit(), PosConstants.BUNKER_NATURAL) < 1);
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
        List<Unit> depots = UnitUtils.myUnitsOfType(UnitUtils.SUPPLY_DEPOT_TYPE);

        for (Unit depot : depots) {
            boolean isDepotLowered = depot.getType() == Units.TERRAN_SUPPLY_DEPOT_LOWERED;

            //WALL DEPOTS
            if (UnitUtils.isRampWallStructure(depot) || UnitUtils.isNatWallStructure(depot)) {
                //Raise
                if (isDepotLowered) {
                    if (InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depot.getPosition().toPoint2d())) {
                        ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
                    }
                }
                //Lower - if any enemy near depot or on the ramp
                else {
                    if (!InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depot.getPosition().toPoint2d()) &&
                            !InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, PosConstants.myRampPos)) {
                        ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
                    }
                }
            }

            //REAPER WALL DEPOTS
            else if (PosConstants.opponentRace == Race.TERRAN && UnitUtils.isReaperWallStructure(depot)) {
                boolean isReaperNearby = !UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_REAPER,
                        depot.getPosition().toPoint2d(), 12).isEmpty();
                //Raise
                if (isDepotLowered && isReaperNearby) {
                    ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
                }
                //Lower
                else if (!isDepotLowered && !isReaperNearby) {
                    boolean isMyScvNearby = !Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == Units.TERRAN_SCV &&
                            UnitUtils.getDistance(u.unit(), depot) < 3).isEmpty();
                    if (isMyScvNearby) {
                        ActionHelper.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
                    }
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
        float xEnemy = PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1).getX();
        float yEnemy = PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1).getY();
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
            xMove = -xMove;
        }
        if (yDistance > 0) {
            yMove = -yMove;
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
                case ZERG_OVERSEER_SIEGED: case PROTOSS_OBSERVER_SIEGED:
                    numVikings += 0.5;
                    hasMobileDetector = true;
                    break;
                case TERRAN_LIBERATOR: case TERRAN_LIBERATOR_AG: case TERRAN_BANSHEE:
                case ZERG_VIPER: case ZERG_BROODLORD_COCOON: case ZERG_BROODLORD:
                case PROTOSS_ORACLE: case TERRAN_MEDIVAC:
                    numVikings += 0.5;
                    break;
                case ZERG_MUTALISK: case ZERG_OVERLORD_TRANSPORT: case PROTOSS_WARP_PRISM:
                case PROTOSS_WARP_PRISM_PHASING:
                    numVikings += 1;
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
        boolean isInHellionRange = InfluenceMaps.getValue(InfluenceMaps.pointIn5RangeVsGround, hellion.getPosition().toPoint2d());
        boolean canAttack = UnitUtils.isWeaponAvailable(hellion) &&
                InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundValue, hellion.getPosition().toPoint2d()) < 200;

        //shoot when available
        if (canAttack && isInHellionRange) {
            //attack
            if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(hellion);
            return;
        }

        //always flee if locked on by cyclone
        if (hellion.getBuffs().contains(Buffs.LOCK_ON)) {
            retreatUnitFromCyclone(hellion);
            return;
        }

        if (isHellionUnsafe(hellion)) {
            //retreat
            new BasicUnitMicro(hellion, retreatPos, MicroPriority.SURVIVAL).onStep();
            return;
        }

        if (isInHellionRange) {
            //retreat
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(hellion);
            return;
        }

        //attack
        if (lastCommand != ArmyCommands.ATTACK) armyGroundAttacking.add(hellion);
    }

    private static boolean isHellionUnsafe(Unit hellion) {
        boolean isUnsafe;
        if (!UnitUtils.isWeaponAvailable(hellion)) {
            isUnsafe = InfluenceMaps.getValue(InfluenceMaps.pointThreatToGroundPlusBuffer, hellion.getPosition().toPoint2d());
        }
        else if (!isVsOnlyAdepts()) {
            isUnsafe = InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, hellion.getPosition().toPoint2d());
        }
        else {
            isUnsafe = false;
        }
        return isUnsafe;
    }

    private static boolean isVsOnlyAdepts() {
        return attackUnit != null &&
                UnitUtils.getEnemyGroundArmyUnitsNearby(attackGroundPos, 4)
                        .stream()
                        .noneMatch(u -> UnitUtils.getGroundAttackRange(u.unit()) > 3 &&
                                !UnitUtils.ADEPT_TYPE.contains(u.unit().getType()) &&
                                u.unit().getType() != Units.PROTOSS_HIGH_TEMPLAR);
    }

    public static void giveBansheeCommand(Unit banshee) {
        ArmyCommands lastCommand = getCurrentCommand(banshee);
        int x = InfluenceMaps.toMapCoord(banshee.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(banshee.getPosition().getY());
        boolean isUnsafe = (Switches.isDivingTempests) ?
                false :
                InfluenceMaps.pointThreatToAirValue[x][y] > 2 || InfluenceMaps.pointDamageToAirValue[x][y] > banshee.getHealth().orElse(150f);
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke() &&
                UnitUtils.isRepairBaySafe();
        boolean isInDetectionRange = InfluenceMaps.pointDetected[x][y];
        boolean isInBansheeRange = InfluenceMaps.point6RangevsGround[x][y];
        boolean canAttack = UnitUtils.isWeaponAvailable(banshee) && InfluenceMaps.pointThreatToAirValue[x][y] < 200;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0f) > 25 &&
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
            ActionHelper.unitCommand(banshee, Abilities.MOVE, PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1), false);
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
            else if (canCloak && !hasDecloakBuff && UnitUtils.getHealthPercentage(banshee) < 99) {
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
        else if (isWaitingForCloak(banshee) && !isAnyBaseUnderAttack) {
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
        Point2d kiteBackPos = Position.towards(myUnit, enemyPos, -3.5f);
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
            ActionHelper.unitCommand(myUnit, Abilities.MOVE, Position.towards(myUnit, cyclonePos, -3f), false);
        }
    }

    private static boolean isWaitingForCloak(Unit banshee) {
        return Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) &&
                banshee.getCloakState().orElse(CloakState.CLOAKED_ALLIED) != CloakState.CLOAKED_ALLIED &&
                banshee.getEnergy().orElse(0f) < Strategy.ENERGY_TO_SAVE;
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
            ActionHelper.unitCommand(viking, Abilities.MOVE, PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1), false);
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
        Point2d retreatingPos = Position.towards(myAirUnit, retreatPos, 2);
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
        if (PosConstants.opponentRace != Race.TERRAN || attackAirPos == null) {
            return false;
        }

        int numVikingsOnFrontLine = (int)GameCache.vikingList.stream()
                .filter(viking -> UnitUtils.getDistance(viking, attackAirPos) < 20 &&
                        UnitUtils.getHealthPercentage(viking) > Strategy.RETREAT_HEALTH)
                .count();

        int enemyVikings = (int)UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).stream()
                .filter(enemyViking -> enemyViking.getLastSeenGameLoop() + Time.toFrames(5) > Time.nowFrames())
                .count();
        return numVikingsOnFrontLine * 1.33f < enemyVikings;
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
        Optional<ActionIssued> curOrder = ActionIssued.getCurOrder(raven);
        if (curOrder.stream().anyMatch(order -> order.ability == Abilities.EFFECT_AUTO_TURRET) &&
                !UnitUtils.getEnemyTargetsNear(curOrder.get().targetPos, 7).isEmpty() &&
                !UnitUtils.isEnemyGroundUnitsNearby(curOrder.get().targetPos, 1)) {
            return;
        }

        ArmyCommands lastCommand = getCurrentCommand(raven);
        int autoturretAtEnergy = beLiberalWithRavenEnergy(raven) ? 50 : Strategy.AUTOTURRET_AT_ENERGY;
        boolean[][] threatMap =
                raven.getEnergy().orElse(0f) >= (Strategy.DO_MATRIX ? 75 : autoturretAtEnergy)
                ? InfluenceMaps.pointThreatToAir
                : InfluenceMaps.pointThreatToAirPlusBuffer;
        boolean isUnsafe = InfluenceMaps.getValue(threatMap, raven.getPosition().toPoint2d());

        Optional<UnitInPool> turretTarget = getRavenTurretTarget(raven);
        boolean inRange = turretTarget.isPresent();
        boolean canRepair = !Cost.isGasBroke() && !Cost.isMineralBroke() &&
                UnitUtils.isRepairBaySafe();
        int healthToRepair = (!doOffense && attackUnit == null) ? 99 : (Strategy.RETREAT_HEALTH + 10);
        boolean isParasitic = raven.getBuffs().contains(Buffs.PARASITIC_BOMB); //TODO: parasitic bomb run sideways

        //always flee if locked on by cyclone
        if (raven.getBuffs().contains(Buffs.LOCK_ON)) {
            retreatUnitFromCyclone(raven);
            return;
        }

        //fly to enemy main if parasitic'ed
        if (isParasitic) {
            ActionHelper.unitCommand(raven, Abilities.MOVE, PosConstants.baseLocations.get(PosConstants.baseLocations.size()-1), false);
            return;
        }

        //stay in repair bay if not on offensive or under 100% health
        if (canRepair && UnitUtils.getHealthPercentage(raven) < 100 && UnitUtils.getDistance(raven, retreatPos) < 3) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
            return;
        }

        //go home to repair if low TODO: kite first
        if (canRepair && UnitUtils.getHealthPercentage(raven) < healthToRepair) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
            return;
        }

        if (Strategy.ARCHON_MASS_RAVEN) { //no other micro as it's the job of the archon player
            if (!raven.getSelected().orElse(false) && !raven.getActive().orElse(true)) { //send home if not human-controlled
                if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
            }
            return;
        }

        //go home to repair if mass raven strategy and no energy for autoturrets
        if (Strategy.MASS_RAVENS && UnitUtils.getHealthPercentage(raven) < 100 &&
                raven.getEnergy().orElse(0f) < 35) {
            if (lastCommand != ArmyCommands.HOME) armyGoingHome.add(raven);
            return;
        }

        if (Strategy.DO_SEEKER_MISSILE && castSeeker(raven)) {
            return;
        }
        if (Strategy.DO_MATRIX && castMatrix(raven)) {
            return;
        }
        if (doCastTurrets && inRange && doAutoTurret(raven, turretTarget, autoturretAtEnergy)) {
            return;
        }

        //back up if in range
        if (isUnsafe) {
            kiteBackAirUnit(raven, lastCommand);
            return;
        }

        //go forward if not in range
        if (lastCommand != ArmyCommands.ATTACK) {
            if (attackCloakedPos != null) {
                armyDetectorAttacking.add(raven);
            }
            else if (raven.getEnergy().orElse(0f) > 175) {
                armyGroundAttacking.add(raven);
            }
            else {
                armyAirAttacking.add(raven);
            }
            return;
        }
    }

    //if on offense with mass raven
    //if defending main/nat
    //if near dying pf
    //if near a friendly siege tank
    private static boolean beLiberalWithRavenEnergy(Unit raven) {
        return (doOffense && UnitUtils.numMyUnits(Units.TERRAN_RAVEN, false) >= 8 ) ||
                UnitUtils.isInMyMainOrNat(raven) ||
                UnitUtils.isNearInjuredPF(raven, 18, 92) ||
                UnitUtils.isUnitTypesNearby(Alliance.SELF, UnitUtils.SIEGE_TANK_TYPE, raven.getPosition().toPoint2d(), 8);
    }

    private static Optional<UnitInPool> getRavenTurretTarget(Unit raven) {
        List<UnitInPool> targets = Bot.OBS.getUnits(Alliance.ENEMY,
                        enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                !enemy.unit().getType().toString().contains("CHANGELING") &&
                isTargetImportant(enemy.unit()) &&
                UnitUtils.getDistance(raven, enemy.unit()) < 11); //radius 1, cast range 2, attack range 7, 1 enemy radius/buffer
        if (targets.isEmpty() && raven.getEnergy().orElse(0f) > 175) {
            targets = Bot.OBS.getUnits(Alliance.ENEMY, enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                    !enemy.unit().getType().toString().contains("CHANGELING") &&
                    UnitUtils.getDistance(raven, enemy.unit()) < 10); //radius 1, cast range 2, attack range 7
        }
        return targets.stream()
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(raven, enemy.unit())));
    }

    //drop auto-turrets near enemy
    private static boolean doAutoTurret(Unit raven, Optional<UnitInPool> turretTarget, int autoturretAtEnergy) {
        if (turretTarget.isEmpty()) {
            return false;
        }
        if (!UnitUtils.isEnemyRetreating(turretTarget.get().unit(), raven.getPosition().toPoint2d()) &&
                raven.getEnergy().orElse(0f) >= autoturretAtEnergy &&
                !raven.getBuffs().contains(Buffs.RAVEN_SCRAMBLER_MISSILE)) {
            return castAutoTurret(raven, turretTarget.get().unit());
        }
        return false;
    }

    private static boolean isAttackUnitImportant() {
        return attackUnit != null && isTargetImportant(attackUnit);
    }

    private static boolean isTargetImportant(Unit targetUnit) {
        return !UnitUtils.isStructure(targetUnit.getType()) || UnitUtils.canAttack(targetUnit);
    }

    private static boolean castAutoTurret(Unit raven, Unit enemyTarget) {
        //place it far back if the enemy isn't running away
        boolean doPlaceFarBack = !UnitUtils.canMove(enemyTarget) ||
                !UnitUtils.isEnemyRetreating(enemyTarget, raven.getPosition().toPoint2d());
        TurretingRaven.add(raven, enemyTarget.getPosition().toPoint2d(), doPlaceFarBack);
        return true;
    }

//    private static boolean castAutoTurret(Unit raven, Unit enemyTarget) {
//        //see how close we can safely cast from
//        Point2d ravenPos = raven.getPosition().toPoint2d();
//        Point2d enemyTargetPos = enemyTarget.getPosition().toPoint2d();
//        float distanceToTarget = UnitUtils.getDistance(raven, enemyTargetPos);
//        Point2d turretPos = ravenPos;
//        if (!raven.getBuffs().contains(Buffs.FUNGAL_GROWTH)) { //cast at raven if fungaled
//            for (int i = 1; i < distanceToTarget - 1; i++) {
//                Point2d testPos = Position.towards(ravenPos, enemyTargetPos, i);
//                if (testSafetyOfPos(testPos, enemyTarget)) {
//                    break;
//                }
//                turretPos = testPos;
//            }
//        }
//        turretPos = Position.toWholePoint(turretPos);
//
//        //get list of nearby placeable positions
//        List<Point2d> posList = Position.getSpiralList(turretPos, 3).stream()
//                .filter(p -> p.distance(enemyTargetPos) < 7)
//                .filter(p -> Bot.OBS.isPlacable(p))
//                .sorted(Comparator.comparing(p -> p.distance(enemyTargetPos)))
//                .collect(Collectors.toList());
//        if (posList.isEmpty()) {
//            return false;
//        }
//
//        List<QueryBuildingPlacement> queryList = posList.stream()
//                .map(p -> QueryBuildingPlacement
//                        .placeBuilding()
//                        .useAbility(Abilities.EFFECT_AUTO_TURRET)
//                        .on(p).build())
//                .collect(Collectors.toList());
//
//        List<Boolean> placementList = Bot.QUERY.placement(queryList);
//        queriesMade++;
//
//        if (placementList.contains(true)) {
//            Point2d placementPos = posList.get(placementList.indexOf(true));
//            ActionHelper.unitCommand(raven, Abilities.EFFECT_AUTO_TURRET, placementPos, false);
//            //PlacementMap.makeUnavailable(Units.TERRAN_AUTO_TURRET, placementPos);
//            turretsCast++;
//            return true;
//        }
////        else if (attackUnit != null && !UnitUtils.canMove(attackUnit) && towardsEnemyDistance < 4) {
////            castAutoTurret(raven, 4);
////        }
//        return false;
//    }

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
        int xMax = Math.min(InfluenceMaps.toMapCoord(PosConstants.SCREEN_TOP_RIGHT.getX()), (ravenX+Strategy.CAST_SEEKER_RANGE)*2);
        int yMin = Math.max(0, ravenY-Strategy.CAST_SEEKER_RANGE);
        int yMax = Math.min(InfluenceMaps.toMapCoord(PosConstants.SCREEN_TOP_RIGHT.getY()), (ravenY+Strategy.CAST_SEEKER_RANGE)*2);
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
        List<Unit> bio = UnitUtils.myUnitsOfType(Units.TERRAN_MARINE);
        bio.addAll(UnitUtils.myUnitsOfType(Units.TERRAN_MARAUDER));
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
        boolean doHaveRaven = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, false) > 0;
        boolean[][] pointInNat = doExcludeBunkerRange ? InfluenceMaps.pointInNatExcludingBunkerRange : InfluenceMaps.pointInNat;
        return Bot.OBS.getUnits(Alliance.ENEMY).stream()
                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        (doHaveRaven || enemy.unit().getDisplayType() != DisplayType.HIDDEN) && //ignore undetected cloaked/burrowed units
                        (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()) ||
                        InfluenceMaps.getValue(pointInNat, enemy.unit().getPosition().toPoint2d())))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(0).getCcPos())))
                .map(UnitInPool::unit)
                .orElse(null);
    }

    public static Unit getEnemyInMain() {
        boolean doHaveRaven = UnitUtils.numMyUnits(Units.TERRAN_RAVEN, false) > 0;
        return Bot.OBS.getUnits(Alliance.ENEMY).stream()
                .filter(enemy -> !UnitUtils.IGNORED_TARGETS.contains(enemy.unit().getType()) &&
                        (doHaveRaven || enemy.unit().getDisplayType() != DisplayType.HIDDEN) && //ignore undetected cloaked/burrowed units
                        InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, enemy.unit().getPosition().toPoint2d()))
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), GameCache.baseList.get(0).getCcPos()) +
                        (!UnitUtils.canAttack(enemy.unit()) ? 1000 : 0)))
                .map(UnitInPool::unit)
                .orElse(null);
    }
}
