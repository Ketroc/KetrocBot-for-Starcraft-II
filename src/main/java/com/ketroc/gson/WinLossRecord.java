package com.ketroc.gson;

public class WinLossRecord {
    private int wins;
    private int losses;

    public WinLossRecord() {
        this.wins = 0;
        this.losses = 0;
    }

    public WinLossRecord(int wins, int losses) {
        this.wins = wins;
        this.losses = losses;
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
}
