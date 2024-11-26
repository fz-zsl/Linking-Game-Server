import java.net.Socket;

public class Player {
    public Thread playerThread;
    public Socket playerSocket;
    public String name;
    public int score;
    public Boolean waiting;
    public Game currentGame;
    public int winGames = 0;
    public int totalGames = 0;

    public Player(String name, int winGames, int totalGames) {
        this.name = name;
        this.winGames = winGames;
        this.totalGames = totalGames;
    }

    public Player(Thread t, Socket s, String name) {
        this.playerThread = t;
        this.playerSocket = s;
        this.name = name;
    }
}
