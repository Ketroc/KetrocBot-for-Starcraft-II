package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;

public class EnemyMappingFungal extends EnemyMapping {

    public EnemyMappingFungal(Point2d pos) {
        unitType = Units.INVALID;
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
}
