import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.util.Scanner;

public class Server {
    private final int port;
    public ArrayList<Player> allPlayers = new ArrayList<>();
    public ArrayList<Game> activeGames = new ArrayList<>();
    public ArrayList<GameInfo> gameInfos = new ArrayList<>();
    public int row, col;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Please configure the number of rows: ");
        row = scanner.nextInt();
        System.out.print("Please configure the number of columns: ");
        col = scanner.nextInt();

        String csvFilePath = "./data/players.csv";
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                allPlayers.add(new Player(values[0], Integer.parseInt(values[1]), Integer.parseInt(values[2])));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        GameInfo.loadGameInfo(gameInfos, "./data/games.csv");

        for (Player player : allPlayers) {
            player.playerThread = null;
            player.playerSocket = null;
            player.waiting = false;
            player.currentGame = null;
        }
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is running on port " + port);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected");
                    Thread thread = new Thread(new ClientHandler(clientSocket));
                    thread.start();
                } catch (IOException e) {
                    System.err.println("Error accepting client connection");
//                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server on port " + port);
//            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private int[] position = new int[3];

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            String line;
            Player currentPlayer = null, opponentPlayer = null;
            Game currentGame = null;
            try (
                    InputStream input = clientSocket.getInputStream();
                    OutputStream output = clientSocket.getOutputStream();
                    BufferedReader in = new BufferedReader(new InputStreamReader(input));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(output));
            ) {
                while (true) {
                    line = in.readLine();
//                    System.out.println(line);
                    if (line == null) {
                        throw new IOException("received empty line");
                    }
                    String header = line.split(";")[0];
                    String body = line.split(";")[1];
                    if (header.equals("USERNAME")) {
                        for (Player player : allPlayers) {
                            if (player.name.equals(body)) {
                                player.playerSocket = clientSocket;
                                player.playerThread = Thread.currentThread();
                                player.waiting = true;
                                currentPlayer = player;
                            }
                        }
                        if (currentPlayer == null) {
                            currentPlayer = new Player(Thread.currentThread(), clientSocket, body);
                            currentPlayer.score = 0;
                            currentPlayer.waiting = true;
                            currentPlayer.currentGame = null;
                            allPlayers.add(currentPlayer);
                            logPlayers();
                        }
                    }
                    else if (header.equals("PAIR")) {
                        if (currentPlayer != null && currentPlayer.waiting && opponentPlayer == null) {
                            for (Player player : allPlayers) {
                                if (player.name.equals(body) && player.waiting) {
                                    opponentPlayer = player;
                                }
                            }
                        }
                        if (opponentPlayer == null || opponentPlayer == currentPlayer) {
                            out.write("MATCH;FAIL");
                        }
                        else {
                            currentGame = new Game(currentPlayer, opponentPlayer, row, col);
//                            System.out.println("Matching " + currentPlayer.name + " with " + opponentPlayer.name);
                            currentPlayer.waiting = false;
                            opponentPlayer.waiting = false;
                            currentPlayer.currentGame = currentGame;
                            opponentPlayer.currentGame = currentGame;
                            activeGames.add(currentGame);
                            out.write("MATCH;VALID");
                        }
                        out.newLine();
                        out.flush();
                    }
                    else if (line.equals("CHECK;MATCH")) {
                        if (currentPlayer.currentGame == null) {
                            out.write("MATCH;FAIL");
                        }
                        else {
                            out.write("MATCH;VALID");
                        }
                        out.newLine();
                        out.flush();
                    }
                    else if (line.equals("RETRIEVE;GAMEINFO")) {
                        if (currentPlayer == currentPlayer.currentGame.player0) {
                            out.write("USERNAME;" + currentPlayer.currentGame.player1.name);
                            out.newLine();
                            out.write("TURN;0");
                            out.newLine();
                            out.write("SCORE;" + currentPlayer.score + "," + currentPlayer.currentGame.player1.score);
                            out.newLine();
                            out.flush();
                        }
                        else {
                            out.write("USERNAME;" + currentPlayer.currentGame.player0.name);
                            out.newLine();
                            out.write("TURN;1");
                            out.newLine();
                            out.write("SCORE;" + currentPlayer.score + "," + currentPlayer.currentGame.player0.score);
                            out.newLine();
                            out.flush();
                        }
                        ArrayList<Integer> boardInfo = new ArrayList<>();
                        boardInfo.add(row);
                        boardInfo.add(col);
                        for (int i = 0; i < row; ++i) {
                            for (int j = 0; j < col; ++j) {
                                boardInfo.add(currentPlayer.currentGame.board.content[i][j]);
                            }
                        }
                        ByteArrayOutputStream bao = new ByteArrayOutputStream();
                        try (ObjectOutputStream oos = new ObjectOutputStream(bao)) {
                            oos.writeObject(boardInfo);
                            oos.flush();
                            String base64Encoded = Base64.getEncoder().encodeToString(bao.toByteArray());
                            out.write(base64Encoded);
                            out.newLine();
                            out.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else if (header.equals("CLICK")) {
                        if (opponentPlayer == null) {
                            if (currentPlayer == currentPlayer.currentGame.player0) {
                                opponentPlayer = currentPlayer.currentGame.player1;
                            }
                            else {
                                opponentPlayer = currentPlayer.currentGame.player0;
                            }
                        }
                        if (opponentPlayer.playerSocket != null) {
                            System.out.println("Try to contact opponent: " + opponentPlayer.name);
                            OutputStream opponentOutput = opponentPlayer.playerSocket.getOutputStream();
                            BufferedWriter opponentOut = new BufferedWriter(new OutputStreamWriter(opponentOutput));
                            opponentOut.write(line);
                            opponentOut.newLine();
                            opponentOut.flush();

                            int row_input = Integer.parseInt(body.split(",")[0]);
                            int col_input = Integer.parseInt(body.split(",")[1]);
                            if (position[0] == 0) {
                                position[1] = row_input;
                                position[2] = col_input;
                                position[0] = 1;
                            } else {
                                Board board = currentPlayer.currentGame.board;
                                Optional<ArrayList<Position>> route = board.judge(position[1], position[2], row_input, col_input);
                                position[0] = 0;
                                if (route.isPresent()) {
                                    board.setBoard(position[1], position[2]);
                                    board.setBoard(row_input, col_input);
                                    currentPlayer.score += 1;
                                }
                                else {
                                    currentPlayer.score -= 1;
                                }
                            }
                        }
                    }
                    else if (line.equals("REFRESH;USERLIST")) {
                        boolean continueGame = false;
                        for (Game game: activeGames) {
                            if (game.player0.name.equals(currentPlayer.name)) {
                                currentPlayer.score = game.player0.score;
                                currentPlayer.waiting = false;
                                currentPlayer.currentGame = game;
                                game.player0 = currentPlayer;
                                continueGame = true;
                            }
                            else if (game.player1.name.equals(currentPlayer.name)) {
                                currentPlayer.score = game.player1.score;
                                currentPlayer.waiting = false;
                                currentPlayer.currentGame = game;
                                game.player1 = currentPlayer;
                                continueGame = true;
                            }
                        }
                        if (currentPlayer.currentGame != null) {
                            out.write("MATCH;VALID");
                            out.newLine();
                            out.flush();

                            if (currentPlayer == currentPlayer.currentGame.player0) {
                                opponentPlayer = currentPlayer.currentGame.player1;
                            }
                            else {
                                opponentPlayer = currentPlayer.currentGame.player0;
                            }
                            in.readLine();  // should be "RETRIEVE;GAMEINFO"
                            out.write("USERNAME;" + opponentPlayer.name);
                            out.newLine();
                            out.write("TURN;1");
                            out.newLine();
                            out.write("SCORE;" + currentPlayer.score + "," + opponentPlayer.score);
                            out.newLine();
                            out.flush();
                            ArrayList<Integer> boardInfo = new ArrayList<>();
                            boardInfo.add(row);
                            boardInfo.add(col);
                            for (int i = 0; i < row; ++i) {
                                for (int j = 0; j < col; ++j) {
                                    boardInfo.add(currentPlayer.currentGame.board.content[i][j]);
                                }
                            }
                            ByteArrayOutputStream bao = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(bao)) {
                                oos.writeObject(boardInfo);
                                oos.flush();
                                String base64Encoded = Base64.getEncoder().encodeToString(bao.toByteArray());
                                out.write(base64Encoded);
                                out.newLine();
                                out.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            ArrayList<String> playerNames = new ArrayList<>();
                            String winRate = (currentPlayer.totalGames == 0 ? "Newbie" : (100 * currentPlayer.winGames / currentPlayer.totalGames) + "% win rate");
                            playerNames.add(currentPlayer.name + " (you, " + winRate + ")");
                            for (Player player : allPlayers) {
                                if (player.name.equals(currentPlayer.name)) {
                                    continue;
                                }
                                winRate = (player.totalGames == 0 ? "newbie" : (100 * player.winGames / player.totalGames) + "% win rate");
                                if (player.waiting) {
                                    playerNames.add(player.name + " (online, " + winRate + ")");
                                }
                            }
                            for (Player player : allPlayers) {
                                if (player.name.equals(currentPlayer.name)) {
                                    continue;
                                }
                                winRate = (player.totalGames == 0 ? "Newbie" : (100 * player.winGames / player.totalGames) + "% win rate");
                                if (!player.waiting) {
                                    playerNames.add(player.name + " (offline, " + winRate + ")");
                                }
                            }
                            ByteArrayOutputStream bao = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(bao)) {
                                oos.writeObject(playerNames);
                                oos.flush();
                                String base64Encoded = Base64.getEncoder().encodeToString(bao.toByteArray());
                                out.write(base64Encoded);
                                out.newLine();
                                out.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Client socket of " + currentPlayer.name + " closed");
                currentPlayer.waiting = false;
//                e.printStackTrace();
                if (opponentPlayer == null) {
                    if (currentPlayer == currentPlayer.currentGame.player0) {
                        opponentPlayer = currentPlayer.currentGame.player1;
                    }
                    else {
                        opponentPlayer = currentPlayer.currentGame.player0;
                    }
                }
                if (opponentPlayer.playerSocket == null) {
                    // both offline, end game
                    currentPlayer.playerThread = null;
//                    try {
////                        currentPlayer.playerSocket.close();
//                        System.out.println("Player " + currentPlayer.name + " socket closed -2");
//                    } catch (IOException ex) {
//                        throw new RuntimeException(ex);
//                    }
                    currentPlayer.playerSocket = null;
                    if (currentPlayer.currentGame != null) {
                        if (currentPlayer == currentPlayer.currentGame.player0) {
                            currentPlayer.score += currentGame.player0.score;
                            opponentPlayer.score += currentGame.player1.score;
                        }
                        else {
                            currentPlayer.score += currentGame.player1.score;
                            opponentPlayer.score += currentGame.player0.score;
                        }
                    }
                    activeGames.remove(currentPlayer.currentGame);
                    System.out.println("Logging the game ...");
                    GameInfo.logGame(gameInfos, currentGame, !currentPlayer.currentGame.board.hasMoreMoves(), "./data/games.csv");
                    currentPlayer.currentGame = null;
                    opponentPlayer.currentGame = null;
                }
                else {
                    // one offline, keep the game
                    currentPlayer.playerThread = null;
//                    try {
////                        currentPlayer.playerSocket.close();
//                        System.out.println("Player " + currentPlayer.name + "'s socket closed");
//                    } catch (IOException ex) {
//                        throw new RuntimeException(ex);
//                    }
                    currentPlayer.playerSocket = null;
                    try {
                        OutputStream opponentOutput = opponentPlayer.playerSocket.getOutputStream();
                        BufferedWriter opponentOut = new BufferedWriter(new OutputStreamWriter(opponentOutput));
                        opponentOut.write("OFFLINE;OPPONENT");
                        opponentOut.newLine();
                        opponentOut.flush();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
//            finally {
//                try {
////                    clientSocket.close();
//                    System.out.println("CLOSE SOCKET 3");
//                } catch (IOException e) {
//                    System.err.println("Error closing client socket");
////                    e.printStackTrace();
//                }
//            }
        }

        public void logPlayers() {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("./data/players.csv"))) {
                for (Player player : allPlayers) {
                    writer.write(player.name + "," + player.winGames + "," + player.totalGames);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int port = 1268;
        new Server(port).start();
    }
}
