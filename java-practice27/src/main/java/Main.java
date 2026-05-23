import java.io.*;

public class Main{
    public static void main(String[] args){
        try(FileWriter writer = new FileWriter("sample.txt")){
            writer.write("Hello");
        } catch (IOException e) {
            System.out.println("파일 쓰기 중 오류 발생: "+e.getMessage());
        }

        try(BufferedReader reader = new BufferedReader(new FileReader("sample.txt"))){
            String line = reader.readLine();
            System.out.println("파일 내용: "+line);
        } catch (IOException e) {
            System.out.println("파일 읽기 중 오류 발생: "+e.getMessage());
        }
    }
}