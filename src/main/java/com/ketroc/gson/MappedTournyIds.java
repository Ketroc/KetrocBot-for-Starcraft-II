package com.ketroc.gson;

import java.util.HashMap;
import java.util.Map;

public class MappedTournyIds {
    private Map<String, String> protoss;
    private Map<String, String> terran;
    private Map<String, String> zerg;

    public MappedTournyIds() {
        protoss = new HashMap<>();
        terran = new HashMap<>();
        zerg = new HashMap<>();
    }

    public Map<String, String> getProtoss() {
        return protoss;
    }

    public void setProtoss(Map<String, String> protoss) {
        this.protoss = protoss;
    }

    public Map<String, String> getTerran() {
        return terran;
    }

    public void setTerran(Map<String, String> terran) {
        this.terran = terran;
    }

    public Map<String, String> getZerg() {
        return zerg;
    }

    public void setZerg(Map<String, String> zerg) {
        this.zerg = zerg;
    }


    @Override
    public String toString() {
        return new StringBuffer("Mapped Ids\n==========")
                .append("\nProtoss: ").append(protoss.toString())
                .append("\nTerran: ").append(terran.toString())
                .append("\nZerg: ").append(zerg.toString()).toString();
    }
}
