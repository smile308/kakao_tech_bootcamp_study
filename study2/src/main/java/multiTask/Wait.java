package multiTask;
//현재 쓰이는 코드가 아님
public class Wait implements Runnable{
    private Thread sign;

    //다른 스레드를 기다리기 위해 스레드를 받음
    public Wait(Thread sign){
        this.sign=sign;
    }

    //가격비교를 출력하다가 다른 스레드가 작업을 마치면 가격 비교가 끝났다는 문구를 출력
    @Override
    public void run(){
        System.out.println("가격을 비교중입니다.");
        try {
            sign.join();
        } catch (InterruptedException e) {
            System.out.println("중단 요청이 들어왔습니다.");
            e.printStackTrace();
            return ;
        }
        System.out.println("가격 비교가 끝났습니다.");
    }
}
