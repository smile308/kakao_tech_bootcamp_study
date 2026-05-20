import java.util.*;
import practice.*;

public class Main {
    public static void main(String[] args) {
    Runnable downloadTask = new DownloadTask();
    Runnable logTask = new LogWriterTask();

    Thread downloadThread= new Thread(downloadTask);
    Thread logThread= new Thread(logTask);

    downloadThread.start();
    logThread.start();

    System.out.println("메인 스레드 종료");

    }
}
