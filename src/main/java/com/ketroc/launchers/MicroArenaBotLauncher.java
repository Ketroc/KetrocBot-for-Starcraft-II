package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.AiBuild;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.bots.MicroArenaBot;
import com.ketroc.bots.TestMicroArenaBot;
import com.ketroc.bots.TestingBot;
import com.ketroc.utils.DebugHelper;

import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class MicroArenaBotLauncher {

    public static void main(String[] args) {
        DebugHelper.isDebugOn = true;

        List<String> mapList = List.of("PlateauMicro_2.SC2Map", "BotMicroArena_6.SC2Map");
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(false)
                .setWindowLocation(900, 0)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setShowBurrowed(true)
                .setStepSize(Launcher.STEP_SIZE)
                .setRawAffectsSelection(false)
                .setTimeoutMS(600 * 1000)
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, new MicroArenaBot("")),
                        S2Coordinator.createParticipant(Race.RANDOM, new TestMicroArenaBot()))
//                        S2Coordinator.createComputer(Race.PROTOSS, Difficulty.VERY_EASY, AiBuild.MACRO))
                .launchStarcraft()
                .startGame(LocalMap.of(Paths.get(mapList.get(new Random().nextInt(0,1)))));

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();
    }
}