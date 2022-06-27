package com.ketroc.launchers;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.UnitType;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class SampleReplayObserver {

    private static class TestReplayObserver extends S2ReplayObserver {

        private Map<UnitType, Integer> countUnitsBuild = new HashMap<>();

        @Override
        public void onGameStart() {
            System.out.println("Hello world of Starcraft II replays!");
        }

        @Override
        public void onUnitCreated(UnitInPool unitInPool) {
            countUnitsBuild.compute(unitInPool.unit().getType(), (units, count) -> count == null ? 1 : ++count);
        }

        @Override
        public void onStep() {
            if (observation().getRawActions().stream().anyMatch(actionRaw -> actionRaw.getUnitCommand().isPresent())) {
                int qwe = 9;
            }
        }

        @Override
        public void onGameEnd() {
            System.out.println("Units created:");
            observation().getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
                if (!countUnitsBuild.containsKey(unitType)) return;
                System.out.println(unitType + ": " + countUnitsBuild.get(unitType));
            });
            System.out.println("Finished");
        }
    }

    public static void main(String[] args) throws Exception {
        TestReplayObserver replayObserver = new TestReplayObserver();
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setReplayPath(Paths.get("data/1206094_Ketroc_negativeZero_BlackburnAIE.SC2Replay"))
                //.setReplayPath(Paths.get("data/Berlingrad LE (9).SC2Replay"))
                .addReplayObserver(replayObserver)
                .launchStarcraft();

        while (s2Coordinator.update()) {
        }

        s2Coordinator.quit();
    }
}