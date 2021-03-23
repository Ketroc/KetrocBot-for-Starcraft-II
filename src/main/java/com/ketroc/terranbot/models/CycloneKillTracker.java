package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

public class CycloneKillTracker {
    public long createdFrame;
    public int killCount;
    public Cost totalKillValue = new Cost();

    public CycloneKillTracker(long createdFrame) {
        this.createdFrame = createdFrame;
    }

}
