package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;
import com.ketroc.terranbot.utils.Time;

import java.util.ArrayList;
import java.util.List;

public class DelayedChat { //TODO: add functionality for List of units if required
    public static List<DelayedChat> delayedChats = new ArrayList<>();

    long gameFrame;
    String message;

    // **************************************
    // *********** CONSTRUCTORS *************
    // **************************************

    public DelayedChat(String message) {
        this(DelayedChat.getDelayedGameFrame(4), message);
    }

    public DelayedChat(long gameFrame, String message) {
        this.gameFrame = gameFrame;
        this.message = message;
    }

    // **************************************
    // ************* METHODS ****************
    // **************************************

    public void executeAction() {
        if (!Bot.isRealTime) {
            Bot.ACTION.sendChat(message, ActionChat.Channel.BROADCAST);
        }
    }

    // **************************************
    // ********** STATIC METHODS ************
    // **************************************
    public static void onStep() {
        delayedChats.stream()
                .filter(chat -> Time.nowFrames() >= chat.gameFrame)
                .forEach(DelayedChat::executeAction);
        delayedChats.removeIf(chat -> Time.nowFrames() >= chat.gameFrame);
    }

    public static long getDelayedGameFrame(int delaySeconds) {
        long gameLoop = Time.nowFrames() + (long)(delaySeconds * 22.4);
        gameLoop -= gameLoop % Strategy.STEP_SIZE;
        return gameLoop;
    }

    public static void add(String message) {
        add(4, message);
    }

    public static void add(long gameFrame, String message) {
        delayedChats.add(new DelayedChat(gameFrame, message));
    }

}
