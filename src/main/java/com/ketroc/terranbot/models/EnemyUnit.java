package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Strategy;
import com.ketroc.terranbot.UnitUtils;

public class EnemyUnit {
    public float x;
    public float y;
    public boolean isAir;
    public boolean isDetector;
    public boolean isEffect;
    public float detectRange;
    public float airAttackRange;
    public byte threatLevel;
    public float maxRange;

    public EnemyUnit(Unit enemy) {
        x = enemy.getPosition().getX();
        y = enemy.getPosition().getY();
        isAir = enemy.getFlying().orElse(false);
        threatLevel = getThreatValue((Units)enemy.getType());
        detectRange = enemy.getDetectRange().orElse(0f);
        isDetector = detectRange > 0f;
        detectRange +=  Strategy.KITING_BUFFER + 1;

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
            case PROTOSS_TEMPEST: case TERRAN_VIKING_FIGHTER:
                airAttackRange = 6; //hack so my vikings will fight, but this hack is bad for banshees
        }
        calcMaxRange();
    }

    public EnemyUnit(EffectLocations effect) {
        isEffect = true;
        Point2d position = effect.getPositions().iterator().next();
        x = position.getX();
        y = position.getY();
        switch ((Effects)effect.getEffect()) {
            case SCANNER_SWEEP:
                isDetector = true;
                detectRange = 13f;
                break;
            case RAVAGER_CORROSIVE_BILE_CP:
                isDetector = true;
                detectRange = 3.5f + Strategy.KITING_BUFFER; //actual range is 0.5f but effect disappears prior to it landing
                threatLevel = 20;
                airAttackRange = 3.5f + Strategy.KITING_BUFFER; //actual range is 0.5f but effect disappears prior to it landing
                break;
        }
        calcMaxRange();
    }

    private void calcMaxRange() {
        //set the largest range of airattack, detection, range from banshee/viking
        maxRange = Math.max(airAttackRange, Math.max(detectRange, (isAir) ? Strategy.VIKING_RANGE : Strategy.BANSHEE_RANGE));
    }

    public static byte getThreatValue(Units unitType) {
        switch (unitType) {
            case TERRAN_MARINE:
                return 3;
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
            case PROTOSS_SENTRY:
                return 2;
            case PROTOSS_HIGH_TEMPLAR:
                return 2;
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
                return 2;
            case PROTOSS_TEMPEST:
                return 3;
            case PROTOSS_PHOTON_CANNON:
                return 5;
            case ZERG_HYDRALISK:
                return 4;
            case ZERG_QUEEN:
                return 3;
            case ZERG_MUTALISK:
                return 2;
            case ZERG_CORRUPTOR:
                return 2;
            case ZERG_SPORE_CRAWLER:
                return 5;
        }
        return 0;
    }
}
