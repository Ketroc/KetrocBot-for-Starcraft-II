package com.ketroc.gson;

import com.google.gson.Gson;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.GamePlan;
import com.ketroc.strategies.Strategy;
import com.ketroc.utils.Chat;
import com.ketroc.utils.Time;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class JsonUtil {
    public static String getOpponentJsonFile() {
        return null; //TODO:
    }

    public static void setGameResult(GamePlan gamePlan, boolean didWin) {
        try {
            Gson gson = new Gson();
            Path filePath = getPath();

            Opponent opp = getOpponentRecords(gson, filePath);
            opp.incrementRecord(gamePlan, didWin);

            //maintain a record of the most recent loss
            if (!didWin) {
                opp.setPrevGameResult(new GameResult(Strategy.gamePlan, didWin, Time.nowClock()));
            }
            else if (opp.getPrevGameResult() == null) {
                opp.setPrevGameResult(new GameResult());
            }

            Files.write(filePath, gson.toJson(opp).getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Opponent getOpponentRecords() {
        try {
            return getOpponentRecords(new Gson(), getPath());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return new Opponent();
    }

    public static Opponent getOpponentRecords(Gson gson, Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return new Opponent();
        }
        Reader reader = Files.newBufferedReader(filePath);
        Opponent opp = gson.fromJson(reader, Opponent.class);
        reader.close();
        return opp;
    }

//    public static GameResult getGameResult() {
//        try {
//            return getGameResult(new Gson(), getPath());
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    public static GameResult getGameResult(Gson gson, Path filePath) throws IOException {
//        if (!Files.exists(filePath)) {
//            return null;
//        }
//        Reader reader = Files.newBufferedReader(filePath);
//        GameResult gameResult = gson.fromJson(reader, GameResult.class);
//        reader.close();
//        return gameResult;
//    }

    public static void chatAllWinRates(boolean doLog) {
        try {
            Gson gson = new Gson();
            Path filePath = getPath();
            Opponent opp = getOpponentRecords(gson, filePath);
            Chat.chatNeverRepeat(opp.toString());
            if (doLog) {
                System.out.println(opp);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getPath() {
        Optional<String> oppName = Bot.OBS.getGameInfo().getPlayersInfo().stream()
                .filter(playerInfo -> playerInfo.getPlayerId() != Bot.OBS.getPlayerId())
                .findFirst()
                .get()
                .getPlayerName();
        String fileName = oppName.orElse(Bot.opponentId.equals("") ? "human" : Bot.opponentId);
        return Path.of("./data/" + fileName + ".json");
    }
}
