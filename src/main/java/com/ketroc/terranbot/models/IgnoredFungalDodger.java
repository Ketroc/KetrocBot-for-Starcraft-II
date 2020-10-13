package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

public class IgnoredFungalDodger extends Ignored {
    public long releaseGameFrame;

    public IgnoredFungalDodger(Tag unitTag) {
        super(unitTag);
        this.releaseGameFrame = Bot.OBS.getGameLoop() + Strategy.FUNGAL_FRAMES;
    }

    @Override
    public boolean doReleaseUnit() {
        return Bot.OBS.getGameLoop() >= releaseGameFrame;
    }

}
