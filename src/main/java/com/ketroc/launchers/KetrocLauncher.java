package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.protocol.Defaults;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.AiBuild;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.bots.KetrocBot;
import com.ketroc.bots.WorkerAMoveBot;
import com.ketroc.managers.BuildManager;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.DebugHelper;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class KetrocLauncher {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.out.println("In DefaultUncaughtExceptionHandler");
            e.printStackTrace();
        });

        DebugHelper.doTestingSpawns = true;
        DebugHelper.isDebugOn = true;
        Launcher.isRealTime = false;
        Launcher.STEP_SIZE = 2;
        Race oppRace = Race.ZERG;
        Difficulty oppDiff = Difficulty.CHEAT_INSANE;
        AiBuild oppBuild = AiBuild.MACRO;
        Strategy.gamePlan = GamePlan.RAVEN;

        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setRealtime(Launcher.isRealTime)
                .setFeatureLayers(Defaults.defaultSpatialSetup())
                .setMultithreaded(true)
                .setWindowLocation(2549, 480)
                .setWindowSize(1211, 800)
                .setNeedsSupportDir(true)
                .setShowCloaked(true)
                .setStepSize(Launcher.STEP_SIZE)
                .setShowBurrowed(true)
                .setRawAffectsSelection(true)
                .setTimeoutMS(10 * 60000) //10min
                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setEglPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setParticipants(
//                        S2Coordinator.createParticipant(Race.TERRAN, new EnemyDebugTestBot()),
                        S2Coordinator.createParticipant(Race.TERRAN, new KetrocBot("")),
                        S2Coordinator.createComputer(oppRace, oppDiff, oppBuild))
//                        S2Coordinator.createParticipant(Race.PROTOSS, new WorkerAMoveBot()))
//                        S2Coordinator.createParticipant(Race.TERRAN, new KetrocBot("")))
                .launchStarcraft()


                .startGame(LocalMap.of(Paths.get("AncientCisternAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("DragonScalesAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("GoldenauraAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("GresvanAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("InfestationStationAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("RoyalBloodAIE.SC2Map")));


//                .startGame(LocalMap.of(Paths.get("BerlingradAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("HardwireAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("InsideAndOutAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("MoondanceAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("StargazersAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("WaterfallAIE.SC2Map")));

//                .startGame(LocalMap.of(Paths.get("2000AtmospheresAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("BerlingradAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("BlackburnAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("CuriousMindsAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("GlitteringAshesAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("HardwireAIE.SC2Map")));

//                .startGame(LocalMap.of(Paths.get("2000AtmospheresAIE.SC2Map")));
//                .startGame(LocalMap.of(Paths.get("BlackburnAIE.SC2Map")));
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

//                .startGame(LocalMap.of(Paths.get("Flat482Spawns.SC2Map")));


        while (s2Coordinator.update()) {

        }
        s2Coordinator.quit();

    }
}