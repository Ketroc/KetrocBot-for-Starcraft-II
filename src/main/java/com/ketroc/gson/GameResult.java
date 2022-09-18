package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

import java.util.HashSet;
import java.util.Set;

public class GameResult {
    private GamePlan gamePlan;
    private boolean didWin;
    private String time;
    private Set<String> tags = new HashSet<>();

    public GameResult() {
        this.gamePlan = GamePlan.NONE;
        this.didWin = false;
        this.time = "15:00";
    }

    public GameResult(GamePlan gamePlan, boolean didWin, String time, Set<String> tags) {
        this.gamePlan = gamePlan;
        this.didWin = didWin;
        this.time = time;
        this.tags = tags;
    }

    public GamePlan getGamePlan() {
        return gamePlan;
    }

    public void setGamePlan(GamePlan gamePlan) {
        this.gamePlan = gamePlan;
    }

    public boolean isDidWin() {
        return didWin;
    }

    public void setDidWin(boolean didWin) {
        this.didWin = didWin;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
}
