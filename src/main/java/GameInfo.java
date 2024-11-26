import java.io.*;
import java.util.ArrayList;

public class GameInfo {
    String playerName0;
    String playerName1;
    int playerScore0;
    int playerScore1;
    boolean finished;

    public GameInfo(String playerName0, String playerName1, int playerScore0, int playerScore1, boolean finished) {
        this.playerName0 = playerName0;
        this.playerName1 = playerName1;
        this.playerScore0 = playerScore0;
        this.playerScore1 = playerScore1;
        this.finished = finished;
    }

    public GameInfo(Game game, boolean finished) {
        this.playerName0 = game.player0.name;
        this.playerName1 = game.player1.name;
        this.playerScore0 = game.player0.score;
        this.playerScore1 = game.player1.score;
        this.finished = finished;
    }

    public static void loadGameInfo(ArrayList<GameInfo> gameInfos, String filename) {
        gameInfos.removeAll(gameInfos);
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                gameInfos.add(new GameInfo(
                        values[0], values[1],
                        Integer.parseInt(values[2]), Integer.parseInt(values[3]),
                        Integer.parseInt(values[4]) == 1
                ));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logGame(ArrayList<GameInfo> gameInfos, Game game, boolean finished, String filename) {
        GameInfo gameInfo = new GameInfo(game, finished);
        gameInfos.add(gameInfo);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (GameInfo gameInfoTmp : gameInfos) {
                writer.write(gameInfoTmp.playerName0 + "," + gameInfoTmp.playerName1 + ",");
                writer.write(gameInfoTmp.playerScore0 + "," + gameInfoTmp.playerScore1 + ",");
                writer.write(gameInfoTmp.finished ? "1" : "0");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
