package com.ketroc.utils;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.managers.FutureDamageMap;

import java.util.List;
import java.util.Set;

public class ActionHelper {

    public static void unitCommand(Unit unit, Abilities ability, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            ActionIssued.add(unit.getTag(), ability);
        }
        Bot.ACTION.unitCommand(unit, ability, isQueued);
    }

    public static void unitCommand(Unit unit, Abilities ability, Point2d targetPos, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            if (ability.toString().contains("BUILD")) {
                System.out.println("Caching " + ability + " for " + unit.getType());
            }
            ActionIssued.add(unit.getTag(), ability, targetPos);
        }
        Bot.ACTION.unitCommand(unit, ability, targetPos, isQueued);
    }

    public static void unitCommand(Unit unit, Abilities ability, Unit targetUnit, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            ActionIssued.add(unit.getTag(), ability, targetUnit);
        }
        if (!isQueued && doAddFutureDamage(ability)) {
            addToFutureDamage(Bot.OBS.getUnit(unit.getTag()), Bot.OBS.getUnit(targetUnit.getTag()), ability);
        }
        Bot.ACTION.unitCommand(unit, ability, targetUnit, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Abilities ability, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Abilities ability, Point2d targetPos, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability, targetPos);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, targetPos, isQueued);
    }

    public static void unitCommand(List<Unit> unitList, Abilities ability, Unit targetUnit, boolean isQueued) {
        if (unitList.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitList.forEach(unit -> {
                ActionIssued.add(unit.getTag(), ability, targetUnit);
            });
        }
        if (!isQueued && doAddFutureDamage(ability)) {
            unitList.forEach(unit -> {
                addToFutureDamage(Bot.OBS.getUnit(unit.getTag()), Bot.OBS.getUnit(targetUnit.getTag()), ability);
            });
        }
        Bot.ACTION.unitCommand(unitList, ability, targetUnit, isQueued);
    }

    public static void unitCommand(Tag unitTag, Abilities ability, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability);
        }
        Bot.ACTION.unitCommand(unitTag, ability, isQueued);
    }

    public static void unitCommand(Tag unitTag, Abilities ability, Point2d targetPos, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability, targetPos);
        }
        Bot.ACTION.unitCommand(unitTag, ability, targetPos, isQueued);
    }

    public static void unitCommand(Tag unitTag, Abilities ability, Unit targetUnit, boolean isQueued) {
        if (Launcher.isRealTime && !isQueued) {
            ActionIssued.add(unitTag, ability, targetUnit);
        }
        if (!isQueued && doAddFutureDamage(ability)) {
            addToFutureDamage(Bot.OBS.getUnit(unitTag), Bot.OBS.getUnit(targetUnit.getTag()), ability);
        }
        Bot.ACTION.unitCommand(unitTag, ability, targetUnit, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Abilities ability, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Abilities ability, Point2d targetPos, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability, targetPos);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, targetPos, isQueued);
    }

    public static void unitCommand(Set<Tag> unitSet, Abilities ability, Unit targetUnit, boolean isQueued) {
        if (unitSet.isEmpty()) {
            return;
        }
        if (Launcher.isRealTime && !isQueued) {
            unitSet.forEach(unitTag -> {
                ActionIssued.add(unitTag, ability, targetUnit);
            });
        }
        if (!isQueued && doAddFutureDamage(ability)) {
            unitSet.forEach(tag -> {
                addToFutureDamage(Bot.OBS.getUnit(tag), Bot.OBS.getUnit(targetUnit.getTag()), ability);
            });
        }
        Bot.ACTION.unitCommand(unitSet, ability, targetUnit, isQueued);
    }

    private static boolean doAddFutureDamage(Abilities ability) {
        return switch(ability) {
            case EFFECT_YAMATO_GUN -> true;
            default -> false;
        };
    }

    private static void addToFutureDamage(UnitInPool myUip, UnitInPool enemyUip, Abilities ability) {
        FutureDamageMap.addToMap(myUip, enemyUip, ability);
    }
}
