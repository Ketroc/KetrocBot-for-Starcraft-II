package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.strategies.Strategy;

public class EnemyMappingParasitic extends EnemyMapping {

    public EnemyMappingParasitic(Unit friendly) {
        unitType = Units.INVALID;
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
}
