package com.ketroc.micro;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;

public class Target {
    public UnitInPool unit;
    public float value;
    public float hp;

    public Target(UnitInPool unit, float value, float hp) {
        update(unit, value, hp);
    }

    public void update(UnitInPool unit, float value, float hp) {
        this.unit = unit;
        this.value = value;
        this.hp = hp;
    }
}
