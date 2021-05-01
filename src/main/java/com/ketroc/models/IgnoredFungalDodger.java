package com.ketroc.models;

import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.Time;

public class IgnoredFungalDodger extends Ignored {
    public long releaseGameFrame;

    public IgnoredFungalDodger(Tag unitTag) {
        super(unitTag);
        this.releaseGameFrame = Time.nowFrames() + Strategy.FUNGAL_FRAMES;
    }

    @Override
    public boolean doReleaseUnit() {
        return Time.nowFrames() >= releaseGameFrame;
    }

}
