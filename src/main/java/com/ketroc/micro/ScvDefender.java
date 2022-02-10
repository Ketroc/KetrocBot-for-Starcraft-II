package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.managers.ArmyManager;
import com.ketroc.models.MineralPatch;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.LocationConstants;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.List;

public class ScvDefender extends Scv {
    Unit centerMineral;

    public ScvDefender(Unit unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
        setCenterMineral();
    }

    public ScvDefender(UnitInPool unit) {
        super(unit, ArmyManager.attackGroundPos, MicroPriority.DPS);
        setCenterMineral();
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
            if (!isTargettingUnit(centerMineral)) {
                ActionHelper.unitCommand(unit.unit(), Abilities.SMART, centerMineral, false);
            }
            return;
        }

//        //when out of attack range, repair instead
        List<UnitInPool> attackTargets = getScvAttackTargets();
//        List<UnitInPool> repairTargets = getScvRepairTargets();
//        if (attackTargets.isEmpty() && !repairTargets.isEmpty()) {
//            repairTargets.stream()
//                    .min(Comparator.comparing(target -> UnitUtils.getDistance(unit.unit(), target.unit())))
//                    .ifPresent(target -> ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_REPAIR, target.unit(), false));
//            return;
//        }

        //when in attack range, attack-move
        if (!attackTargets.isEmpty()) {
            if (UnitUtils.getOrder(unit.unit()) != Abilities.ATTACK) {
                ActionHelper.unitCommand(unit.unit(), Abilities.ATTACK, getLeadEnemyWorkerPos(), false);
            }
            return;
        }

        //when low hp, mineral-walk back and release for mining
        if (unit.unit().getHealth().orElse(45f) <= 15) {
            ActionHelper.unitCommand(unit.unit(), Abilities.SMART, centerMineral, false);
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
                .min(Comparator.comparing(enemy -> UnitUtils.getDistance(enemy.unit(), LocationConstants.myMineralPos)))
                .map(enemy -> enemy.unit().getPosition().toPoint2d())
                .orElse(null);
    }

    protected void setCenterMineral() {
        centerMineral = GameCache.baseList.get(0).getMineralPatches().stream()
                .map(MineralPatch::getNode)
                .min(Comparator.comparing(node -> UnitUtils.getDistance(node, GameCache.baseList.get(0).getResourceMidPoint())))
                .orElse(null);
    }

    protected List<UnitInPool> getScvAttackTargets() {
        return Bot.OBS.getUnits(Alliance.ENEMY, enemy -> UnitUtils.canAttack(enemy.unit().getType()) &&
                UnitUtils.getDistance(unit.unit(), enemy.unit()) <= enemy.unit().getRadius() + unit.unit().getRadius() + 0.3f);
    }

    protected List<UnitInPool> getScvRepairTargets() {
        return Bot.OBS.getUnits(Alliance.SELF, worker ->
                worker.unit().getType() == Units.TERRAN_SCV &&
                worker.unit().getHealth().orElse(45f) < 45f &&
                UnitUtils.getDistance(unit.unit(), worker.unit()) <= worker.unit().getRadius() + unit.unit().getRadius() + 0.2f);
    }
}
