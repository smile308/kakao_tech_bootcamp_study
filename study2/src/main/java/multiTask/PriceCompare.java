package multiTask;

import codeInfo.Market;

import java.util.ArrayList;
import java.util.List;

public class PriceCompare implements Runnable{
   private List<Market> marketList = new ArrayList<>();
   private String insertId;
   //최저가를 찾기위해 가장 높은 int값을 미리 넣어둠
   private int minPrice=2147483647;
    private String minMarket;
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
                if(minPrice>m.getPrice(insertId))
                {
                    minPrice=m.getPrice(insertId);
                    minMarket=m.getMarketName();
                }
                System.out.println(m.getMarketName() + "에서 판매중인 가격은 " + m.getPrice(insertId) + "입니다.");
            }

        }
        System.out.println("해당 상품은 "+minMarket+"에서 "+minPrice+"원에 최저가로 판매되고 있습니다.");
    }

}
