package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.ketroc.GameCache;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.HashSet;
import java.util.Set;

public class AdeptShadeTracker {
    public static final int SHADE_DURATION = 160;
    public static final int THREAT_FRAMES = 12;
    public static final int TARGET_FRAMES = 40;
    public static Set<AdeptShadeTracker> activeShades = new HashSet<>();

    private long endFrame;
    private UnitInPool shadeUip;

    public AdeptShadeTracker(UnitInPool shadeUip) {
        this.shadeUip = shadeUip;
        boolean shadeCreatedInVision = GameCache.allVisibleEnemiesMap.get(Units.PROTOSS_ADEPT).stream()
                .anyMatch(adept -> UnitUtils.getDistance(adept, shadeUip.unit()) < 1.5f);
        this.endFrame = Time.nowFrames() + (int)(shadeCreatedInVision ? SHADE_DURATION : SHADE_DURATION * 0.8f); //remove 20% of shade duration if I didn't see it get created
    }

    public long getEndFrame() {
        return endFrame;
    }

    public void setEndFrame(long endFrame) {
        this.endFrame = endFrame;
    }

    public UnitInPool getShadeUip() {
        return shadeUip;
    }

    public void setShadeUip(UnitInPool shadeUip) {
        this.shadeUip = shadeUip;
    }

    //shades that expired, were cancelled, or went into the fog and past the allotted max time
    public boolean hasExpired() {
        return !shadeUip.isAlive() || (UnitUtils.isInFogOfWar(shadeUip) && Time.nowFrames() > endFrame);
    }

    //consider a threat as it's about to expire/jump
    public boolean doConsiderThreat() {
        return framesFromComplete() < THREAT_FRAMES;
    }

    //consider a threat as it's about to expire/jump
    public boolean doConsiderTarget() {
        return framesFromComplete() < TARGET_FRAMES;
    }

    public long framesFromComplete() {
        return endFrame - Time.nowFrames();
    }

    public static void onShadeCreated(UnitInPool shadeUip) {
        activeShades.add(new AdeptShadeTracker(shadeUip));
    }

    public static void onStep() {
        //remove shades that died, expired, were cancelled, or went into the fog and past the allotted max time
        activeShades.removeIf(shade -> shade.hasExpired());
    }

    public static boolean contains(UnitInPool shadeEnteredVision) {
        return activeShades.stream()
                .anyMatch(shade -> shade.shadeUip.getTag().equals(shadeEnteredVision.getTag()));
    }

    public static void add(UnitInPool shadeToAdd) {
        if (!contains(shadeToAdd)) {
            activeShades.add(new AdeptShadeTracker(shadeToAdd));
        }
    }

    public static boolean shouldTargetShade(UnitInPool shadeUip) {
        return activeShades.stream()
                .anyMatch(s -> s.shadeUip.getTag().equals(shadeUip.getTag()) && s.doConsiderTarget());
    }
}
