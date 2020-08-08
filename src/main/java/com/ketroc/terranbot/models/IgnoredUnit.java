package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.ArrayList;
import java.util.List;

public abstract class IgnoredUnit {
    public static List<IgnoredUnit> ignoredUnits = new ArrayList<>();

    public Tag unitTag;
    public abstract boolean doReleaseUnit();
}
