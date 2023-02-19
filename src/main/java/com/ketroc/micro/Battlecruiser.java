package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.ActionIssued;
import com.ketroc.utils.MyUnitAbilities;

public class Battlecruiser extends BasicUnitMicro {

    protected long prevAttackFrame;
    public static long prevYamatoFrame;
    protected Point2d posMoveTo;

    public Battlecruiser(Unit unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    public Battlecruiser(UnitInPool unit, Point2d targetPos, MicroPriority priority) {
        super(unit, targetPos, priority);
    }

    protected boolean isJumpAvailable() {
        return MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP);
    }

    protected boolean isYamatoAvailable() {
        return prevYamatoFrame + 36 < Bot.OBS.getGameLoop() && //1 yamato from any BC every 3 seconds
                MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_YAMATO_GUN);
    }

    protected void yamato(Unit target) {
        prevYamatoFrame = Bot.OBS.getGameLoop();
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_YAMATO_GUN, target, false);
    }

    protected boolean safeJump(Point2d targetPos) {
        //jump directly to targetPos
        if (isSafe(targetPos)) {
            ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP, targetPos, false);
            return true;
        }

        //targetPos unsafe so jump nearby
        Point2d safePos = findDetourPos();
        if (targetPos.distance(safePos) < 25) {
            ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP, safePos, false);
            return true;
        }

        //don't jump because entire target area is unsafe
        return false;
    }

    protected void jump(Point2d jumpPos){
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP, jumpPos, false);
    }


    protected boolean isAttackStep() {
        return prevAttackFrame + 12 < Bot.OBS.getGameLoop();
    }

    protected boolean isCasting() {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(order -> order.ability == Abilities.EFFECT_YAMATO_GUN ||
                        order.ability == Abilities.EFFECT_TACTICAL_JUMP);
    }


}
