package com.ketroc.micro;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.ActionHelper;

public class Hellbat extends BasicUnitMicro {
    private Unit closestEnemyThreat;
    private boolean doStutterForward;

    public Hellbat(Unit unit) {
        super(unit, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        //remove if dead or transformed to hellion
        if (!isAlive() || unit.unit().getType() == Units.TERRAN_HELLION) {
            onDeath();
            return;
        }
        closestEnemyThreat = getClosestEnemyThreatToGround(); // FIXME: this method seems marine-specific (assumes hellbats attack air -- used to keep hellbats and ghosts together???)
        doStutterForward = doStutterForward(unit.unit(), closestEnemyThreat);
        setTargetPos();
        super.onStep();
    }

    @Override
    public boolean isSafe(Point2d pos) {
        return doStutterForward && !isInSplashDamage(pos);
    }

    @Override
    protected void setTargetPos() {
        //if none, then set to attackGroundPos
        if (closestEnemyThreat != null) {
            targetPos = closestEnemyThreat.getPosition().toPoint2d();
        }
        else if (ArmyManager.attackGroundPos == null) {
            setFinishHimTarget();
        }
        else {
            targetPos = ArmyManager.attackGroundPos;
        }
    }

    public void morph() {
        morph(false);
    }

    public void morph(boolean doRelease) {
        Abilities morphAbility = unit.unit().getType() == Units.TERRAN_HELLION_TANK ? Abilities.MORPH_HELLION : Abilities.MORPH_HELLBAT;
        ActionHelper.unitCommand(unit.unit(), morphAbility, false);
        removeMe = doRelease;
    }


    public static boolean isAnyMorphing() {
        return UnitMicroList.unitMicroList.stream()
                .anyMatch(hellbat -> hellbat instanceof Hellbat &&
                        hellbat.isMorphing());
    }
}
