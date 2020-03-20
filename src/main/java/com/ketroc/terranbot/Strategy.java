package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Gas;

public class Strategy {
    public static final int MAX_SCVS = 90;
    public static final float KITING_BUFFER = 2.5f;
    public static final int NUM_SCVS_BEFORE_GAS = 12; //number of scvs on minerals at a base before taking refineries
    public static final int RETREAT_HEALTH = 20; //% health of mech unit to go home to get repaired
    public static final int SKIP_FRAMES = 4;
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_BEFORE_CLOAKING = 60f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 6;


    public static int getMaxScvs() {
        int idealScvs = 0; //(com.ketroc.terranbot.GameState.baseList.size() + com.ketroc.terranbot.GameState.productionMap.getOrDefault(Abilities.BUILD_COMMAND_CENTER, 0)) * 22;
        for (Base base : GameState.baseList) {
            idealScvs += base.getCc().getIdealHarvesters().get();
            for (Gas gas : base.getGases()) {
                UnitInPool refinery = gas.getRefinery();
                if (refinery != null) {
                    idealScvs += 3;
                }
            }
        }
        idealScvs += GameState.productionMap.getOrDefault(Abilities.BUILD_COMMAND_CENTER, 0) * 22;
        return Math.min(MAX_SCVS, Math.max(idealScvs, 44));
    }
}
