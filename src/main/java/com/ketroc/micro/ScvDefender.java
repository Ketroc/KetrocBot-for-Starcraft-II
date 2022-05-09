package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.gamestate.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;

public class ScvDefender extends Scv {
    UnitInPool centerMineral;

    public ScvDefender(Unit unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
    }

    public ScvDefender(UnitInPool unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
    }

    @Override
    public void onStep() {
        if (!isAlive()) {
            removeMe = true;
            return;
        }

        updateTargetPos();

        //return cargo
        if (UnitUtils.isCarryingResources(unit.unit())) {
            ActionHelper.unitCommand(unit.unit(), Abilities.HARVEST_RETURN, false);
            return;
        }

        //mineral-walk back when on weapon cooldown
        if (!UnitUtils.isWeaponAvailable(unit.unit())) {
            if (getCenterMineral() != null && !isTargettingUnit(getCenterMineral())) {
                ActionHelper.unitCommand(unit.unit(), Abilities.SMART, getCenterMineral(), false);
            }
            return;
        }

        //when in attack range, attack-move
        if (!getScvAttackTargets().isEmpty()) {
            if (UnitUtils.getOrder(unit.unit()) != Abilities.ATTACK) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, getLeadEnemyWorkerPos(), false);
            }
            return;
        }

        //when low hp, mineral-walk back and release for mining
        if (getCenterMineral() != null && unit.unit().getHealth().orElse(45f) <= 15) {
            ActionHelper.unitCommand(unit.unit(), Abilities.SMART, getCenterMineral(), false);
            removeMe = true;
            return;
        }

        //when out of range, attack-move
        if (UnitUtils.getOrder(unit.unit()) != Abilities.ATTACK) {
            ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, getLeadEnemyWorkerPos(), false);
        }
        return;
    }

    private Point2d getLeadEnemyWorkerPos() {
        return UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.WORKER_TYPE).stream()
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), PosConstants.myMineralPos)))
                .map(enemy -> enemy.unit().getPosition().toPoint2d())
                .orElse(null);
    }

    protected Unit getCenterMineral() {
        if (centerMineral == null || !centerMineral.isAlive()) {
            setCenterMineral();
        }
        return (centerMineral == null) ? null : centerMineral.unit();
    }

    protected void setCenterMineral() {
        centerMineral = Bot.OBS.getUnits(Alliance.NEUTRAL, u -> UnitUtils.MINERAL_NODE_TYPE.contains(u.unit().getType()) &&
                        u.unit().getDisplayType() == DisplayType.VISIBLE)
                .stream()
                .min(Comparator.comparing(mineral -> UnitUtils.getDistance(mineral.unit(), GameCache.baseList.get(0).getResourceMidPoint())))
                .orElse(null);
    }

    protected List<UnitInPool> getScvAttackTargets() {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.canAttack(enemy.unit()) &&
                UnitUtils.getDistance(unit.unit(), enemy.unit()) <= enemy.unit().getRadius() + unit.unit().getRadius() + 0.3f);
    }

    protected List<UnitInPool> getScvRepairTargets() {
        return Bot.OBS.getUnits(Alliance.SELF, worker ->
                worker.unit().getType() == Units.TERRAN_SCV &&
                worker.unit().getHealth().orElse(45f) < 45f &&
                UnitUtils.getDistance(unit.unit(), worker.unit()) <= worker.unit().getRadius() + unit.unit().getRadius() + 0.2f);
    }
}
