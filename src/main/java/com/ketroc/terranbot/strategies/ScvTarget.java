package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;

import java.util.ArrayList;
import java.util.List;

public class ScvTarget {
    public static List<ScvTarget> targets = new ArrayList<>();
    public int numScvs;
    public List<UnitInPool> scvs = new ArrayList<>();
    public UnitInPool targetUnit;
    public boolean giveUp = false;

    public ScvTarget(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
        numScvs = 0;
        switch ((Units)targetUnit.unit().getType()) {
            case PROTOSS_PHOTON_CANNON:
                if (targetUnit.unit().getBuildProgress() > 0.65) {
                    giveUp = true;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.5) {
                    numScvs = 7;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.35) {
                    numScvs = 6;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.2) {
                    numScvs = 5;
                }
                else {
                    numScvs = 4;
                }
                break;
            case PROTOSS_PROBE:
                numScvs = 0; //worker defense already puts 1 scv on each probe
                break;
            case PROTOSS_PYLON:
                //send an scv to pylon blocking natural
                //(this gives vision of deep cannons and help free up natural for later defensive PF)
                if (UnitUtils.getDistance(targetUnit.unit(), LocationConstants.baseLocations.get(1)) < 3.5f) {
                    numScvs = 1;
                }
                else {
                    numScvs = 0;
                }
                break;
        }
    }

    public void addScv(UnitInPool scvToAdd) {
        scvs.add(scvToAdd);
        Ignored.add(new IgnoredUnit(scvToAdd.getTag()));
    }

    public void removeScv(UnitInPool scvToRemove) {
        scvs.remove(scvToRemove);
        Ignored.remove(scvToRemove.getTag());
    }

    // ************** STATIC METHODS ******************
    public static void removeDeadTargets() {
        for (int i = 0; i<targets.size(); i++) {
            ScvTarget scvTarget = targets.get(i);
            if (!scvTarget.targetUnit.isAlive() || !UnitUtils.isVisible(scvTarget.targetUnit) ||
                    scvTarget.targetUnit.unit().getPosition().toPoint2d().distance(LocationConstants.baseLocations.get(0)) > 50) {
                if (!scvTarget.scvs.isEmpty()) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvTarget.scvs), Abilities.HARVEST_GATHER, GameCache.defaultRallyNode, false);
                    scvTarget.scvs.stream().forEach(scv -> Ignored.remove(scv.getTag()));
                }
                targets.remove(i--);
            }
        }
    }

}
