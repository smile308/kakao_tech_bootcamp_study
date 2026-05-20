package Info;

import java.util.HashMap;
import java.util.Map;

public class Market {
    //marketName : 마켓 이름(올리브영,지그재그,무신사)
    //priceMap <상품ID,가격>
    private String marketName;
    private Map<String,Integer> priceMap = new HashMap<>();

    //마켓 이름을 정하는 생성자
    public Market(String marketName)
    {
        this.marketName=marketName;
    }

    //priceMap에 상품의 아이디와 가격을 입력
    public void addProductPrice(String productId, int price)
    {
        this.priceMap.put(productId,price);
    }

    //priceMap에서 productId에 해당하는 값을 반환하고 없으면 -1을 반환 -1의 경우 오류 처리에 사용
    public int getPrice (String productId){
        return priceMap.getOrDefault(productId,-1);
    }

    //Market의 이름을 반환
    public String getMarketName(){
        return this.marketName;
    }

}
