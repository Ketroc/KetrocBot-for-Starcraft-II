package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

import java.util.HashMap;
import java.util.Map;

public class Opponent {
    private Map<GamePlan,WinLossRecord> strategyWinRates;

    public Opponent() {
        this.strategyWinRates = new HashMap<>();
    }

    public Opponent(Map<GamePlan, WinLossRecord> strategyWinRates) {
        this.strategyWinRates = strategyWinRates;
    }

    public Map<GamePlan, WinLossRecord> getStrategyWinRates() {
        return strategyWinRates;
    }

    public void setStrategyWinRates(Map<GamePlan, WinLossRecord> strategyWinRates) {
        this.strategyWinRates = strategyWinRates;
    }

    public void incrementRecord(GamePlan gamePlan, boolean didWin) {
        WinLossRecord winLossRecord = strategyWinRates.getOrDefault(gamePlan, new WinLossRecord());
        winLossRecord.increment(didWin);
        strategyWinRates.put(gamePlan, winLossRecord);
    }

    @Override
    public String toString() {
        StringBuffer allStrategyWinRates = new StringBuffer("Win rates:");
        for (Map.Entry<GamePlan, WinLossRecord> entry : strategyWinRates.entrySet()) {
            allStrategyWinRates.append(" ")
                    .append(entry.getKey())
                    .append(":")
                    .append(entry.getValue().getWins())
                    .append("-")
                    .append(entry.getValue().getLosses());
        }
        return allStrategyWinRates.toString();
    }
}
