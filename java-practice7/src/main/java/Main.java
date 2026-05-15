import java.util.*;
import java.io.*;
import com.google.gson.Gson;

public class Main {
    public static void main(String[] args)
    {
        Gson gson = new Gson();
        Scanner sc = new Scanner(System.in);
        String name=sc.nextLine();
        int age = sc.nextInt();
        sc.nextLine();
        String mail = sc.nextLine();
        String phone = sc.nextLine();
        Map<String, Object> data =new HashMap<>();
        data.put("name",name);
        data.put("age",age);
        data.put("mail",mail);
        data.put("phone",phone);



        try (Writer writer = new FileWriter("myInfo.json")){
            gson.toJson(data, writer);
        }catch(IOException e) {
            e.printStackTrace();
    }
        Map<?,?> read =null;
        try (Reader reader = new FileReader("myInfo.json")){
            read =gson.fromJson(reader, Map.class);
        }catch(IOException e){
            e.printStackTrace();
        }
        System.out.println(read);
    }

}