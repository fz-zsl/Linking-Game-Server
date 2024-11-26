import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Game {
    public Player player0,  player1;
    public Board board;
    public final int NUM_ICONS = 12;

    public Game(Player player0, Player player1, int row, int col) {
        this.player0 = player0;
        this.player1 = player1;
        List<Integer> numbers = new ArrayList<>();

        int avg = (row * col + NUM_ICONS - 1) / NUM_ICONS;
        avg += (avg & 1);
        for (int i = 0; i < NUM_ICONS; i++) {
            for (int j = 0; j < avg; j++) {
                numbers.add(i);
            }
        }
        while (numbers.size() > row * col) {
            numbers.removeLast();
        }

        do {
            Collections.shuffle(numbers);
            int[][] boardContent = new int[row][col];
            int index = 0;
            for (int i = 0; i < row; i++) {
                for (int j = 0; j < col; j++) {
                    boardContent[i][j] = numbers.get(index++);
                }
            }
            board = new Board(row, col, boardContent);
        } while (!board.hasMoreMoves());
    }
}
