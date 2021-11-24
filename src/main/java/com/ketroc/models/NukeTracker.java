package com.ketroc.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.ketroc.GameCache;
import com.ketroc.bots.Bot;
import com.ketroc.micro.RavenMatrixer;
import com.ketroc.micro.UnitMicroList;
import com.ketroc.utils.Chat;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class NukeTracker {
    public static final int NUKE_DURATION = 319;
    public static final int NUKE_EFFECT_DURATION = 300;
    public static final int THREAT_AT = 224;
    public static Set<NukeTracker> activeNukes = new HashSet<>();

    private long startFrame;
    private EffectLocations effect;
    private boolean isRavenAssigned;
    private Optional<UnitInPool> nukeGhost = Optional.empty(); //TODO: check for movement to narrow down a list of ghosts

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

    //time (in seconds) until nuke cannot be cancelled
    public float cancelTimeRemaining() {
        return (NUKE_EFFECT_DURATION - (Time.nowFrames() - startFrame)) / 22.4f - 0.5f; //0.5s allotted for projectile time
    }

    //consider a threat as it's about to expire/jump
    public boolean doConsiderThreat() {
        return Time.nowFrames() - startFrame > THREAT_AT;
    }

    public static void onStep() {
        //add new nukes
        Bot.OBS.getEffects().stream()
                .filter(effect -> effect.getEffect() == Effects.NUKE_PERSISTENT &&
                                activeNukes.stream().noneMatch(nuke -> nuke.getEffect().equals(effect)))
                .forEach(effect -> activeNukes.add(new NukeTracker(effect)));

        //remove expired and cancelled nukes
        activeNukes.removeIf(nuke -> nuke.isExpired() || nuke.isCancelled());

        //assign raven to interrupt ghost
        for (NukeTracker nuke : activeNukes) {
            if (nuke.isRavenAssigned) {
                continue;
            }
            if (nuke.nukeGhost.isEmpty()) {
                nuke.nukeGhost = nuke.getNukeGhost();
            }
            if (nuke.nukeGhost.isPresent()) {
                nuke.isRavenAssigned = true; //one attempt to find a raven
                GameCache.ravenList.stream()
                        .filter(raven -> !Ignored.contains(raven.getTag()))
                        .min(Comparator.comparing(raven -> RavenMatrixer.minTimeToMatrix(raven, nuke.nukeGhost.get().unit())))
                        .filter(raven -> RavenMatrixer.minTimeToMatrix(raven, nuke.nukeGhost.get().unit()) <
                                nuke.cancelTimeRemaining())
                        .ifPresent(raven -> {
                            UnitMicroList.add(new RavenMatrixer(raven, nuke.nukeGhost.get()));
                            Chat.tag("MATRIX_GHOST");
                        });
            }
        }
    }

    //find closest ghost in range (cloaked ghost preference)
    public Optional<UnitInPool> getNukeGhost() {
        Point2d nukePos = effect.getPositions().iterator().next();
        return GameCache.allEnemiesMap.get(Units.TERRAN_GHOST).stream()
                .filter(ghost -> UnitUtils.getDistance(ghost.unit(), nukePos) - ghost.unit().getRadius() <= 12)
                .min(Comparator.comparing(ghost -> UnitUtils.getDistance(ghost.unit(), nukePos) +
                        (ghost.unit().getCloakState().get() == CloakState.NOT_CLOAKED ? 10 : 0)));
    }
}