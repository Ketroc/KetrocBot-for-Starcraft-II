package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.models.DelayedChat;

import java.util.*;

public class Chat {
    public static Set<String> usedTags = new HashSet<>();
    public static Map<String, Integer> usedMessages = new HashMap<>();
    public static Map<String, String> botResponses = new HashMap<>();
    static {
        //ANIBot responses
        botResponses.put("Scout sent", "RIP Scout");
        botResponses.put("Building Vikings", "Oh... but I'll always have more vikings :)");
        botResponses.put("Strategy:", "Thanks for letting me know.  If only I coded a response beyond this message.");
        botResponses.put("Hidden units detected", "Can you really say they're hiding if they kill everything you have?");
        botResponses.put("Scanning for enemy structures", "You consider terran structures an enemy??? Then start by killing your own structures.");
        botResponses.put("unarmed mine", "Sorry, I can't hear you over the sound of widow mines exploding...  You said something about my arms?");

        //Sproutch responses
        //botResponses.put("sprr", "New game. Andy does a shot every time you yell: Sproutch.");

        //RStrelok responses
        botResponses.put("VLADIMIR_PUTIN.EXE", "y");
        botResponses.put("What a clown", "It wasn't my choice. After downloading VLADIMIR_PUTIN.EXE, decisions are now made for me.");
        botResponses.put("Not today", "It wasn't my choice. After downloading VLADIMIR_PUTIN.EXE, decisions are now made for me.");
        botResponses.put("stupid idea", "It wasn't my choice. After downloading VLADIMIR_PUTIN.EXE, decisions are now made for me.");

        //MicroMachine responses
        botResponses.put("range upgrade on your missile turrets", "Did you know if you zoom in, there is guy manning the cockpit of that turret?");
        botResponses.put("rudimentary database is telling me that", "Rudimentary database? More like rude database");
    }

    public static final List<String> VIKING_DIVE = new ArrayList<>(List.of(
            "Vikings, YOLO!", "First viking to suicide wins a lollipop.  Go!", "Vikings, do your thing",
            "Let's purge some tempests", "Stalkers, please don't look up so I can kill some tempests"
    ));

    public static final List<String> WINNING_BM_CHAT = new ArrayList<>(List.of(
            "This is where Larva would start playing with his feet",
            "Just GG out.  I'm not going to crash--                           0x6F09844E FATAL ERROR!",
            "Shouldn't we be fast-forwarding by now?"
            //"Ya give up, or ya thirsty for more?",
            //"KET REKT!"
    ));

    public static final List<String> LOSING_BM_CHAT = new ArrayList<>(List.of(
            "Feel free to GG anytime now"

    ));

    public static final List<String> RANDOM_BM_CHAT = new ArrayList<>(List.of(
            "Do you have an extra Goto 10 line or something?"
    ));

    public static String getRandomMessage(List<String> messageList) {
        return messageList.get(new Random().nextInt(messageList.size()));
    }

    public static void respondToBots(ChatReceived chat) {
        botResponses.forEach((in, out) -> {
            String incomingMessage = chat.getMessage().toUpperCase();
            if (incomingMessage.contains(in.toUpperCase())) {
                if (in.equals("VLADIMIR_PUTIN.EXE")) { //instant response here
                    Bot.ACTION.sendChat(out, ActionChat.Channel.BROADCAST);
                }
                else {
                    DelayedChat.delayedChats.add(new DelayedChat(out));
                }
            }
        });

    }

    public static void chat(String message) {
        Bot.ACTION.sendChat(message, ActionChat.Channel.BROADCAST);
    }

    public static void chatInvisToHuman(String message) {
        Bot.ACTION.sendChat(message, getPublicChannel());
    }

    public static void chatNeverRepeat(String message) {
        chatNeverRepeat(message, ActionChat.Channel.BROADCAST);
    }

    public static void chatNeverRepeatInvisToHuman(String message) {
        chatNeverRepeat(message, getPublicChannel());
    }

    public static void chatNeverRepeat(List<String> chatOptions) {
        chatNeverRepeat(chatOptions, ActionChat.Channel.BROADCAST);
    }

    public static void chatNeverRepeatInvisToHuman(List<String> chatOptions) {
        chatNeverRepeat(chatOptions, getPublicChannel());
    }

    private static void chatNeverRepeat(String message, ActionChat.Channel channel) {
        if (!usedMessages.keySet().contains(message)) {
            usedMessages.put(message, Time.nowSeconds());
            Bot.ACTION.sendChat(message, channel);
        }
    }

    public static void chatNeverRepeat(List<String> chatOptions, ActionChat.Channel channel) {
        String randomMessage = getRandomMessage(chatOptions);
        if (usedMessages.keySet().contains(randomMessage)) {
            return;
        }
        Bot.ACTION.sendChat(randomMessage, channel);
        chatOptions.forEach(message -> usedMessages.put(message, Time.nowSeconds()));
    }

    public static void chatWithoutSpam(String message, int secondsBetweenMessages) {
        chatWithoutSpam(message, secondsBetweenMessages, ActionChat.Channel.BROADCAST);
    }

    public static void chatWithoutSpamInvisToHuman(String message, int secondsBetweenMessages) {
        chatWithoutSpam(message, secondsBetweenMessages, getPublicChannel());
    }

    public static void chatWithoutSpam(String message, int secondsBetweenMessages, ActionChat.Channel channel) {
        int lastChatted = usedMessages.getOrDefault(message, Integer.MIN_VALUE);
        if (lastChatted + secondsBetweenMessages <= Time.nowSeconds()) {
            usedMessages.put(message, Time.nowSeconds());
            Bot.ACTION.sendChat(message, channel);
        }
    }

    public static void chatWithoutSpam(List<String> chatOptions, int secondsBetweenMessages) {
        chatWithoutSpam(chatOptions, secondsBetweenMessages, ActionChat.Channel.BROADCAST);
    }

    public static void chatWithoutSpamInvisToHuman(List<String> chatOptions, int secondsBetweenMessages) {
        chatWithoutSpam(chatOptions, secondsBetweenMessages, getPublicChannel());
    }

    public static void chatWithoutSpam(List<String> chatOptions, int secondsBetweenMessages, ActionChat.Channel channel) {
        String randomMessage = getRandomMessage(chatOptions);
        int lastChatted = usedMessages.getOrDefault(randomMessage, Integer.MIN_VALUE);
        if (lastChatted + secondsBetweenMessages > Time.nowSeconds()) {
            return;
        }
        Bot.ACTION.sendChat(randomMessage, channel);
        chatOptions.forEach(message -> usedMessages.put(message, Time.nowSeconds()));
    }

    public static void tag(String tag) {
        if (!Launcher.isRealTime && tag != null && !tag.equals("")) {
            if (!usedTags.contains(tag)) {
                usedTags.add(tag);
                chat("Tag:" + Time.nowClock() + "_" + tag);
            }
        }
    }

    private static ActionChat.Channel getPublicChannel() {
        return Launcher.isRealTime ? ActionChat.Channel.TEAM : ActionChat.Channel.BROADCAST;
    }
}
