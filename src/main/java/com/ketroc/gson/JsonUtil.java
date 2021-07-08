package com.ketroc.gson;

import com.google.gson.Gson;
import com.ketroc.bots.Bot;
import com.ketroc.strategies.GamePlan;
import com.ketroc.utils.Chat;

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
        Opponent opp;
        if (!Files.exists(filePath)) {
            opp = new Opponent();
        }
        else {
            Reader reader = null;
            reader = Files.newBufferedReader(filePath);
            opp = gson.fromJson(reader, Opponent.class);
            reader.close();
        }
        return opp;
    }

    public static void chatAllWinRates() {
        try {
            Gson gson = new Gson();
            Path filePath = getPath();
            Opponent opp = getOpponentRecords(gson, filePath);
            Chat.chatNeverRepeat(opp.toString());
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
