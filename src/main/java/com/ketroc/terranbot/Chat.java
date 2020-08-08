package com.ketroc.terranbot;

import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.ketroc.terranbot.models.DelayedChat;

import java.util.*;

public class Chat {
    public static Map<String, String> botResponses = new HashMap<>();
    static {
        //ANIBot responses
        botResponses.put("Scout sent", "RIP Scout");
        botResponses.put("Building Vikings", "Oh... but I'll always have more vikings :)");
        botResponses.put("Strategy:", "Thanks for letting me know.  If only I coded a response beyond this message.");
        botResponses.put("Hidden units detected", "Can you really say they're hiding if they kill everything you have?");
        botResponses.put("Scanning for enemy structures", "You consider terran structures an enemy??? Then start by killing your own structures.");
        botResponses.put("unarmed mine", "Sorry, I can't hear you over the sound of widow mines exploding...  You said something about my arms?");
    }

    public static final List<String> VIKING_DIVE = new ArrayList<>(List.of(
            "Vikings, YOLO!", "Time to sacrifice vikings to the Gods of Aiur", "Vikings, do your thing",
            "Let's purge some tempests", "Stalkers, please don't look up so I can kill some tempests"
    ));

    public static String getRandomMessage(List<String> messageList) {
        return messageList.get(new Random().nextInt(messageList.size()));
    }

    public static void respondToBots(ChatReceived chat) {
        botResponses.forEach((in, out) -> {
            if (chat.getMessage().toUpperCase().contains(in.toUpperCase())) {
                DelayedChat.delayedChats.add(new DelayedChat(out));
            }
        });

    }
}
