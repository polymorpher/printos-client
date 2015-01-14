package printos.client;
import printos.client.util.*;
public class BarcodeBuilder {
	static String BARCODE_CMD=(char)29+""+(char)107;
	static String BARCODE_MODE=""+(char)73;
	//Return ESC/POS barcode sequence
    public static String filterString(String str){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<str.length();++i){
            char c=str.charAt(i);
            if((c>=(char)0) && (c<=(char)9)){
                sb.append(c);
            }
        }
        return sb.toString();
    }
    public static String parseEscapes(String str){
        String ret="";
        int p=-1;
        for(int i=0;i<str.length();++i){
            if(str.charAt(i)==(char)27){
                if(p==-1){
                    p=i+1;
                }else{
                    ret+="|"+Base62Util.encode(Long.parseLong(str.substring(p,i)));
                    p=-1;
                }
            }else{
                if(p==-1){
                    ret+=str.charAt(i);
                }
            }
        }
        return ret;
    }
	public static String buildCodeB(String str){
        str=parseEscapes(str);
		String ret="";
		for(int i=0;i<str.length();i++){
			if(str.charAt(i)<=32)return "ERROR";
			ret+=(char)(((int)str.charAt(i)) - 0);
		}
		return ret;
	}

    public static void main(String[] args) {
        System.out.println(parseEscapes("phU23^"+(char)27+"0123213213131"+(char)27));
        System.out.println(parseEscapes("phU23^"+(char)27+"1"+(char)27+(char)27+"2"+(char)27));
    }
}
