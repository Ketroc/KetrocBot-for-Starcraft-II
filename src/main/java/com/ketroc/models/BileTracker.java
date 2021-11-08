package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.ketroc.bots.Bot;
import com.ketroc.utils.Time;

import java.util.HashSet;
import java.util.Set;

public class BileTracker {
    public static final int BILE_DURATION = 50;
    public static Set<BileTracker> activeBiles = new HashSet<>();

    private long endFrame;
    private EffectLocations effect;

    public BileTracker(EffectLocations effect) {
        this.effect = effect;
        this.endFrame = Time.nowFrames() + BILE_DURATION;
    }

    public long getEndFrame() {
        return endFrame;
    }

    public void setEndFrame(long endFrame) {
        this.endFrame = endFrame;
    }

    public EffectLocations getEffect() {
        return effect;
    }

    public void setEffect(EffectLocations effect) {
        this.effect = effect;
    }

    public static void onStep() {
        //add new biles
        Bot.OBS.getEffects().stream()
                .filter(effect -> effect.getEffect() == Effects.RAVAGER_CORROSIVE_BILE_CP &&
                                activeBiles.stream().noneMatch(bile -> bile.getEffect().equals(effect)))
                .forEach(effect -> activeBiles.add(new BileTracker(effect)));

        //remove expired biles
        activeBiles.removeIf(bile -> bile.getEndFrame() <= Time.nowFrames());
    }
}