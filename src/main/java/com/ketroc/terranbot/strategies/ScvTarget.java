package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;

import java.util.ArrayList;
import java.util.List;

public class ScvTarget {
    public int numScvs;
    public List<UnitInPool> scvs = new ArrayList<>();
    public UnitInPool targetUnit;
    public boolean giveUp = false;

    public ScvTarget(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
        int numScvs = 0;
        switch ((Units)targetUnit.unit().getType()) {
            case PROTOSS_PHOTON_CANNON:
                if (targetUnit.unit().getBuildProgress() > 0.65) {
                    giveUp = true;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.5) {
                    numScvs = 7;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.35) {
                    numScvs = 6;
                }
                else if (targetUnit.unit().getBuildProgress() > 0.2) {
                    numScvs = 5;
                }
                else {
                    numScvs = 4;
                }
                break;
            case PROTOSS_PROBE:
                numScvs = 1;
                break;
            case PROTOSS_PYLON:
                numScvs = 0;
                break;
        }
        this.numScvs = numScvs;
    }

}
