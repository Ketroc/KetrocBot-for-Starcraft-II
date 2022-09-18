package com.ketroc.gson;

import com.ketroc.strategies.GamePlan;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
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

    public void setRecord(GamePlan gamePlan, int wins, int losses) {
        strategyWinRates.stream()
                .filter(record -> record.getGamePlan() == gamePlan)
                .findAny()
                .ifPresent(winLossRecord -> {
                    winLossRecord.setWins(wins);
                    winLossRecord.setLosses(losses);
                });
    }

    public void filterToGamePlans(Set<GamePlan> gamePlanSet) {
        strategyWinRates = gamePlanSet.stream()
                .map(gamePlan -> getRecord(gamePlan))
                .collect(Collectors.toSet());
    }

    public GamePlan getWinningestGamePlan() {
        return getWinningestGamePlan(filter -> true);
    }
    public GamePlan getWinningestGamePlan(Predicate<WinLossRecord> searchFilter) {
        if (strategyWinRates.size() == 1) {
            return strategyWinRates.iterator().next().getGamePlan();
        }

        GamePlan prevLossPlan = prevGameResult == null ? GamePlan.NONE : prevGameResult.getGamePlan();
        return strategyWinRates.stream()
                //find max win rate strategy with the least games played
                .filter(winLossRecord -> winLossRecord.getGamePlan() != prevLossPlan)
                .filter(searchFilter)
                .max(Comparator.comparing(record -> record.winRate() - ((float)record.numGames()) / 1000))
                .map(WinLossRecord::getGamePlan)
                .orElse(GamePlan.NONE);
    }

    //randomly select a game plan that requires more test games
    public GamePlan getGamePlanNeedingMoreTests(int minTestGames) {
        return getGamePlanNeedingMoreTests(minTestGames, winLossRecord -> true);
    }
    //randomly select a game plan that requires more test games
    public GamePlan getGamePlanNeedingMoreTests(int minTestGames, Predicate<WinLossRecord> searchFilter) {
        return strategyWinRates.stream()
                .filter(winLossRecord -> winLossRecord.numGames() < minTestGames)
                .filter(searchFilter)
                .min(Comparator.comparing(winLossRecord -> Math.random()))
                .map(WinLossRecord::getGamePlan)
                .orElse(GamePlan.NONE);
    }
}
