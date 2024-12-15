import javazoom.jl.player.Player;
import java.io.FileInputStream;

public class Mp3PlayerThread extends Thread {
    private String filePath;
    private boolean repeat;

    public Mp3PlayerThread(String filePath, boolean repeat) {
        this.filePath = filePath;
        this.repeat = repeat;
    }

    @Override
    public void run() {
        do {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                Player player = new Player(fis);
                player.play(); // 동기적으로 실행, 파일 끝날 때까지 블로킹
            } catch (Exception e) {
                System.err.println("Error playing MP3: " + e.getMessage());
                break; // 에러 발생 시 반복 종료
            }
        } while (repeat); // repeat이 true면 반복 재생
    }
}
