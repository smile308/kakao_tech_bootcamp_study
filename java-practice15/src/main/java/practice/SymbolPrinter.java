package practice;

public class SymbolPrinter extends Thread{
    @Override
    public void run(){
        for(int i=1;i<=5;i++)
        {
            System.out.println("[Symbol] 출력: "+i);
            try{
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}