package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;
import io.reactivex.annotations.Nullable;

import java.util.Comparator;
import java.util.Set;

public class ScvAttackerBunkerDefense extends ScvAttacker {
    public static final Set<Units> ENEMY_TYPES = Set.of(Units.TERRAN_MARINE, Units.TERRAN_MARAUDER, Units.TERRAN_SCV, Units.TERRAN_BUNKER);

    @Nullable
    private UnitInPool targetUip;

    public ScvAttackerBunkerDefense(Unit unit) {
        super(unit);
    }

    public ScvAttackerBunkerDefense(UnitInPool uip) {
        super(uip);
    }

    @Override
    public void onStep() {
        targetUip = getProxyBunkerPriorityTarget();
        super.onStep();
    }

    @Override
    public void onArrival() {

    }

    @Override
    protected void setTargetPos() {
        if (targetUip != null) {
            targetPos = targetUip.unit().getPosition().toPoint2d();
            return;
        }

        super.setTargetPos();
    }

    @Override
    public UnitInPool selectTarget() {
        return (targetUip != null) ? targetUip : super.selectTarget();
    }

    @Nullable
    public UnitInPool getProxyBunkerPriorityTarget() {
        return UnitUtils.getVisibleEnemyUnitsOfType(ENEMY_TYPES).stream()
                .filter(u -> UnitUtils.getDistance(u.unit(), PosConstants.BUNKER_NATURAL) < 15 ||
                        UnitUtils.isInMyMainOrNat(u.unit()))
                .max(Comparator.comparing(u -> getTargetValue(u)))
                .orElse(null);

    }


    //find based on how dangerous the unit type is and its remaining hp
    private float getTargetValue(UnitInPool target) {
        float value;
        switch ((Units)target.unit().getType()) {
            case TERRAN_MARINE: case TERRAN_MARAUDER:
                value = 6f;
                break;
            case TERRAN_SCV:
                value = 2f;
                break;
            default: //TERRAN_BUNKER
                value = 1f;
        }
        value /= target.unit().getHealth().orElse(100f);
        return value;
    }
}
