package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.utils.Time;

import java.util.HashSet;
import java.util.Set;

public class EnemyScan {
    public static Set<EnemyScan> enemyScanSet = new HashSet<>();

    // ************* FIELDS ************
    public static final int SCAN_DURATION = 275;
    public long endTime;
    public EffectLocations scanEffect;
    public Point2d position;

    // ************ CONSTRUCTOR ************
    public EnemyScan(EffectLocations scanEffect) {
        this.scanEffect = scanEffect;
        this.position = scanEffect.getPositions().iterator().next();
        this.endTime = Time.nowFrames() + SCAN_DURATION;
    }

    // ************ STATIC METHODS ************
    public static void onStep() {
        //if I can see the scan spot and it's not there, or it endTime has passed, then remove the scan
        enemyScanSet.removeIf(enemyScan ->
                (Bot.OBS.getVisibility(enemyScan.position) == Visibility.VISIBLE &&
                        Bot.OBS.getEffects().stream()
                                .noneMatch(effect -> effect.getEffect() == Effects.SCANNER_SWEEP &&
                                        enemyScan.position.distance(effect.getPositions().iterator().next()) < 1)) ||
                        Time.nowFrames() >= enemyScan.endTime);
    }

    public static boolean contains(EffectLocations scanEffect) {
        return enemyScanSet.stream().anyMatch(enemyScan -> enemyScan.scanEffect.equals(scanEffect));
    }

    public static void add(EffectLocations scanEffect) {
        enemyScanSet.add(new EnemyScan(scanEffect));
    }

    public static void remove(Point2d p) {
        enemyScanSet.removeIf(enemyScan -> enemyScan.position.equals(p));
    }
}
