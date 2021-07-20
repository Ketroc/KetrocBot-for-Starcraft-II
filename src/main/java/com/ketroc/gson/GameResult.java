package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

public class GameResult {
    private GamePlan gamePlan = GamePlan.NONE;
    private boolean didWin;
    private String time;

    public GameResult() {
        this.gamePlan = GamePlan.NONE;
        this.didWin = false;
        this.time = "15:00";
    }

    public GameResult(GamePlan gamePlan, boolean didWin, String time) {
        this.gamePlan = gamePlan;
        this.didWin = didWin;
        this.time = time;
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
}
