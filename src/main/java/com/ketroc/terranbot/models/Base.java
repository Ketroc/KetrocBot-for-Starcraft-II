package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Base {
    public static final List<Units> GAS_GEYSER_TYPE = new ArrayList<>(
            Arrays.asList(Units.NEUTRAL_RICH_VESPENE_GEYSER, Units.NEUTRAL_SPACE_PLATFORM_GEYSER, Units.NEUTRAL_VESPENE_GEYSER, Units.NEUTRAL_PROTOSS_VESPENE_GEYSER, Units.NEUTRAL_PURIFIER_VESPENE_GEYSER, Units.NEUTRAL_SHAKURAS_VESPENE_GEYSER));
    public static final List<Units> MINERAL_NODE_TYPE = new ArrayList<>(
            Arrays.asList(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750));

    private Point2d ccPos;
    private Unit cc; //TODO: make Optional
    private List<UnitInPool> turrets = new ArrayList<>();
    private List<Gas> gases = new ArrayList<>();
    private List<UnitInPool> mineralPatches = new ArrayList<>();
    private UnitInPool rallyNode; //mineral node this cc is rallied to

    // ============= CONSTRUCTORS ============

    public Base(Point2d ccPos) {
        this.ccPos = ccPos;
        //initBase();
    }

    // =========== GETTERS AND SETTERS =============

    public List<UnitInPool> getMineralPatches() {
        return mineralPatches;
    }

    public void setMineralPatches(List<UnitInPool> mineralPatches) {
        this.mineralPatches = mineralPatches;
    }

    public Point2d getCcPos() {
        return ccPos;
    }

    public void setCcPos(Point2d ccPos) {
        this.ccPos = ccPos;
    }

    public Unit getCc() {
        return cc;
    }

    public void setCc(Unit cc) {
        this.cc = cc;
    }

    public List<Gas> getGases() {
        return gases;
    }

    public void setGases(List<Gas> gases) {
        this.gases = gases;
    }

    public UnitInPool getRallyNode() {
        return rallyNode;
    }

    public void setRallyNode(UnitInPool rallyNode) {
        this.rallyNode = rallyNode;
    }

    public List<UnitInPool> getTurrets() {
        return turrets;
    }

    public void setTurrets(List<UnitInPool> turrets) {
        this.turrets = turrets;
    }


    // ============ METHODS ==============

    public List<UnitInPool> getAvailableGeysers() {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, u -> {
            return this.ccPos.distance(u.unit().getPosition().toPoint2d()) < 10.0 &&
                    GAS_GEYSER_TYPE.contains(u.unit().getType());
        });
    }


}