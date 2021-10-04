package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.bots.KetrocBot;

import java.nio.file.Paths;

public class KetrocLauncher {
    public static void main(String[] args) {
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(Launcher.isRealTime)
                .setMultithreaded(true)
                .setWindowLocation(2560, 0)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setStepSize(2)
                .setShowBurrowed(true)
                .setRawAffectsSelection(true)
                .setTimeoutMS(10 * 60000) //10min
//                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setParticipants(
                        S2Coordinator.createParticipant(Race.TERRAN, new KetrocBot(true, "")),
                        S2Coordinator.createComputer(Race.TERRAN, Difficulty.MEDIUM))
//                        S2Coordinator.createParticipant(Race.ZERG, new EnemyDebugTestBot()))
                .launchStarcraft()

//                .startGame(LocalMap.of(Paths.get("2000AtmospheresAIE.SC2Map")));
                .startGame(LocalMap.of(Paths.get("BlackburnAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("JagannathaAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("LightshadeAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("OxideAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("RomanticideAIE.SC2Map")));


//                .startGame(LocalMap.of(Paths.get("AscensiontoAiurLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("CatalystLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DeathAura506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("EternalEmpire506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("EverDream506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("GoldenWall506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("IceandChrome506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("NightshadeLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("PillarsOfGold506.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("Submarine506.SC2Map")));

//                .startGame(LocalMap.of(Paths.get("AcropolisLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DiscoBloodbathLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("Ephemeron.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("EphemeronLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("SimulacrumLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("ThunderbirdLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("TritonLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WintersGateLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WorldofSleepersLE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("ZenLE.SC2Map")));

        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();

    }

}