package com.ketroc.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;

public class CycloneLockData {
    private UnitInPool enemyCyclone;
    private UnitInPool targetUnit;
    private long castedGameFrame;

    public UnitInPool getEnemyCyclone() {
        return enemyCyclone;
    }

    public void setEnemyCyclone(UnitInPool enemyCyclone) {
        this.enemyCyclone = enemyCyclone;
    }

    public UnitInPool getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
    }

    public long getCastedGameFrame() {
        return castedGameFrame;
    }

    public void setCastedGameFrame(long castedGameFrame) {
        this.castedGameFrame = castedGameFrame;
    }
}
