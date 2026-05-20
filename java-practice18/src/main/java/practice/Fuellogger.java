package practice;

public class Fuellogger {
    private static int logCount=0;
    public static synchronized void writeLog(String carName){
        logCount++;
        System.out.println(carName+"로그 기록됨 총 로그:"+logCount);
    }
}
