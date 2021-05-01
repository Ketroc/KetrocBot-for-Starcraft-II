package com.ketroc.models;

import com.github.ocraft.s2client.protocol.unit.Tag;

public class IgnoredUnit extends Ignored{
    public IgnoredUnit(Tag unitTag) {
        super(unitTag);
    }

    public boolean doReleaseUnit() {
        return false;
    }
}
