package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Gas;
import com.ketroc.terranbot.purchases.PurchaseStructure;

public class Strategy {
    public static final int MAX_SCVS = 90;
    public static final float KITING_BUFFER = 2.5f;
    public static final int NUM_SCVS_BEFORE_GAS = 12; //number of scvs on minerals at a base before taking refineries
    public static final int RETREAT_HEALTH = 20; //% health of mech unit to go home to get repaired
    public static final int SKIP_FRAMES = 2;
    public static final int NUM_DONT_EXPAND = 2; //number of bases to never try expanding to
    public static final float ENERGY_BEFORE_CLOAKING = 60f; //don't cloak banshee if their energy is under this value
    public static final int NUM_SCVS_REPAIR_STATION = 6;
    public static final float BANSHEE_RANGE = 7; //range in which banshee will be given the command to attack
    public static final float VIKING_RANGE = 10; //range in which viking will be given the command to attack
    public static final int MIN_GAS_FOR_REFINERY = 1; //only build a refinery on this vespian node if it has at least this much gas
    public static final int DIVE_RANGE = 16;

    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;

    public static int getMaxScvs() {
        int idealScvs = 0; //(GameState.baseList.size() + GameState.productionMap.getOrDefault(Abilities.BUILD_COMMAND_CENTER, 0)) * 22;
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

    public static void onStep_TvtFaststart() {
        switch (step_TvtFastStart) {
            case 1:
                //rally it to the depot1
                Bot.ACTION.unitCommand(GameState.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, LocationConstants.REAPER_JUMP_2x2, false);
                step_TvtFastStart++;
                break;
            case 2:
                //rally back to mineral node
                if (Bot.OBS.getFoodWorkers() == 13) {
                    Bot.ACTION.unitCommand(GameState.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, GameState.baseList.get(0).getMineralPatches().get(0).unit(), false);
                    step_TvtFastStart++;
                }
                break;
            case 3:
                //queue up depot and rax with first scv
                if (GameState.mineralBank >= 100) {
                    scv_TvtFastStart = WorkerManager.getAllScvs(LocationConstants.REAPER_JUMP_2x2, 5).get(0); //TODO: null check
                    Bot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_BARRACKS, LocationConstants.REAPER_JUMP_3x3));
                    Bot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_SUPPLY_DEPOT, LocationConstants.REAPER_JUMP_2x2));
                    Switches.tvtFastStart = false;
                }
                break;
        }
    }
}
