package potatos.client.util;
import java.util.*;
/**
 * Created by polymorpher on 6/24/14.
 */
public class Base62Util {
    static String codes="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static Map<Character, Integer> map=new HashMap<Character,Integer>();
    static{
        for(int i=0;i<codes.length();++i){
            map.put(Character.valueOf(codes.charAt(i)),Integer.valueOf(i));
        }
    }
    public static long decode(String s){
        long ret=0;
        long base=1;
        for(int i=0;i<s.length();++i){
            int pos=s.length()-i-1;
            ret += base * map.get(Character.valueOf(s.charAt(pos)));
            base*=62;
        }
        return ret;
    }
    public static String encode(long id){
        String ret="";
        while(id>=62){
            int c=(int) (id % 62);
            ret=codes.charAt(c) + ret;
            id=id/62;
        }
        ret=codes.charAt((int)id) + ret;
        return ret;
    }
    public static String trimLeadingZeros(String s){
        return s.substring(s.lastIndexOf('0')+1);
    }
    public static void main(String[] args) {
        System.out.println(encode(123456));
        System.out.println(decode("w7e"));
        System.out.println(trimLeadingZeros("04123456789"));

    }
}
