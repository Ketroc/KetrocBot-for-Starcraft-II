package com.ketroc.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.ketroc.bots.Bot;
import com.ketroc.utils.Time;

import java.util.HashSet;
import java.util.Set;

public class NukeTracker {
    public static final int NUKE_DURATION = 319;
    public static final int NUKE_EFFECT_DURATION = 300;
    public static Set<NukeTracker> activeNukes = new HashSet<>();

    private long startFrame;
    private EffectLocations effect;

    public NukeTracker(EffectLocations effect) {
        this.effect = effect;
        this.startFrame = Time.nowFrames();
    }

    public long getStartFrame() {
        return startFrame;
    }

    public void setStartFrame(long startFrame) {
        this.startFrame = startFrame;
    }

    public EffectLocations getEffect() {
        return effect;
    }

    public void setEffect(EffectLocations effect) {
        this.effect = effect;
    }

    public boolean isExpired() {
        return startFrame + NUKE_DURATION <= Time.nowFrames();
    }

    public boolean isCancelled() {
        return startFrame + NUKE_EFFECT_DURATION <= Time.nowFrames() &&
                !Bot.OBS.getEffects().contains(effect);
    }

    public static void onStep() {
        //add new nukes
        Bot.OBS.getEffects().stream()
                .filter(effect -> effect.getEffect() == Effects.NUKE_PERSISTENT &&
                                activeNukes.stream().noneMatch(nuke -> nuke.getEffect().equals(effect)))
                .forEach(effect -> activeNukes.add(new NukeTracker(effect)));

        //remove expired and cancelled nukes
        activeNukes.removeIf(nuke -> nuke.isExpired() || nuke.isCancelled());
    }
}