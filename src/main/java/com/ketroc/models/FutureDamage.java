package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.ketroc.utils.Time;

public class FutureDamage {
    public UnitInPool myUip;
    public UnitInPool enemyUip;
    public Abilities ability;
    public long startFrame;

    public FutureDamage(UnitInPool myUip, UnitInPool enemyUip, Abilities ability) {
        this.myUip = myUip;
        this.enemyUip = enemyUip;
        this.ability = ability;
        startFrame = Time.nowFrames();
    }

    public int calculateDamage() {
        return switch (ability) {
            case EFFECT_YAMATO_GUN -> 240;
            default -> 0;
        };
    }

    public boolean doRemove() {
        return switch (ability) {
            case EFFECT_YAMATO_GUN -> Time.nowFrames() - startFrame > 71; //yamato = 3sec + .33sec(guess) projectile time
            default -> true;
        };
    }
}