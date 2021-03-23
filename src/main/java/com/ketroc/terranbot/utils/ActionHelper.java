package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Base;

import java.util.List;
import java.util.Set;

public class ActionHelper {

    public static void unitCommand(Unit unit, Ability ability, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unit.getTag(), ability);
        }
        Bot.ACTION.unitCommand(unit, ability, isQueued);
    }

    public static void unitCommand(Unit unit, Ability ability, Point2d targetPos, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unit.getTag(), ability, targetPos);
        }
        Bot.ACTION.unitCommand(unit, ability, targetPos, isQueued);
    }

    public static void unitCommand(Unit unit, Ability ability, Unit targetUnit, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unit.getTag(), ability, targetUnit);
        }
        Bot.ACTION.unitCommand(unit, ability, targetUnit, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Ability ability, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Ability ability, Point2d targetPos, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability, targetPos);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, targetPos, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Ability ability, Unit targetUnit, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability, targetUnit);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, targetUnit, isQueued);
    }

    public static void unitCommand(Tag unitTag, Ability ability, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability);
        }
        Bot.ACTION.unitCommand(unitTag, ability, isQueued);
    }

    public static void unitCommand(Tag unitTag, Ability ability, Point2d targetPos, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability, targetPos);
        }
        Bot.ACTION.unitCommand(unitTag, ability, targetPos, isQueued);
    }

    public static void unitCommand(Tag unitTag, Ability ability, Unit targetUnit, boolean isQueued) {
        if (Bot.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability, targetUnit);
        }
        Bot.ACTION.unitCommand(unitTag, ability, targetUnit, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Ability ability, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Ability ability, Point2d targetPos, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability, targetPos);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, targetPos, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Ability ability, Unit targetUnit, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Bot.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability, targetUnit);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, targetUnit, isQueued);
    }


    public static void giveScvCommand(Unit scv, Abilities ability) {
        Base.releaseMineralScv(scv);
        ActionHelper.unitCommand(scv, ability, false);
    }

    public static void giveScvCommand(Unit scv, Abilities ability, Point2d targetPos) {
        Base.releaseMineralScv(scv);
        ActionHelper.unitCommand(scv, ability, targetPos, false);
    }

    public static void giveScvCommand(Unit scv, Abilities ability, Unit targetUnit) {
        Base.releaseMineralScv(scv);
        ActionHelper.unitCommand(scv, ability, targetUnit, false);
    }

    public static void giveScvCommand(List<Unit> scvs, Abilities ability) {
        scvs.forEach(scv -> Base.releaseMineralScv(scv));
        ActionHelper.unitCommand(scvs, ability, false);
    }

    public static void giveScvCommand(List<Unit> scvs, Abilities ability, Point2d targetPos) {
        scvs.forEach(scv -> Base.releaseMineralScv(scv));
        ActionHelper.unitCommand(scvs, ability, targetPos, false);
    }
}
