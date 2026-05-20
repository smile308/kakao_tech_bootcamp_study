package practice;

public class DownloadTask implements Runnable{
    @Override
    public void run(){
        for (int i=1;i<=5;i++)
        {
            System.out.println("파일"+i+"다운로드");
            try{
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
