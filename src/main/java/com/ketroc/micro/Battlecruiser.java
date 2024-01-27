package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.utils.*;

public class Battlecruiser extends BasicUnitMicro {
    public static final float RADIUS = 1.25f;
    public static final float ATTACK_RANGE = 7.5f;
    public static final long COOLDOWN_JUMP = 1590;
    public static final long COOLDOWN_YAMATO = 1590;

    protected long prevAttackFrame;
    protected Point2d posMoveTo;
    protected long prevJumpFrame;

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
        return MyUnitAbilities.isAbilityAvailable(unit.unit(), Abilities.EFFECT_YAMATO_GUN);
    }

    protected void yamato(Unit target) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_YAMATO_GUN, target, false);
    }

    protected boolean safeJump(Point2d targetPos) {
        //jump directly to targetPos
        if (isSafe(targetPos)) {
            jump(targetPos);
            return true;
        }

        //targetPos unsafe so jump nearby
        Point2d safePos = findDetourPos();
        if (targetPos.distance(safePos) < 25) {
            jump(safePos);
            return true;
        }

        //don't jump because entire target area is unsafe
        return false;
    }

    protected void jump(Point2d jumpPos) {
        ActionHelper.unitCommand(unit.unit(), Abilities.EFFECT_TACTICAL_JUMP, jumpPos, false);
        prevJumpFrame = Time.nowFrames();
    }

    protected long jumpCooldownRemaining() {
        return prevJumpFrame + COOLDOWN_JUMP - Time.nowFrames();
    }

    protected boolean isAttackStep() {
        return prevAttackFrame + 12 < Bot.OBS.getGameLoop();
    }

    protected boolean isCasting() {
        return ActionIssued.getCurOrder(unit).stream()
                .anyMatch(order -> order.ability == Abilities.EFFECT_YAMATO_GUN ||
                        order.ability == Abilities.EFFECT_TACTICAL_JUMP);
    }

    protected boolean needRepairs() {
        return unit.unit().getHealth().orElse(400f) < 200;
    }
}
