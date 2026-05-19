import java.util.*;
import practice.*;

public class Main {
    public static void main(String[] args){
        UserLogPrinter u = new UserLogPrinter();
        SymbolPrinter s = new SymbolPrinter();

        u.start();
        s.start();

        System.out.println("종료");
    }
}
