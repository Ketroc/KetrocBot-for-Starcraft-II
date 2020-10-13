package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

import java.util.ArrayList;
import java.util.List;

public class IgnoredUnit extends Ignored{
    public IgnoredUnit(Tag unitTag) {
        super(unitTag);
    }

    public boolean doReleaseUnit() {
        return false;
    }
}
