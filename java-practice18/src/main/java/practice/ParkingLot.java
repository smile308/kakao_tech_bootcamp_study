package practice;

public class ParkingLot {
    private int availableSpots=3;

    public void enter(String carName){
        System.out.println(carName+"->진입 시도");

        synchronized (this){
            if(availableSpots>0){
                availableSpots--;
                System.out.println(carName+"주차 성공 / 남은 자리:"+availableSpots);
            }else{
                System.out.println(carName+"주차 실패");
            }
        }
        System.out.println(carName+"안내 종료");
    }
}
