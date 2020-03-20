package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Strategy;
import com.ketroc.terranbot.UnitUtils;

public class EnemyUnit {
    public float x;
    public float y;
    public boolean isAir;
    public boolean isDetector;
    public float detectRange;
    public float airAttackRange;
    public byte threatLevel;

    public EnemyUnit(Unit enemy) {
        x = enemy.getPosition().getX();
        y = enemy.getPosition().getY();
        isAir = enemy.getFlying().orElse(false);
        threatLevel = getThreatValue((Units)enemy.getType());
        detectRange = enemy.getDetectRange().orElse(0f);
        isDetector = detectRange > 0f;
        detectRange +=  Strategy.KITING_BUFFER;

        airAttackRange = UnitUtils.getAirAttackRange(enemy) + Strategy.KITING_BUFFER;
        switch ((Units)enemy.getType()) {
            case PROTOSS_PHOENIX:
                airAttackRange += 2; //hack to assume enemy has its range upgrade since enemy upgrades cannot be checked
                break;
            case TERRAN_MISSILE_TURRET: case TERRAN_AUTO_TURRET: case ZERG_HYDRALISK: //hack to assume enemy has its range upgrade since enemy upgrades cannot be checked
                airAttackRange++;
                break;
            case TERRAN_MARINE: case PROTOSS_SENTRY: case PROTOSS_HIGH_TEMPLAR: //lessen buffer on units banshees should kite anyhow
                airAttackRange -= 0.5;
                break;
        }
    }

    public static byte getThreatValue(Units unitType) {
        switch (unitType) {
            case TERRAN_MARINE:
                return 2;
            case TERRAN_MISSILE_TURRET:
                return 8;
            case TERRAN_BUNKER: //TODO: what to do with that?
                return 4;
            case TERRAN_VIKING_FIGHTER:
                return 3;
            case TERRAN_LIBERATOR:
                return 6;
            case TERRAN_GHOST:
                return 4;
            case TERRAN_AUTO_TURRET:
                return 6;
            case TERRAN_CYCLONE:
                return 5;
            case TERRAN_THOR:
                return 14;
            case TERRAN_THOR_AP:
                return 6;
            case TERRAN_WIDOWMINE_BURROWED:
                return 0; //TODO: what to do with that?
            case TERRAN_BATTLECRUISER:
                return 8;
        }
        return 0;
    }
}
