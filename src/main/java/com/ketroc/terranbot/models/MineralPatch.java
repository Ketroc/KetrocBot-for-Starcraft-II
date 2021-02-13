package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.ketroc.terranbot.micro.ScvMiner;

import java.util.ArrayList;
import java.util.List;

public class MineralPatch {
    public UnitInPool mineralPatch;
    public List<ScvMiner> scvs = new ArrayList<>();

    public MineralPatch(UnitInPool mineralPatch) {
        this.mineralPatch = mineralPatch;
    }
}
