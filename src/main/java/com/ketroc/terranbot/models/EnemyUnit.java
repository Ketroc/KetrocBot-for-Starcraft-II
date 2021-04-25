package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.Time;
import com.ketroc.terranbot.utils.UnitUtils;

public class EnemyUnit {

    public float x;
    public float y;
    public float supply;
    public boolean isAir;
    public boolean isDetector;
    public boolean isEffect;
    public boolean isArmy;
    public boolean isSeekered;
    public boolean isTempest;
    public boolean isTumor;
    public boolean isInProgress;
    public float detectRange;
    public float visionRange;
    public float groundAttackRange;
    public float airAttackRange;
    public int threatLevel;
    public int groundDamage;
    public int airDamage;
    public int pfTargetLevel;
    public boolean isPersistentDamage;
    public float maxRange; //used to determine what portion of the grid to loop through

    public EnemyUnit(Unit enemy) {
        x = enemy.getPosition().getX();
        y = enemy.getPosition().getY();
        supply = Bot.OBS.getUnitTypeData(false).get(enemy.getType()).getFoodRequired().orElse(0f);
        isAir = enemy.getFlying().orElse(false);
        isTumor = UnitUtils.CREEP_TUMOR.contains(enemy.getType());
        isInProgress = enemy.getBuildProgress() > 0 && enemy.getBuildProgress() < 0.95f;
        if (isInProgress) {
            threatLevel = 0;
            detectRange = 0;
            airAttackRange = 0;
            groundAttackRange = 0;

        }
        else {
            threatLevel = getThreatValue((Units) enemy.getType());
            groundDamage = getGroundDamage((Units) enemy.getType());
            airDamage = getAirDamage((Units) enemy.getType());
            detectRange = getDetectionRange(enemy);
            visionRange = UnitUtils.getVisionRange(enemy);
            airAttackRange = UnitUtils.getAirAttackRange(enemy);
            groundAttackRange = UnitUtils.getGroundAttackRange(enemy);
        }

        float kitingBuffer = getKitingBuffer(enemy);
        if (groundAttackRange != 0) {
            groundAttackRange += kitingBuffer;
        }
        if (airAttackRange != 0) {
            airAttackRange += kitingBuffer;
        }
        if (detectRange != 0) {
            detectRange += kitingBuffer;
        }
        pfTargetLevel = getPFTargetValue(enemy);
        isDetector = detectRange > 0f;
        isArmy = supply > 0 &&
                (!UnitUtils.WORKER_TYPE.contains(enemy.getType()) || //any unit that costs supply and is not a worker
                        (Strategy.WALL_OFF_IMMEDIATELY &&
                                UnitUtils.getEnemyUnitsOfTypes(UnitUtils.WORKER_TYPE).size() > 4 &&
                                Time.nowFrames() < Time.toFrames("3:00"))); //include workers if defending worker rush
        isSeekered = enemy.getBuffs().contains(Buffs.RAVEN_SHREDDER_MISSILE_TINT);
        switch ((Units)enemy.getType()) {
            case PROTOSS_PHOENIX: case PROTOSS_COLOSSUS:
                airAttackRange += 2; //hack to assume enemy has its range upgrade since enemy upgrades cannot be checked
                break;
            case TERRAN_MISSILE_TURRET: case TERRAN_AUTO_TURRET: case ZERG_HYDRALISK: case TERRAN_PLANETARY_FORTRESS: //hack to assume enemy has its range upgrade since enemy upgrades cannot be checked
            case ZERG_MUTALISK: //giving more kiting range since it's fast
                if (airAttackRange != 0) {
                    airAttackRange++;
                }
                if (groundAttackRange != 0) {
                    groundAttackRange++;
                }
                break;
            case TERRAN_MARINE: case PROTOSS_SENTRY: case PROTOSS_HIGH_TEMPLAR: //lessen buffer on units banshees should kite anyhow
                airAttackRange -= 0.5f;
                break;
            case TERRAN_CYCLONE: //lessen attack range of cyclones assuming they will only lock on
                airAttackRange = 3.5f;
                groundAttackRange = 3.5f;
                break;
            case PROTOSS_TEMPEST:
                isTempest = true;
                break;
        }
        calcMaxRange(); //largest range of airattack, detection, range from banshee/viking
    }

    public EnemyUnit(Unit friendly, boolean isParasitic) {
        x = friendly.getPosition().getX();
        y = friendly.getPosition().getY();
        isPersistentDamage = true;
        isDetector = true;
        detectRange = 3f + Strategy.KITING_BUFFER;
        airAttackRange = 3f + Strategy.KITING_BUFFER;
        threatLevel = 200;
        airDamage = 120;
        calcMaxRange();
    }

    public EnemyUnit(Point2d pos, boolean isFungal) {
        x = pos.getX();
        y = pos.getY();
        isDetector = true;
        isEffect = true;
        detectRange = 3.5f;
        groundAttackRange = 3.5f;
        groundDamage = 30;
        airDamage = 30;
        airAttackRange = 3.5f;
        maxRange = 3.5f;
        threatLevel = 200;
    }

    public EnemyUnit(EffectLocations effect) {
        isEffect = true;
        isPersistentDamage = true;
        Point2d position = effect.getPositions().iterator().next();
        x = position.getX();
        y = position.getY();
        switch ((Effects)effect.getEffect()) {
            case LIBERATOR_TARGET_MORPH_DELAY_PERSISTENT:
            case LIBERATOR_TARGET_MORPH_PERSISTENT:
                threatLevel = 200;
                groundAttackRange = effect.getRadius().get() + Strategy.STATIONARY_KITING_BUFFER;
                groundDamage = 75;
                break;
            case SCANNER_SWEEP:
                isDetector = true;
                detectRange = 13f + Strategy.STATIONARY_KITING_BUFFER;
                break;
            case RAVAGER_CORROSIVE_BILE_CP:
                isDetector = true;
                detectRange = 5f + Strategy.STATIONARY_KITING_BUFFER; //actual range is 0.5f but effect disappears prior to it landing
                threatLevel = 200;
                airAttackRange = 5f + Strategy.STATIONARY_KITING_BUFFER; //actual range is 0.5f but effect disappears prior to it landing
                groundDamage = 60;
                airDamage = 60;
                break;
//            case NUKE_PERSISTENT:
//                isDetector = true;
//                detectRange = effect.getRadius().get() + kitingBuffer;
//                threatLevel = 200;
//                airAttackRange = effect.getRadius().get() + kitingBuffer;
//                groundAttackRange = airAttackRange;
//                damage = 300;
//                break;
            case PSI_STORM_PERSISTENT:
                isDetector = true;
                detectRange = effect.getRadius().get() + Strategy.STATIONARY_KITING_BUFFER;
                threatLevel = 200;
                airAttackRange = effect.getRadius().get() + Strategy.STATIONARY_KITING_BUFFER;
                groundAttackRange = airAttackRange;
                groundDamage = 80;
                airDamage = 80;
                break;
        }
        calcMaxRange(); //largest range of airattack, detection, range from banshee/viking
    }

    private int getGroundDamage(Units unitType) {
        return getDamage(unitType, Weapon.TargetType.AIR);
    }

    private int getAirDamage(Units unitType) {
        return getDamage(unitType, Weapon.TargetType.GROUND);
    }

    private int getDamage(Units unitType, Weapon.TargetType excludeTargetType) {
        return Bot.OBS.getUnitTypeData(false).get(unitType).getWeapons().stream()
                .filter(weapon -> weapon.getTargetType() != excludeTargetType)
                .findFirst()
                .map(weapon -> weapon.getDamage() * weapon.getAttacks())
                .orElse(0f)
                .intValue();
    }

    private float getKitingBuffer(Unit enemy) {
        return (!UnitUtils.canMove(enemy) || (groundAttackRange > 0 && groundAttackRange < 2)) ? Strategy.STATIONARY_KITING_BUFFER : Strategy.KITING_BUFFER;
    }

    private float getDetectionRange(Unit enemy) {
        float range = enemy.getDetectRange().orElse(0f);
        if (range == 0f) { //handle snapshots of detectors
            switch ((Units)enemy.getType()) {
                case PROTOSS_PHOTON_CANNON: case TERRAN_MISSILE_TURRET: case ZERG_SPORE_CRAWLER:
                    range = 11;
                    break;
                case PROTOSS_OBSERVER:
                    range = 11;
                    break;
            }
        }
        if (range != 0) {
            range += enemy.getRadius();
        }
        return range;
    }

    private void calcMaxRange() {
        //viking stay back range if tempests are out
        if (!UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty()) {
            maxRange = 15 + Strategy.KITING_BUFFER;
            return;
        }

        //set the largest range of air attack, ground attack, detection, viking range, and banshee range
        maxRange = Math.max(airAttackRange + Strategy.RAVEN_DISTANCING_BUFFER, groundAttackRange);
        maxRange = Math.max(maxRange, detectRange);
        if (isAir) {
            maxRange = Math.max(maxRange, Strategy.VIKING_RANGE);
        }
        else {
            if (isDetector) { //spell effects (any turrets/spores/cannons)
                maxRange = Math.max(maxRange, Strategy.BANSHEE_RANGE);
            }
            else {
                maxRange = Math.max(maxRange, 13); //13 for GameCache.pointGroundUnitWithin13
            }
        }
    }

    public static int getThreatValue(Units unitType) {
        switch (unitType) {
            case TERRAN_HELLION:
                return 2;
            case TERRAN_HELLION_TANK:
                return 3;
            case TERRAN_MARAUDER:
                return 3;
            case TERRAN_SIEGE_TANK:
                return 4;
            case TERRAN_SIEGE_TANK_SIEGED:
                return 4;
            case TERRAN_BANSHEE:
                return 3;
            case TERRAN_PLANETARY_FORTRESS:
                return 6;
            case TERRAN_VIKING_ASSAULT:
                return 3;
            case TERRAN_KD8CHARGE:
                return 1;
            case TERRAN_MARINE:
                return 2;
            case TERRAN_MISSILE_TURRET:
                return 7;
            case TERRAN_BUNKER: //assume 4 marines
                return 12;
            case TERRAN_VIKING_FIGHTER:
                return 3;
            case TERRAN_LIBERATOR:
                return 6;
            case TERRAN_GHOST:
                return 4;
            case TERRAN_AUTO_TURRET:
                return 6;
            case TERRAN_CYCLONE:
                return 2;
            case TERRAN_THOR:
                return 14;
            case TERRAN_THOR_AP:
                return 6;
            case TERRAN_WIDOWMINE_BURROWED:
                return 30; //TODO: what to do with that?
            case TERRAN_BATTLECRUISER:
                return 8;
            case PROTOSS_SENTRY:
                return 2;
            case PROTOSS_HIGH_TEMPLAR:
                return 2;
            case PROTOSS_ZEALOT:
                return 3;
            case PROTOSS_ADEPT:
                return 3;
            case PROTOSS_DARK_TEMPLAR:
                return 5;
            case PROTOSS_IMMORTAL:
                return 6;
            case PROTOSS_COLOSSUS:
                return 4;
            case PROTOSS_DISRUPTOR_PHASED:
                return 30;
            case PROTOSS_ORACLE:
                return 3;
            case PROTOSS_PHOENIX:
                return 5;
            case PROTOSS_ARCHON:
                return 10;
            case PROTOSS_INTERCEPTOR:
                return 1;
            case PROTOSS_MOTHERSHIP:
                return 5;
            case PROTOSS_VOIDRAY:
                return 4;
            case PROTOSS_STALKER:
                return 3;
            case PROTOSS_TEMPEST:
                return 2; //should be 4, but this is a hack to ignore tempests when only 1 of them
            case PROTOSS_PHOTON_CANNON:
                return 5;
            case ZERG_ZERGLING:
                return 1;
            case ZERG_ROACH:
                return 2;
            case ZERG_INFESTOR_TERRAN:
                return 2;
            case ZERG_BANELING:
                return 6;
            case ZERG_ULTRALISK:
                return 8;
            case ZERG_LOCUS_TMP:
                return 3;
            case ZERG_RAVAGER:
                return 3;
            case ZERG_BROODLING:
                return 1;
            case ZERG_BROODLORD:
                return 3;
            case ZERG_SPINE_CRAWLER:
                return 4;
            case ZERG_LURKER_MP_BURROWED:
                return 4;
            case ZERG_HYDRALISK:
                return 3;
            case ZERG_QUEEN:
                return 4;
            case ZERG_MUTALISK:
                return 3;
            case ZERG_CORRUPTOR:
                return 3;
            case ZERG_SPORE_CRAWLER:
                return 5;
        }
        return 0;
    }

    public static int getPFTargetValue(Unit enemy) {
        switch ((Units)enemy.getType()) {
            case TERRAN_SCV:
                return 3;
            case TERRAN_MARINE:
                return 5;
            case TERRAN_MARAUDER:
                return 6;
            case TERRAN_GHOST:
                return 11;
            case TERRAN_AUTO_TURRET:
                return 1;
            case TERRAN_CYCLONE:
                return 6;
            case TERRAN_THOR:
                return 4;
            case TERRAN_THOR_AP:
                return 4;
            case TERRAN_SIEGE_TANK:
                return 5;
            case TERRAN_SIEGE_TANK_SIEGED:
                return 4;
            case TERRAN_WIDOWMINE: case TERRAN_WIDOWMINE_BURROWED:
                return 3;
            case TERRAN_HELLION:
                return 2;
            case TERRAN_HELLION_TANK:
                return 5;
            case PROTOSS_ZEALOT:
                return 5;
            case PROTOSS_PROBE:
                return 5;
            case PROTOSS_ADEPT:
                return 3;
            case PROTOSS_SENTRY:
                return 3;
            case PROTOSS_STALKER:
                return 4;
            case PROTOSS_COLOSSUS:
                return 3;
            case PROTOSS_IMMORTAL:
                if (enemy.getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
                    return 0;
                }
                return 15;
            case PROTOSS_HIGH_TEMPLAR:
                return 15;
            case PROTOSS_ARCHON:
                return 5;
            case PROTOSS_DARK_TEMPLAR:
                return 9;
            case ZERG_DRONE:
                return 5;
            case ZERG_DRONE_BURROWED:
                return 4;
            case ZERG_HYDRALISK:
                return 6;
            case ZERG_HYDRALISK_BURROWED:
                return 5;
            case ZERG_QUEEN:
                return 3;
            case ZERG_QUEEN_BURROWED:
                return 2;
            case ZERG_INFESTOR:
                return 11;
            case ZERG_INFESTOR_BURROWED:
                return 11;
            case ZERG_LURKER_MP:
                return 8;
            case ZERG_LURKER_MP_BURROWED:
                return 8;
            case ZERG_ZERGLING:
                return 5;
            case ZERG_ZERGLING_BURROWED:
                return 5;
            case ZERG_BANELING:
                return 25;
            case ZERG_BANELING_BURROWED:
                return 8;
            case ZERG_BANELING_COCOON:
                return 1;
            case ZERG_RAVAGER:
                return 6;
//            case ZERG_RAVAGER_BURROWED:
//                return 5;
            case ZERG_RAVAGER_COCOON:
                return 1;
            case ZERG_ULTRALISK:
                return 3;
//            case ZERG_ULTRALISK_BURROWED:
//                return 2;
            case ZERG_SWARM_HOST_MP:
                return 4;
            case ZERG_SWARM_HOST_BURROWED_MP:
                return 3;
            case ZERG_LOCUS_TMP:
                return 1;

        }
        return 0;
    }

}
