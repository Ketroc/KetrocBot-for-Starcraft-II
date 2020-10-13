package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

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
        Bot.ACTION.sendChat(message, ActionChat.Channel.BROADCAST);
    }

    // **************************************
    // ********** STATIC METHODS ************
    // **************************************
    public static void onStep() {
        delayedChats.stream()
                .filter(chat -> Bot.OBS.getGameLoop() >= chat.gameFrame)
                .forEach(DelayedChat::executeAction);
        delayedChats.removeIf(chat -> Bot.OBS.getGameLoop() >= chat.gameFrame);
    }

    public static long getDelayedGameFrame(int delaySeconds) {
        long gameLoop = Bot.OBS.getGameLoop() + (long)(delaySeconds * 22.4);
        gameLoop -= gameLoop % Strategy.SKIP_FRAMES;
        return gameLoop;
    }

}
