package com.ketroc.models;

public class CycloneKillTracker {
    public long createdFrame;
    public int killCount;
    public Cost totalKillValue = new Cost();

    public CycloneKillTracker(long createdFrame) {
        this.createdFrame = createdFrame;
    }

}
