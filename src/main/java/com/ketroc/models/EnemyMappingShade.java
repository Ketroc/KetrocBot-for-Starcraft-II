package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.Strategy;

public class EnemyMappingShade extends EnemyMapping {

    public EnemyMappingShade(Unit shade) {
        unitType = Units.PROTOSS_ADEPT_PHASE_SHIFT;
        x = shade.getPosition().getX();
        y = shade.getPosition().getY();
        UnitTypeData shadeData = Bot.OBS.getUnitTypeData(false).get(Units.PROTOSS_ADEPT_PHASE_SHIFT);
        UnitTypeData adeptData = Bot.OBS.getUnitTypeData(false).get(Units.PROTOSS_ADEPT);
        supply = adeptData.getFoodRequired().orElse(0f);
        canMove = shadeData.getMovementSpeed().orElse(0f) > 0;
        threatLevel = getThreatValue(Units.PROTOSS_ADEPT);
        groundDamage = (int)adeptData.getWeapons().iterator().next().getDamage();
        sightRange = shadeData.getSightRange().orElse(0f);
        groundAttackRange = adeptData.getWeapons().iterator().next().getRange() + Strategy.KITING_BUFFER;
        pfTargetLevel = 0;
        isArmy = true;
        calcMaxRange(); //largest range of airattack, detection, range from banshee/viking
    }
}
