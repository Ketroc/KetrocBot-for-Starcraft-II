package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

public class WinLossRecord {
    private GamePlan gamePlan;
    private int wins;
    private int losses;

    public WinLossRecord(GamePlan gamePlan) {
        this.gamePlan = gamePlan;
        this.wins = 0;
        this.losses = 0;
    }

    public WinLossRecord(GamePlan gamePlan, int wins, int losses) {
        this.gamePlan = gamePlan;
        this.wins = wins;
        this.losses = losses;
    }

    public GamePlan getGamePlan() {
        return gamePlan;
    }

    public void setGamePlan(GamePlan gamePlan) {
        this.gamePlan = gamePlan;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public void increment(boolean didWin) {
        if (didWin) {
            wins++;
        }
        else {
            losses++;
        }
    }

    public int numGames() {
        return wins + losses;
    }

    public float winRate() {
        if (numGames() == 0) {
            return 1;
        }
        return wins / (float)(wins + losses);
    }
}
