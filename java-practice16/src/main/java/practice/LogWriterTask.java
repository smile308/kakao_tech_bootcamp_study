package practice;

public class LogWriterTask implements Runnable{
    @Override
    public void run(){
        for (int i=1;i<=5;i++)
        {
            System.out.println("로그 기록"+i);
            try
            {Thread.sleep(700);}
            catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }


}
