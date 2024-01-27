package com.ketroc.micro;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.*;

import java.util.Set;

//travels far distances as a hellion
public class Hellbat_FastTravel extends Hellbat {
    public Hellbat_FastTravel(Unit unit) {
        super(unit);
    }

    @Override
    public void onStep() {
        if (canMorph() && !isMorphing()) {
            if (unit.unit().getType() == Units.TERRAN_HELLION_TANK &&
                    UnitUtils.getDistance(unit.unit(), targetPos) > getToHellionDistance()) {
                morph();
                return;
            }
            if (unit.unit().getType() == Units.TERRAN_HELLION &&
                    UnitUtils.getDistance(unit.unit(), targetPos) < getToHellbatDistance()) {
                morph();
                return;
            }
        }
        super.onStep();
    }

    public boolean canMorph() {
        Set<Abilities> myUnitAbilities = MyUnitAbilities.map.get(unit.getTag());
        return myUnitAbilities != null && myUnitAbilities.stream().anyMatch(ability -> ability.toString().startsWith("MORPH"));
    }

    public static int getToHellionDistance() {
        return (UnitUtils.hasUpgrade(Upgrades.SMART_SERVOS)) ? 40 : 60;
    }

    public static int getToHellbatDistance() {
        return (UnitUtils.hasUpgrade(Upgrades.SMART_SERVOS)) ? 15 : 25;
    }
}
