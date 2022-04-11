package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.UnitUtils;

public class EnemyMapping {
    public static float CYCLONE_AIR_ATTACK_RANGE = 3.5f;
    public static float CYCLONE_GROUND_ATTACK_RANGE = 4.5f;

    public Units unitType;
    public float unitRadius;
    public float empValue;
    public float x;
    public float y;
    public float supply;
    public boolean isAir;
    public boolean isDetector;
    public boolean isEffect;
    public boolean isArmy;
    public boolean canMove;
    public boolean isSeekered;
    public boolean isTempest;
    public boolean isTumor;
    public boolean isInProgress;
    public float detectRange;
    public float sightRange;
    public float groundAttackRange;
    public float airAttackRange;
    public int threatLevel;
    public int groundDamage;
    public int airDamage;
    public int pfTargetLevel;
    public boolean isPersistentDamage;
    public float maxRange; //used to determine what portion of the grid to loop through

    protected int getGroundDamage(Units unitType) {
        return getDamage(unitType, Weapon.TargetType.AIR);
    }

    protected int getAirDamage(Units unitType) {
        return getDamage(unitType, Weapon.TargetType.GROUND);
    }

    protected int getDamage(Units unitType, Weapon.TargetType excludeTargetType) {
        if (unitType.toString().contains("CHANGELING")) {
            return 0;
        }
        if (unitType == Units.TERRAN_BUNKER) {
            return 24;
        }
        return Bot.OBS.getUnitTypeData(false).get(unitType).getWeapons().stream()
                .filter(weapon -> weapon.getTargetType() != excludeTargetType)
                .findFirst()
                .map(weapon -> weapon.getDamage() * weapon.getAttacks())
                .orElse(0f)
                .intValue();
    }

    protected float getKitingBuffer(Unit enemy) {
        return (!UnitUtils.canMove(enemy) || (groundAttackRange > 0 && groundAttackRange < 2)) ?
                Strategy.STATIONARY_KITING_BUFFER :
                Strategy.KITING_BUFFER;
    }

    protected float getDetectionRange(Unit enemy) {
        float range = enemy.getDetectRange().orElse(0f);
        if (range == 0f) { //handle snapshots of detectors
            switch (unitType) {
                case PROTOSS_PHOTON_CANNON: case TERRAN_MISSILE_TURRET: case ZERG_SPORE_CRAWLER:
                    range = 11;
                    break;
                case PROTOSS_OBSERVER:
                    range = 11;
                    break;
            }
        }
        return range;
    }

    protected void calcMaxRange() {
        //viking stay back range if tempests are out or mass raven strat
        if (Strategy.MASS_RAVENS || !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty()) {
            maxRange = 15 + Strategy.KITING_BUFFER;
            return;
        }

        //set the largest range of air attack, ground attack, detection, viking range, and banshee range
        maxRange = Math.max(airAttackRange + Strategy.RAVEN_DISTANCING_BUFFER, groundAttackRange + Strategy.RAVEN_DISTANCING_BUFFER);
        maxRange = Math.max(maxRange, sightRange);
        maxRange = Math.max(maxRange, detectRange);
        if (isAir) {
            maxRange = Math.max(maxRange, Strategy.VIKING_RANGE);
        } else if (isTargettableUnit()) {
            maxRange = Math.max(maxRange, 13); //13 for GameCache.pointGroundUnitWithin13
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
                return Strategy.DO_IGNORE_BUNKERS ? 0 : 12;
            case TERRAN_VIKING_FIGHTER:
                return 3;
            case TERRAN_LIBERATOR:
                return 2;
            case TERRAN_GHOST:
                return 4;
            case TERRAN_AUTO_TURRET:
                return 6;
            case TERRAN_CYCLONE:
                return 3;
            case TERRAN_THOR:
                return 14;
            case TERRAN_THOR_AP:
                return 6;
            case TERRAN_WIDOWMINE_BURROWED:
                return 20; //TODO: what to do with that?
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
                return 4;
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
        if (enemy.getBuffs().contains(Buffs.IMMORTAL_OVERLOAD)) {
            return 0;
        }
        return getPFTargetValue((Units)enemy.getType());
    }

    public static int getPFTargetValue(Units enemyType) {
        switch (enemyType) {
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

    public boolean isTargettableUnit() {
        return unitType != Units.INVALID && !UnitUtils.UNTARGETTABLES.contains(unitType);
    }

}
