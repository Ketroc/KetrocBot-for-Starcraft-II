package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.utils.ActionHelper;
import com.ketroc.terranbot.utils.DebugHelper;
import com.ketroc.terranbot.utils.UnitUtils;

import java.util.Comparator;

/*
    This scv object will attack a target until it dies or flees the main&nat area
    Scv is replaced with a fresh scv when it's hp drops below 10
 */
public class ScvAttackTarget extends Scv {

    public UnitInPool targetUnit;

    public ScvAttackTarget(UnitInPool scv, UnitInPool targetUnit) {
        this(scv.unit(), targetUnit);
    }

    public ScvAttackTarget(Unit scv, UnitInPool targetUnit) {
        super(scv, targetUnit.unit().getPosition().toPoint2d(), MicroPriority.DPS);
        Base.releaseScv(scv);
        this.targetUnit = targetUnit;
    }

    @Override
    public void onStep() {
        DebugHelper.draw3dBox(unit.unit().getPosition().toPoint2d(), Color.GREEN, 0.5f);
        Point2d targetPos = targetUnit.unit().getPosition().toPoint2d();

        //remove this object if enemy is dead or left the base
        if (!targetUnit.isAlive() ||
                (UnitUtils.getDistance(unit.unit(), targetPos) < 1 && UnitUtils.isInFogOfWar(targetUnit)) ||
                !UnitUtils.isInMyMainOrNat(targetPos)) {
            remove();
            return;
        }

        //swap out scv if it gets low in health
        if (!unit.isAlive() || unit.unit().getHealth().orElse(45f) <= 10) {
            UnitInPool newScv = getNewScv();
            if (newScv != null) {
                ActionHelper.unitCommand(unit.unit(), Abilities.STOP, false);
                replaceUnit(newScv);
            }
            else { //remove object if no replacement scv is found
                remove();
                return;
            }
        }

        //attack
        if (!UnitUtils.isInFogOfWar(targetUnit)) {
            if (!isAttackingTarget(targetUnit.getTag())) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, targetUnit.unit(), false);
            }
        }
        else {
            if (!isMovingToTargetPos()) {
                ActionHelper.unitCommand(unit.unit(), Abilities.MOVE, targetPos, false);
            }
        }
    }

    @Override
    public void onArrival() {

    }

    public void remove() {
        removeMe = true;
        if (unit.isAlive()) {
            ActionHelper.unitCommand(unit.unit(), Abilities.STOP, false);
        }
    }

    public static void add(Unit enemyWorker) {
        boolean alreadyExists = UnitMicroList.unitMicroList.stream()
                .anyMatch(unitMicro -> unitMicro instanceof ScvAttackTarget &&
                        ((ScvAttackTarget) unitMicro).targetUnit.getTag().equals(enemyWorker.getTag()));
        if (!alreadyExists) {
            UnitInPool newScv = getNewScv();
            if (newScv != null) {
                UnitMicroList.add(new ScvAttackTarget(newScv, Bot.OBS.getUnit(enemyWorker.getTag())));
            }
        }
    }

    private static UnitInPool getNewScv() {
        return WorkerManager.getAvailableScvs(GameCache.baseList.get(0).getCcPos()).stream()
                .max(Comparator.comparing(scv -> scv.unit().getHealth().orElse(0f)))
                .orElse(null);
    }

    public static boolean contains(Tag targetTag) {
        return UnitMicroList.getUnitSubList(ScvAttackTarget.class).stream()
                .anyMatch(scvAttackTarget -> scvAttackTarget.targetUnit != null && scvAttackTarget.targetUnit.getTag().equals(targetTag));
    }

}
