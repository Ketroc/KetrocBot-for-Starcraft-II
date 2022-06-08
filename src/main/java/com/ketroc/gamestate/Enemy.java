package com.ketroc.gamestate;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.bots.Bot;
import com.ketroc.launchers.Launcher;
import com.ketroc.utils.PosConstants;
import com.ketroc.utils.Time;
import com.ketroc.utils.UnitUtils;

public class Enemy {
    private UnitInPool uip;
    private Unit prevStepUnit;
    private long prevUnitFrame;
    private long firstFrame;


    public Enemy(UnitInPool uip) {
        this.uip = uip;
        this.firstFrame = Time.nowFrames();
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

    public long getPrevUnitFrame() {
        return prevUnitFrame;
    }

    public void setPrevUnitFrame(long prevUnitFrame) {
        this.prevUnitFrame = prevUnitFrame;
    }

    public boolean isAlive() {
        return uip.isAlive();
    }

    public boolean is(Tag unitTag) {
        return uip.getTag().equals(unitTag);
    }

    @Override
    public String toString() {
        return uip.unit().getType() + " " + uip.getTag() + " " + uip.unit().getPosition().toPoint2d();
    }

    public Units getType() {
        return (Units)uip.unit().getType();
    }

    public Point2d getPosition() {
        return uip.unit().getPosition().toPoint2d();
    }

    public float getDistanceFromPrevStep() {
        //unreliable result because unit was in fog or steps were skipped
        if (Bot.OBS.getGameLoop() > prevUnitFrame + Launcher.STEP_SIZE) {
            return 0;
        }
        return UnitUtils.getDistance(prevStepUnit, uip.unit());
    }

    public boolean isInFog() {
        return UnitUtils.isInFogOfWar(uip);
    }

    public long getLastSeenFrame() {
        return uip.getLastSeenGameLoop();
    }

    public boolean isTemporary() {
        switch (getType()) {
            case TERRAN_KD8CHARGE: case TERRAN_AUTO_TURRET: case TERRAN_NUKE:
            case PROTOSS_DISRUPTOR_PHASED: case NEUTRAL_FORCE_FIELD: case PROTOSS_ADEPT_PHASE_SHIFT:
            case PROTOSS_ORACLE_STASIS_TRAP: case TERRAN_MULE:
            case ZERG_BROODLING: case ZERG_LOCUS_TMP: case ZERG_LOCUS_TMP_FLYING: case ZERG_CHANGELING_MARINE:
            case ZERG_CHANGELING_MARINE_SHIELD: case ZERG_CHANGELING:
                return true;
        }
        return false;
    }

    //returns true when enemy unit hasn't been spotted for a set period of time
    public boolean isExpired() {
        //never expire structures that aren't burning
        if (UnitUtils.isStructure(uip.unit().getType()) &&
                (PosConstants.opponentRace != Race.TERRAN || UnitUtils.getHealthPercentage(uip.unit()) > 33)) {
            return false;
        }

        //expired temporary units
        if (isTemporary() && Time.after(firstFrame + getDuration())) {
            return true;
        }

        //expire units that haven't been seen for 3min TODO: make this smarter
        return Time.after(getLastSeenFrame() + 4032);
    }

    private long getDuration() {
        switch (getType()) {
            case TERRAN_KD8CHARGE:
                return 0;
            case TERRAN_AUTO_TURRET:
                return 224; //10s
            case PROTOSS_DISRUPTOR_PHASED:
                return 47; //2.1s
            case NEUTRAL_FORCE_FIELD:
                return 246; //11s
            case PROTOSS_ADEPT_PHASE_SHIFT:
                return 246; //11s
            case PROTOSS_ORACLE_STASIS_TRAP:
                return 3808; //170s
            case TERRAN_MULE:
                return 1434; //64s
            case ZERG_BANELING_COCOON:
                return 314; //14s
            case ZERG_BROODLING:
                return 128; //5.71s
            case ZERG_LOCUS_TMP: case ZERG_LOCUS_TMP_FLYING:
                return 403; //18s
            case ZERG_CHANGELING_MARINE: case ZERG_CHANGELING_MARINE_SHIELD: case ZERG_CHANGELING:
                return 3360; //150s
        }
        return Long.MAX_VALUE;
    }
}
