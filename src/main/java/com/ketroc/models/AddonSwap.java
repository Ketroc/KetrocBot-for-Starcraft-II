package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.query.QueryBuildingPlacement;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.ketroc.purchases.PurchaseStructure;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.purchases.PurchaseStructureMorph;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.UnitUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AddonSwap {
    public UnitInPool newStructure;
    public UnitInPool addOnBuildingStructure;
    public Point2d addOnBuildingStructurePos;
    public UnitInPool addOn;
    public Units newStructureType;
    public Point2d newStructurePos;
    public boolean removeMe;

    public AddonSwap(UnitInPool addOnBuildingStructure, Abilities addOnMorph, Units newStructure) {
        this(addOnBuildingStructure, addOnMorph, newStructure, null);
    }

    public AddonSwap(UnitInPool addOnBuildingStructure, Abilities addOnMorph, Units newStructure, UnitInPool scv) {
        this(addOnBuildingStructure, addOnMorph, newStructure,
                findNewStructurePos(addOnBuildingStructure.unit().getPosition().toPoint2d()), scv);
    }


    public AddonSwap(UnitInPool addOnBuildingStructure, Abilities addOnMorph, Units newStructureType, Point2d newStructurePos, UnitInPool scv) {
        this.addOnBuildingStructure = addOnBuildingStructure;
        this.newStructureType = newStructureType;
        this.newStructurePos = newStructurePos;
        this.addOnBuildingStructurePos = addOnBuildingStructure.unit().getPosition().toPoint2d();

        //build addon
        KetrocBot.purchaseQueue.addFirst(new PurchaseStructureMorph(addOnMorph, addOnBuildingStructure));
        //build new structure
        if (scv == null || !scv.isAlive()) {
            KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(newStructureType, newStructurePos));
        } else {
            KetrocBot.purchaseQueue.addFirst(new PurchaseStructure(scv.unit(), newStructureType, newStructurePos));
            ActionHelper.unitCommand(scv.unit(), Abilities.MOVE, newStructurePos, false);
            UnitUtils.patrolInPlace(scv.unit(), newStructurePos);
        }
    }

    public void onStep() {
        //set newStructure when its construction begins
        if (newStructure == null) {
            Bot.OBS.getUnits(Alliance.SELF, u -> u.unit().getType() == newStructureType && UnitUtils.getDistance(u.unit(), newStructurePos) < 1).stream()
                    .findFirst()
                    .ifPresent(structure -> newStructure = structure);
        }

        //set addOn when its construction begins
        if (addOn == null) {
            addOnBuildingStructure.unit().getAddOnTag().ifPresent(tag -> addOn = Bot.OBS.getUnit(tag));
        }

        //swap when all construction is complete
        if (newStructure != null && newStructure.unit().getBuildProgress() == 1 &&
                addOn != null && addOn.unit().getBuildProgress() == 1) {
            //lift
            lift(newStructure, newStructurePos);
            lift(addOnBuildingStructure, addOnBuildingStructurePos);

            //move and land
            land(newStructure, addOnBuildingStructurePos);
            land(addOnBuildingStructure, newStructurePos);

            //if swap is complete
            if (!newStructure.unit().getFlying().orElse(true) &&
                    !addOnBuildingStructure.unit().getFlying().orElse(true) &&
                    UnitUtils.getDistance(newStructure.unit(), addOnBuildingStructurePos) < 1 &&
                    UnitUtils.getDistance(addOnBuildingStructure.unit(), newStructurePos) < 1) {
                removeMe = true;
            }
        }
    }

    private void land(UnitInPool structure, Point2d structurePos) {
        if (UnitUtils.getOrder(structure.unit()) == null &&
                structure.unit().getFlying().get()) {
            ActionHelper.unitCommand(structure.unit(), Abilities.LAND, structurePos, false);
        }
    }

    private void lift(UnitInPool structure, Point2d structurePos) {
        if (UnitUtils.getOrder(structure.unit()) == null &&
                !structure.unit().getFlying().get() &&
                UnitUtils.getDistance(structure.unit(), structurePos) < 1) {
            ActionHelper.unitCommand(structure.unit(), Abilities.LIFT, false);
        }
    }

    //check above, below, and left of addonStructure to build
    private static Point2d findNewStructurePos(Point2d addOnStructurePos) {
        Point2d[] pArray = {Point2d.of(addOnStructurePos.getX()-3, addOnStructurePos.getY()),
                Point2d.of(addOnStructurePos.getX(), addOnStructurePos.getY()+3),
                Point2d.of(addOnStructurePos.getX(), addOnStructurePos.getY()-3)};
        List<QueryBuildingPlacement> queryList = Arrays.stream(pArray)
                .map(p -> QueryBuildingPlacement.placeBuilding().useAbility(Abilities.BUILD_BARRACKS).on(p).build())
                .collect(Collectors.toList());
        List<Boolean> placement = Bot.QUERY.placement(queryList);
        for (int i=0; i<placement.size(); i++) {
            if (placement.get(i)) {
                return pArray[i];
            }
        }
        return null; //no available position next to addOnBuildingStructure
    }
}
