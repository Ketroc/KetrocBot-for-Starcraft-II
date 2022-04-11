package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.strategies.Strategy;

public class EnemyMappingEffect extends EnemyMapping {
    public EnemyMappingEffect(EffectLocations effect) {
        unitType = Units.INVALID;
        isEffect = true;
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
                detectRange = 0.5f + Strategy.STATIONARY_KITING_BUFFER;
                threatLevel = 200;
                airAttackRange = 0.5f + Strategy.STATIONARY_KITING_BUFFER;
                groundAttackRange = 0.5f + Strategy.STATIONARY_KITING_BUFFER;
                airDamage = 60;
                groundDamage = 60;
                isPersistentDamage = true;
                break;
            case NUKE_PERSISTENT:
                isDetector = true;
                detectRange = 8 + Strategy.STATIONARY_KITING_BUFFER + 2; //additional 2 to make room for all units to get out of range
                threatLevel = 200;
                airAttackRange = 8 + Strategy.STATIONARY_KITING_BUFFER + 2; //additional 2 to make room for all units to get out of range
                groundAttackRange = airAttackRange;
                airDamage = 300;
                groundDamage = 300;
                isPersistentDamage = true;
                break;
            case PSI_STORM_PERSISTENT:
                isDetector = true;
                detectRange = effect.getRadius().get() + Strategy.STATIONARY_KITING_BUFFER;
                threatLevel = 200;
                airAttackRange = effect.getRadius().get() + Strategy.STATIONARY_KITING_BUFFER;
                groundAttackRange = airAttackRange;
                groundDamage = 80;
                airDamage = 80;
                isPersistentDamage = true;
                break;
        }
        calcMaxRange(); //largest range of airattack, detection, range from banshee/viking
    }

}
