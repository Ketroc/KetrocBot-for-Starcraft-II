package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.micro.BasicUnitMicro;
import com.ketroc.terranbot.micro.ScvAttackTarget;
import com.ketroc.terranbot.micro.UnitMicroList;
import com.ketroc.terranbot.utils.LocationConstants;
import com.ketroc.terranbot.utils.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/*
    Assign a target in your main or natural, and it will pull the appropriate amount of scvs to kill it
    Scvs will retreat when low hp and get replaced.
    On target death, or if target leaves, scvs free up to be used for mining
 */
public class ScvTarget {
    public static List<ScvTarget> targets = new ArrayList<>();

    public int numScvs;
    public UnitInPool targetUnit;
    public boolean giveUp = false; //TODO: unused

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
                numScvs = 1;
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
            case ZERG_HATCHERY: //TODO: math the answer for numScvs
                if (targetUnit.unit().getBuildProgress() > 0.80) {
                    giveUp = true;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.5) {
                    numScvs = 12;
                }
                else {
                    numScvs = 7;
                }
                break;
        }
    }

    public void cancelTarget() {
        List<ScvAttackTarget> scvs = getScvList();
        Bot.ACTION.sendChat("cancelling " + scvs.size() + "scvs for " + targetUnit.unit().getType(), ActionChat.Channel.BROADCAST);
        scvs.forEach(scvAttackTarget -> scvAttackTarget.remove());
    }

    public List<ScvAttackTarget> getScvList() {
        return UnitMicroList.getUnitSubList(ScvAttackTarget.class).stream()
                .filter(scvAttackTarget -> scvAttackTarget.targetUnit.getTag().equals(targetUnit.getTag()))
                .collect(Collectors.toList());
    }

    public void addScv(UnitInPool scvToAdd) {
        UnitMicroList.add(new ScvAttackTarget(scvToAdd, targetUnit));
    }

    public void removeScv(Tag scvToRemove) {
        List<ScvAttackTarget> scvs = getScvList();
        scvs.stream()
            .filter(scvAttackTarget -> scvAttackTarget.unit.getTag().equals(scvToRemove))
            .findFirst()
            .ifPresent(scvAttackTarget -> scvAttackTarget.remove());
    }

    // ************** STATIC METHODS ******************
    public static void removeDeadTargets() {
        for (int i = 0; i<targets.size(); i++) {
            ScvTarget scvTarget = targets.get(i);
            List<ScvAttackTarget> scvs = scvTarget.getScvList();
            if (!scvTarget.targetUnit.isAlive() || !UnitUtils.isInMyMainOrNat(scvTarget.targetUnit.unit())) {
                scvs.forEach(scvAttackTarget -> {
                    scvAttackTarget.remove();
                });
                targets.remove(i--);
            }
        }
    }

    public static boolean contains(Tag targetTag) {
        return targets.stream().anyMatch(scvTarget -> targetTag.equals(scvTarget.targetUnit.getTag()));
    }

}
