package potatos.client.rpi.printing;

public class BarcodeBuilder {
	static String BARCODE_CMD=(char)29+""+(char)107;
	static String BARCODE_MODE=""+(char)73;
	
	//Return ESC/POS barcode sequence
	public static String buildCodeB(String str){
		String ret="";
		for(int i=0;i<str.length();i++){
			if(str.charAt(i)<=32)return "ERROR";
			ret+=(char)(((int)str.charAt(i)) - 0);
		}
		return ret;
	}
}
