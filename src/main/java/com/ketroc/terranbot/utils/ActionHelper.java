package com.ketroc.terranbot.utils;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Base;

import java.util.List;

public class ActionHelper {
    public static void giveScvCommand(Unit scv, Abilities ability) {
        Base.releaseMineralScv(scv);
        Bot.ACTION.unitCommand(scv, ability, false);
    }

    public static void giveScvCommand(Unit scv, Abilities ability, Point2d pos) {
        Base.releaseMineralScv(scv);
        Bot.ACTION.unitCommand(scv, ability, pos, false);
    }

    public static void giveScvCommand(Unit scv, Abilities ability, Unit targetUnit) {
        Base.releaseMineralScv(scv);
        Bot.ACTION.unitCommand(scv, ability, targetUnit, false);
    }

    public static void giveScvCommand(List<Unit> scvs, Abilities ability) {
        scvs.forEach(scv -> Base.releaseMineralScv(scv));
        Bot.ACTION.unitCommand(scvs, ability, false);
    }

    public static void giveScvCommand(List<Unit> scvs, Abilities ability, Point2d pos) {
        scvs.forEach(scv -> Base.releaseMineralScv(scv));
        Bot.ACTION.unitCommand(scvs, ability, pos, false);
    }
}
