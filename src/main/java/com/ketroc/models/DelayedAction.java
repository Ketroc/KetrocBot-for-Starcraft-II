package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.launchers.Launcher;
import com.ketroc.utils.ActionHelper;
import com.ketroc.utils.Print;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.ArrayList;
import java.util.List;

public class DelayedAction { //TODO: add functionality for List of units if required
    public static List<DelayedAction> delayedActions = new ArrayList<>();

    long gameFrame;
    Abilities ability;
    UnitInPool unit;
    Point2d targetPos;
    UnitInPool targetUnit;

    // **************************************
    // *********** CONSTRUCTORS *************
    // **************************************

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit) {
        this.gameFrame = getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
    }

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit, UnitInPool targetUnit) {
        this.gameFrame = getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
        this.targetUnit = targetUnit;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit, UnitInPool targetUnit) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
        this.targetUnit = targetUnit;
    }

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit, Point2d targetPos) {
        this.gameFrame = getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
        this.targetPos = targetPos;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit, Point2d targetPos) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
        this.targetPos = targetPos;
    }





    // **************************************
    // ************* METHODS ****************
    // **************************************

    public long getDelayedGameFrame(int delaySeconds) {
        if (delaySeconds == -1) {
            return nextFrame();
        }
        long gameFrame = Time.nowFrames() + Time.toFrames(delaySeconds);
        return gameFrame - (gameFrame % Launcher.STEP_SIZE);
    }

    public boolean executeAction() {
        //if unit is dead, or targetUnit is dead or in fog, cancel action
        if (!unit.isAlive() || (targetUnit != null && (!targetUnit.isAlive() || UnitUtils.isInFogOfWar(targetUnit)))) {
            return false;
        }
        if (targetUnit == null && targetPos == null) {
            ActionHelper.unitCommand(unit.unit(), ability, false);
        }
        else if (targetUnit == null) {
            ActionHelper.unitCommand(unit.unit(), ability, targetPos, false);
        }
        else { //targetPos == null
            ActionHelper.unitCommand(unit.unit(), ability, targetUnit.unit(), false);
        }
        return true;
    }

    // **************************************
    // ********** STATIC METHODS ************
    // **************************************
    public static void onStep() {
        delayedActions.stream()
                .filter(action -> Time.nowFrames() >= action.gameFrame)
                .forEach(delayedAction -> {
                    if (!delayedAction.executeAction()) Print.print("Action not performed: " + delayedAction.toString());
                });
        delayedActions.removeIf(action -> Time.nowFrames() >= action.gameFrame);
    }

    public static long nextFrame() {
        return Time.nowFrames() + Launcher.STEP_SIZE;
    }

}
