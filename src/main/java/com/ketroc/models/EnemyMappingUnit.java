package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.micro.GhostBasic;
import com.ketroc.utils.UnitUtils;

public class EnemyMappingUnit extends EnemyMapping {

    public EnemyMappingUnit(Unit enemy) {
        unitType = (Units)enemy.getType();
        unitRadius = enemy.getRadius();
        empValue = getEmpValue(enemy);
        x = enemy.getPosition().getX();
        y = enemy.getPosition().getY();
        UnitTypeData unitTypeData = Bot.OBS.getUnitTypeData(false).get(unitType);
        supply = unitTypeData.getFoodRequired().orElse(0f);
        canMove = unitTypeData.getMovementSpeed().orElse(0f) > 0;
        isAir = enemy.getFlying().orElse(false);
        isTumor = UnitUtils.CREEP_TUMOR_TYPES.contains(unitType);
        isInProgress = enemy.getBuildProgress() > 0 && enemy.getBuildProgress() < 0.95f;
        if (isInProgress || isUnpoweredCannon(enemy)) {
            threatLevel = 0;
            detectRange = 0;
            airAttackRange = 0;
            groundAttackRange = 0;
        }
        else {
            threatLevel = getThreatValue(unitType);
            groundDamage = getGroundDamage(unitType);
            airDamage = getAirDamage(unitType);
            detectRange = getDetectionRange(enemy);
            sightRange = unitTypeData.getSightRange().orElse(0f);
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
        isArmy = supply > 0 && !UnitUtils.WORKER_TYPE.contains(enemy.getType()); //any unit that costs supply and is not a worker
//        isArmy = supply > 0 && !UnitUtils.WORKER_TYPE.contains(enemy.getType()) || //any unit that costs supply and is not a worker
//                        (Strategy.WALL_OFF_IMMEDIATELY &&
//                                UnitUtils.getEnemyUnitsOfType(UnitUtils.WORKER_TYPE).size() > 5 &&
//                                Time.nowFrames() < Time.toFrames("3:00"))); //include workers if defending worker rush
        isSeekered = enemy.getBuffs().contains(Buffs.RAVEN_SHREDDER_MISSILE_TINT);
        switch (unitType) {
            case ZERG_LURKER_MP_BURROWED: case PROTOSS_COLOSSUS:
                groundAttackRange += 2; //hack to assume enemy has its range upgrade since enemy upgrades cannot be checked
                break;
            case PROTOSS_PHOENIX:
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
                airAttackRange = CYCLONE_AIR_ATTACK_RANGE;
                groundAttackRange = CYCLONE_GROUND_ATTACK_RANGE;
                break;
            case PROTOSS_TEMPEST:
                isTempest = true;
                break;
        }
        calcMaxRange(); //largest range of airattack, detection, range from banshee/viking
    }

    private float getEmpValue(Unit enemy) {
        float shieldValue = Math.min(100, enemy.getShield().orElse(0f)) * getShieldMultiple(enemy);
        float energyValue = Math.min(100, enemy.getEnergy().orElse(0f)) * getEnergyMultiple(enemy);
        return shieldValue + energyValue + getCloakedEmpValue(enemy);
    }

    private float getShieldMultiple(Unit enemy) {
        return (UnitUtils.isStructure(enemy.getType()) && !UnitUtils.canAttack(enemy)) ? 0.15f : 1f;
    }

    private float getEnergyMultiple(Unit enemy) {
        switch ((Units)enemy.getType()) {
            case ZERG_VIPER: case ZERG_INFESTOR_BURROWED: case ZERG_INFESTOR:
            case TERRAN_GHOST: case TERRAN_BANSHEE: case PROTOSS_HIGH_TEMPLAR:
            case PROTOSS_ORACLE: case PROTOSS_MOTHERSHIP:
                return 3;
            case ZERG_QUEEN: case TERRAN_MEDIVAC: case PROTOSS_SENTRY: case TERRAN_RAVEN:
            case TERRAN_ORBITAL_COMMAND: case PROTOSS_PHOENIX:
                return 1.75f;
            case PROTOSS_NEXUS:
                return 1;
            case ZERG_OVERSEER: case ZERG_OVERSEER_SIEGED:
                return 0;
            case PROTOSS_SHIELD_BATTERY:
                return enemy.getBuffs().contains(Buffs.BATTERY_OVERCHARGE) ? 0 : 2.5f;
        }
        return 0;
    }

    private float getCloakedEmpValue(Unit enemy) {
        switch ((Units)enemy.getType()) {
            case PROTOSS_DARK_TEMPLAR: case TERRAN_BANSHEE: case TERRAN_GHOST:
                return enemy.getCloakState().stream().anyMatch(state -> state == CloakState.CLOAKED) ? GhostBasic.getEmpThreshold() : 0;
        }
        return 0;
    }

    private boolean isUnpoweredCannon(Unit enemy) {
        return unitType == Units.PROTOSS_PHOTON_CANNON &&
                !UnitUtils.isSnapshot(enemy) &&
                !enemy.getPowered().orElse(true);
    }

}
