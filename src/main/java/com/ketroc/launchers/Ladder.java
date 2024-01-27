package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.bots.Bot;
import com.ketroc.bots.KetrocBot;
import com.ketroc.bots.MicroArenaBot;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.DebugHelper;

public class Ladder {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.out.println("In DefaultUncaughtExceptionHandler");
            e.printStackTrace();
        });

        String opponentId = "";
        for (int i=0; i<args.length; i++) {
            String arg = args[i];
            if (arg.contains("--OpponentId")) {
                opponentId = args[i+1];
            }
        }
        Bot bot = new KetrocBot(opponentId);
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .setTimeoutMS(300000) //5min
                .setRawAffectsSelection(true)
                .loadLadderSettings(args)
                .setStepSize(2)
                .setParticipants(S2Coordinator.createParticipant(Race.TERRAN, bot))
                .connectToLadder()
                .joinGame();

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();
    }
}