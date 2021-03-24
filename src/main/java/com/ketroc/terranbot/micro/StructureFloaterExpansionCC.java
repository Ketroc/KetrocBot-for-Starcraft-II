package com.ketroc.terranbot.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.KetrocBot;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.utils.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StructureFloaterExpansionCC extends StructureFloater {

    public Point2d basePos;

    public StructureFloaterExpansionCC(UnitInPool structure, Point2d targetPos) {
        super(structure, targetPos, true);
        basePos = targetPos;
        doDetourAroundEnemy = true;
    }

    public StructureFloaterExpansionCC(Unit structure, Point2d targetPos) {
        super(Bot.OBS.getUnit(structure.getTag()), targetPos, true);
        basePos = targetPos;
        doDetourAroundEnemy = true;
    }

    @Override
    public void onStep() {
        //landing code
        if (UnitUtils.getDistance(unit.unit(), targetPos) < 13) {
            //if flying
            if (unit.unit().getFlying().orElse(true)) {
                if (UnitUtils.getOrder(unit.unit()) != Abilities.LAND) {
                    if (isBlockedByEnemyCommandStructure()) {
                        Chat.chatWithoutSpam("Recalculating landing pos for offensive PF", 120);
                        Point2d safeLandingPos = calcSafeLandingPos();
                        if (safeLandingPos != null) {
                            targetPos = safeLandingPos;
                            ActionHelper.unitCommand(unit.unit(), Abilities.LAND, targetPos, false);
                            removeCCFromBaseList();
                            return;
                        }
                        else {
                            targetPos = basePos;
                            addCCToBaseList();
                            super.onStep();
                        }
                    }
                    ActionHelper.unitCommand(unit.unit(), Abilities.LAND, basePos, false);
                }
            }
            else if (!unit.unit().getActive().get()) {
                //if landed not on position
                if (targetPos != basePos) {
                    Chat.chatWithoutSpam("CC landed off base position", 180);
                    //end if morphing to PF
                    if (ActionIssued.getCurOrder(unit.unit()).stream().anyMatch(order -> order.ability == Abilities.MORPH_PLANETARY_FORTRESS)) {
                        removeMe = true; //TODO: cancel PF upgrade and move over if enemy command structure dies
                    }
                    //morph to PF
                    else if (!Purchase.isMorphQueued(unit.getTag(), Abilities.MORPH_PLANETARY_FORTRESS)) {
                        KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, unit));
                    }
                }
                else {
                    removeMe = true;
                }
            }
        }
        else {
            super.onStep();
        }
    }

    @Override
    public void onArrival() {
        return;
    }

    private Point2d calcSafeLandingPos() {
        //get position in front of enemy command structure
        Base base = GameCache.baseList.stream()
                .filter(b -> b.getCcPos().distance(basePos) < 1)
                .findFirst().get();
        Point2d outFrontPos = Position.toHalfPoint(
                Position.towards(basePos, base.getResourceMidPoint(), -10));

        //get a list of cc positions sorted by nearest to flying cc
        List<Point2d> landingPosList = Position.getSpiralList(outFrontPos, 7);
        landingPosList = landingPosList.stream()
                //.filter(landingPos -> landingPos.distance(basePos) < 12) //in range for PF to kill enemy command structure
                .filter(landingPos -> UnitUtils.isPlaceable(Units.TERRAN_COMMAND_CENTER, landingPos))
                .filter(landingPos -> InfluenceMaps.getGroundThreatToStructure(Units.TERRAN_BARRACKS, landingPos) < 3) //barracks instead of command center: is a hack to help ignore a portion of the kiting buffer of enemy units
                //.filter(landingPos -> !InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, landingPos))
                .sorted(Comparator.comparing(p -> p.distance(basePos)))
                .collect(Collectors.toList());

        //query closest valid landing position
        if (!landingPosList.isEmpty()) {
            return Position.getFirstPosFromQuery(landingPosList, Abilities.LAND_COMMAND_CENTER);
        }
        return null;
    }

    private void removeCCFromBaseList() {
        GameCache.baseList.stream()
                .filter(base -> basePos.distance(base.getCcPos()) < 1)
                .findFirst()
                .ifPresent(base -> base.setCc(null));
    }

    private void addCCToBaseList() {
        GameCache.baseList.stream()
                .filter(base -> targetPos.distance(base.getCcPos()) < 1)
                .findFirst()
                .ifPresent(base -> base.setCc(unit));
    }

    private boolean isBlockedByEnemyCommandStructure() {
        return !Bot.OBS.getUnits(Alliance.ENEMY, u ->
                UnitUtils.COMMAND_STRUCTURE_TYPE.contains(u.unit().getType()) &&
                !u.unit().getFlying().orElse(true) &&
                UnitUtils.getDistance(u.unit(), basePos) < 2).isEmpty();
    }

    @Override
    public void onDeath() {
        removeMe = true;
    }
}
