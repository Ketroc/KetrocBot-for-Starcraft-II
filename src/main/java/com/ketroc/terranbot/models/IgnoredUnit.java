package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.HashSet;
import java.util.Set;

public class IgnoredUnit {
    public static Set<IgnoredUnit> ignoredUnits = new HashSet<>();
    public Tag unitTag;

    public IgnoredUnit(Tag unitTag) {
        this.unitTag = unitTag;
    }

    public boolean doReleaseUnit() {
        return false;
    }

}
