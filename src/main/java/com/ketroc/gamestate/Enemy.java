package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;

public class Enemy {
    private UnitInPool uip;
    private Unit prevStepUnit;

    public Enemy(UnitInPool uip) {
        this.uip = uip;
    }

    public UnitInPool getUip() {
        return uip;
    }

    public void setUip(UnitInPool uip) {
        this.uip = uip;
    }

    public Unit getPrevStepUnit() {
        return prevStepUnit;
    }

    public void setPrevStepUnit(Unit prevStepUnit) {
        this.prevStepUnit = prevStepUnit;
    }

    public boolean isAlive() {
        return uip != null && uip.isAlive();
    }

    public boolean is(Tag unitTag) {
        return uip.getTag().equals(unitTag);
    }
}
