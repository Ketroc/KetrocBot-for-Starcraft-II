package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

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
        long gameFrame = Bot.OBS.getGameLoop() + (long)(delaySeconds * 22.4);
        return gameFrame - (gameFrame % Strategy.SKIP_FRAMES);
    }

    public boolean executeAction() {
        //if unit is dead, or targetUnit is dead or in fog, cancel action
        if (!unit.isAlive() || (targetUnit != null && (!targetUnit.isAlive() || targetUnit.getLastSeenGameLoop() != Bot.OBS.getGameLoop()))) {
            return false;
        }
        if (targetUnit == null && targetPos == null) {
            Bot.ACTION.unitCommand(unit.unit(), ability, false);
        }
        else if (targetUnit == null) {
            Bot.ACTION.unitCommand(unit.unit(), ability, targetPos, false);
        }
        else { //targetPos == null
            Bot.ACTION.unitCommand(unit.unit(), ability, targetUnit.unit(), false);
        }
        return true;
    }

    // **************************************
    // ********** STATIC METHODS ************
    // **************************************
    public static void onStep() {
        delayedActions.stream()
                .filter(action -> action.gameFrame == Bot.OBS.getGameLoop())
                .forEach(delayedAction -> {
                    if (!delayedAction.executeAction()) System.out.println("Action not performed: " + delayedAction.toString());
                });
        delayedActions.removeIf(action -> action.gameFrame == Bot.OBS.getGameLoop());
    }

    public static long nextFrame() {
        return Bot.OBS.getGameLoop() + Strategy.SKIP_FRAMES;
    }

}
