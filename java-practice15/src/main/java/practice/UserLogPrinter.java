package practice;

public class UserLogPrinter extends Thread{
    @Override
    public void run(){
        for(int i=1;i<=5;i++) {
            System.out.println("[UserLog] 용자자 로그인 처리 중" + i);

            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}