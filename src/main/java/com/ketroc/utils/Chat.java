package com.ketroc.utils;

import com.github.ocraft.s2client.protocol.action.ActionChat;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.ketroc.bots.Bot;
import com.ketroc.models.DelayedChat;

import java.util.*;

public class Chat {
    public static Map<String, Integer> onceMessages = new HashMap<>();
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
        botResponses.put("rudimentary database is telling me that", "...and my rude database is telling me that you smell");
    }

    public static final List<String> VIKING_DIVE = new ArrayList<>(List.of(
            "Vikings, YOLO!", "Time to sacrifice vikings to the Gods of Aiur", "Vikings, do your thing",
            "Let's purge some tempests", "Stalkers, please don't look up so I can kill some tempests"
    ));

    public static final List<String> WINNING_BM_CHAT = new ArrayList<>(List.of(
            "This is where Larva would start playing with his feet",
            "Just GG already.  I'm not going to crash. This is a bug-free codebas-- 0x6F09844E FATAL ERROR!",
            "Ya give up, or ya thirsty for more?",
            "I wonder if I can convert minerals to BTC",
            "KET REKT!"
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

    public static void chatNeverRepeat(String message) {
        chatNeverRepeat(message, ActionChat.Channel.BROADCAST);
    }

    private static void chatNeverRepeat(String message, ActionChat.Channel channel) {
        if (!onceMessages.keySet().contains(message)) {
            onceMessages.put(message, Time.nowSeconds());
            Bot.ACTION.sendChat(message, channel);
        }
    }

    public static void chatWithoutSpam(String message, int secondsBetweenMessages) {
        int lastChatted = onceMessages.getOrDefault(message, Integer.MIN_VALUE);
        int now = Time.nowSeconds();
        if (lastChatted + secondsBetweenMessages <= now) {
            onceMessages.put(message, Time.nowSeconds());
            Bot.ACTION.sendChat(message, ActionChat.Channel.BROADCAST);
        }
    }

    public static void tag(String tag) {
        if (tag != null && !tag.equals("")) {
            chatNeverRepeat("Tag:ket" + tag, ActionChat.Channel.BROADCAST);
        }
    }
}
