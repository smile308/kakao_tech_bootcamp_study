package Thread;

import Info.Market;

import java.util.ArrayList;
import java.util.List;

public class PriceCompare implements Runnable{
   private List<Market> marketList = new ArrayList<>();
   private String insertId;


    public PriceCompare(List<Market> marketList, String insertId)
    {
        this.marketList=marketList;
        this.insertId=insertId;
    }

    //클래스에서 제품ID로 상품을 찾아 가격을 출력
    @Override
    public void run(){
        for(Market m : marketList){
            int price = m.getPrice(insertId);
            if (price != -1) {
                System.out.println(m.getMarketName() + "에서 판매중인 가격은 " + m.getPrice(insertId) + "입니다.");
            }
        }
    }

}
