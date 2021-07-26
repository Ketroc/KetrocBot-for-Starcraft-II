package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Opponent {
    private Set<WinLossRecord> strategyWinRates;
    private GameResult prevGameResult;

    public Opponent() {
        this.strategyWinRates = new HashSet<>();
    }

    public Opponent(Set<WinLossRecord> strategyWinRates) {
        this.strategyWinRates = strategyWinRates;
    }

    public Set<WinLossRecord> getStrategyWinRates() {
        return strategyWinRates;
    }

    public void setStrategyWinRates(Set<WinLossRecord> strategyWinRates) {
        this.strategyWinRates = strategyWinRates;
    }

    public GameResult getPrevGameResult() {
        return prevGameResult;
    }

    public void setPrevGameResult(GameResult prevGameResult) {
        this.prevGameResult = prevGameResult;
    }

    public void incrementRecord(GamePlan gamePlan, boolean didWin) {
        strategyWinRates.stream()
                .filter(winLossRecord -> winLossRecord.getGamePlan() == gamePlan)
                .findAny()
                .ifPresentOrElse(
                        winLossRecord -> winLossRecord.increment(didWin),
                        () -> {
                            strategyWinRates.add(new WinLossRecord(
                                    gamePlan,
                                    didWin ? 1 : 0,
                                    didWin ? 0 : 1
                            ));
                        });
    }

    @Override
    public String toString() {
        StringBuffer allStrategyWinRates = new StringBuffer("Win rates:");
        for (WinLossRecord record : strategyWinRates) {
            allStrategyWinRates.append(" ")
                    .append(record.getGamePlan())
                    .append(":")
                    .append(record.getWins())
                    .append("-")
                    .append(record.getLosses());
        }
        return allStrategyWinRates.toString();
    }

    public WinLossRecord getRecord(GamePlan gamePlan) {
        return strategyWinRates.stream()
                .filter(record -> record.getGamePlan() == gamePlan)
                .findAny()
                .orElse(new WinLossRecord(gamePlan));
    }

    public void filterToGamePlans(Set<GamePlan> gamePlanSet) {
        strategyWinRates = gamePlanSet.stream()
                .map(gamePlan -> getRecord(gamePlan))
                .collect(Collectors.toSet());
    }

    public GamePlan getWinningestGamePlan() {
        GamePlan prevLossPlan = prevGameResult == null ? GamePlan.NONE : prevGameResult.getGamePlan();
        return strategyWinRates.stream()
                //find max win rate strategy with the least games played
                .filter(winLossRecord -> winLossRecord.getGamePlan() != prevLossPlan)
                .max(Comparator.comparing(record -> record.winRate() - ((float)record.numGames()) / 1000))
                .orElse(strategyWinRates.iterator().next())
                .getGamePlan();
    }

    public GamePlan getGamePlanNeedingMoreTests(int minTestGames) {
        return strategyWinRates.stream()
                .filter(winLossRecord -> winLossRecord.numGames() < minTestGames)
                .min(Comparator.comparing(WinLossRecord::numGames))
                .map(WinLossRecord::getGamePlan)
                .orElse(GamePlan.NONE);
    }
}
