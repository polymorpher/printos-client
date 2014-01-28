package potatos.client.rpi.printing;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;

public class ESCPOSConverterIcodT90 implements ESCPOSConverter{
//	static String PRTCMD_FONT_TYPE_A=""+(char)27+'M'+(char)0; //12x24
//	static String PRTCMD_FONT_TYPE_B=""+(char)27+'M'+(char)1; //9x17
//	static String PRTCMD_FONT_SIZE_1=""+(char)29+'!'+(char)0;
//	static String PRTCMD_FONT_SIZE_2=""+(char)29+'!'+(char)(16+1);
//	static String PRTCMD_FONT_SIZE_3=""+(char)29+'!'+(char)(32+2);
//	static String PRTCMD_FONT_SIZE_4=""+(char)29+'!'+(char)(48+3);
//	static String PRTCMD_SMALL_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_1;
//	static String PRTCMD_REGULAR_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_1;
//	static String PRTCMD_LARGE_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_2;
//	static String PRTCMD_HUGE_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_2;
//	static String PRTCMD_ENORMOUS_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_3;
//	static String PRTCMD_GIGANTIC_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_3;
//	static String PRTCMD_BOLD_TEXT_ENABLE=""+(char)27+'E'+(char)1;
//	static String PRTCMD_BOLD_TEXT_DISABLE=""+(char)27+'E'+(char)0;
//	static HashMap<String,Integer> SIZE_TAG_WIDTH_MAP=new HashMap<String,Integer>();
//	static HashMap<String,String> SIZE_TAG_COMMAND_MAP=new HashMap<String,String>();
//	static{
//		for(String tag:VALID_TAGS){
//			if(tag.length()>MAX_TAG_LENGTH){
//				MAX_TAG_LENGTH=tag.length();
//			}
//		}
//		SIZE_TAG_WIDTH_MAP.put(SMALL_TEXT,9);
//		SIZE_TAG_WIDTH_MAP.put(REGULAR_TEXT,12);
//		SIZE_TAG_WIDTH_MAP.put(LARGE_TEXT,18);
//		SIZE_TAG_WIDTH_MAP.put(HUGE_TEXT,24);
//		SIZE_TAG_WIDTH_MAP.put(ENORMOUS_TEXT,27);
//		SIZE_TAG_WIDTH_MAP.put(GIGANTIC_TEXT,36);
//		SIZE_TAG_COMMAND_MAP.put(SMALL_TEXT, PRTCMD_SMALL_TEXT);
//		SIZE_TAG_COMMAND_MAP.put(REGULAR_TEXT, PRTCMD_REGULAR_TEXT);
//		SIZE_TAG_COMMAND_MAP.put(LARGE_TEXT, PRTCMD_LARGE_TEXT);
//		SIZE_TAG_COMMAND_MAP.put(HUGE_TEXT, PRTCMD_HUGE_TEXT);
//		SIZE_TAG_COMMAND_MAP.put(ENORMOUS_TEXT, PRTCMD_ENORMOUS_TEXT);
//		SIZE_TAG_COMMAND_MAP.put(GIGANTIC_TEXT, PRTCMD_GIGANTIC_TEXT);
//
//		
//	}
//	@Override
//	public String convert(String s) throws Exception{
//		if(!validate(s))return "";
//		List<String> lines=divideToMultiLines(s,480);
//		ArrayDeque<String> textMode=new ArrayDeque<String>();
//		textMode.addFirst(REGULAR_TEXT);
//		String output=""+SIZE_TAG_COMMAND_MAP.get(REGULAR_TEXT);
//		for(String line:lines){
//			int pos=0;
//			while(line.length()>pos){
//				char c=line.charAt(pos);
//				if(c=='<'){
//					String tag=getCurrentTag(line,pos);
//					if(tag.equals(BOLD_TEXT)){
//						output+=PRTCMD_BOLD_TEXT_ENABLE;
//					}else if(tag.equals("/"+BOLD_TEXT)){
//						output+=PRTCMD_BOLD_TEXT_DISABLE;
//					}else if(SIZE_TAG_COMMAND_MAP.containsKey(tag)){
//						output+= SIZE_TAG_COMMAND_MAP.get(tag);
//						textMode.addFirst(tag);
//						//System.out.println("adding: "+tag);
//					}else if(SIZE_TAG_COMMAND_MAP.containsKey(tag.substring(1))){
//						textMode.removeFirst();
//						String lastTag=textMode.getFirst();
//						//System.out.println("removing: "+lastTag);
//						output+=SIZE_TAG_COMMAND_MAP.get(lastTag);
//					}
//					pos+=tag.length()+2;
//				}else{
//					output+=c;
//					pos++;
//				}
//				
//			}
//			output+="\n";
//		}
//		//output+="\n\n\n\n\n"+ (char)27+"m\n";
//		return output;
//	}
	public String convert(String taggedString){return "";}
}
