package printos.client;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

//import com.google.common.base.Charsets;
//import com.google.common.io.Files;
public class ESCPOSTagParser implements AbstractTagParser{
	static String SMALL_TEXT="h1"; // 9x17
	static String REGULAR_TEXT="h2"; // 12x24
	static String LARGE_TEXT="h3";  // 18x34
	static String HUGE_TEXT="h4"; //24x48
	static String ENORMOUS_TEXT="h5"; //27x51
	static String GIGANTIC_TEXT="h6"; //36x72
	static String BOLD_TEXT="b";
	static String BARCODE_REGION="barcode";
	static String ALIGN_LEFT="left";
	static String ALIGN_CENTER="center";
	static String ALIGN_RIGHT="right";
	static Set<String> VALID_TAGS=new HashSet<String>(Arrays.asList(
			new String[]{SMALL_TEXT,REGULAR_TEXT,LARGE_TEXT,HUGE_TEXT,ENORMOUS_TEXT,GIGANTIC_TEXT,
					BOLD_TEXT,ALIGN_LEFT,ALIGN_CENTER,ALIGN_RIGHT,BARCODE_REGION}
			));
			
	static int MAX_TAG_LENGTH=-1;
	static String BARCODE_CMD=""+(char)29+""+(char)'k';
	static String BARCODE_MODE=""+(char)73;
	static final String BARCODE_CODEB=""+(char)'{'+""+(char)'B';
	static String BARCODE_ENDING=""+(char)0;
	static String PRTCMD_FONT_TYPE_A=""+(char)27+'M'+(char)0; //12x24
	static String PRTCMD_FONT_TYPE_B=""+(char)27+'M'+(char)1; //9x17
	static String PRTCMD_FONT_SIZE_1=""+(char)29+'!'+(char)0;
	static String PRTCMD_FONT_SIZE_2=""+(char)29+'!'+(char)(16+1);
	static String PRTCMD_FONT_SIZE_3=""+(char)29+'!'+(char)(32+2);
	static String PRTCMD_FONT_SIZE_4=""+(char)29+'!'+(char)(48+3);
	static String PRTCMD_SMALL_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_1;
	static String PRTCMD_REGULAR_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_1;
	static String PRTCMD_LARGE_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_2;
	static String PRTCMD_HUGE_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_2;
	static String PRTCMD_ENORMOUS_TEXT=PRTCMD_FONT_TYPE_B + PRTCMD_FONT_SIZE_3;
	static String PRTCMD_GIGANTIC_TEXT=PRTCMD_FONT_TYPE_A + PRTCMD_FONT_SIZE_3;
	static String PRTCMD_BOLD_TEXT_ENABLE=""+(char)27+'E'+(char)1;
	static String PRTCMD_BOLD_TEXT_DISABLE=""+(char)27+'E'+(char)0;
	static HashMap<String,Integer> SIZE_TAG_WIDTH_MAP=new HashMap<String,Integer>();
	static HashMap<String,String> SIZE_TAG_COMMAND_MAP=new HashMap<String,String>();
	static{
		for(String tag:VALID_TAGS){
			if(tag.length()>MAX_TAG_LENGTH){
				MAX_TAG_LENGTH=tag.length();
			}
		}
		SIZE_TAG_WIDTH_MAP.put(SMALL_TEXT,9);
		SIZE_TAG_WIDTH_MAP.put(REGULAR_TEXT,12);
		SIZE_TAG_WIDTH_MAP.put(LARGE_TEXT,18);
		SIZE_TAG_WIDTH_MAP.put(HUGE_TEXT,24);
		SIZE_TAG_WIDTH_MAP.put(ENORMOUS_TEXT,27);
		SIZE_TAG_WIDTH_MAP.put(GIGANTIC_TEXT,36);
		SIZE_TAG_COMMAND_MAP.put(SMALL_TEXT, PRTCMD_SMALL_TEXT);
		SIZE_TAG_COMMAND_MAP.put(REGULAR_TEXT, PRTCMD_REGULAR_TEXT);
		SIZE_TAG_COMMAND_MAP.put(LARGE_TEXT, PRTCMD_LARGE_TEXT);
		SIZE_TAG_COMMAND_MAP.put(HUGE_TEXT, PRTCMD_HUGE_TEXT);
		SIZE_TAG_COMMAND_MAP.put(ENORMOUS_TEXT, PRTCMD_ENORMOUS_TEXT);
		SIZE_TAG_COMMAND_MAP.put(GIGANTIC_TEXT, PRTCMD_GIGANTIC_TEXT);

		
	}
	public boolean validate(String s) throws InvalidTagException, BadFormatException{
		if(s==null) throw new BadFormatException("Empty input. Please consult with API documentation and verify that field names match with the requirement.");
		int from=0,to=s.length();
		ArrayDeque<String> currentTags=new ArrayDeque<String>();
		boolean bold=false;
		boolean barcode=false;
		boolean notag=false;
		while(to>from){
			if(s.charAt(from)!='<'){
				from++;
			}else{
				String tag=getCurrentTag(s,from);
				if(tag!=null){
					if(tag.charAt(0)=='/'){
						if(currentTags.isEmpty())
							throw new BadFormatException("Closure without tag. Position="+from);
						String expecting=currentTags.pollFirst();
						if(!tag.substring(1).equals(expecting)){
							throw new BadFormatException("Invalid closure at position="+from+". Expecting "+expecting);
						}
						if(tag.equals("/"+BOLD_TEXT)){
							bold=false;
						}
						if(tag.equals("/"+BARCODE_REGION)){
							barcode=false;
							notag=false;
						}
						from+=tag.length()+2;
					}else{
						if(!VALID_TAGS.contains(tag)){
							throw new InvalidTagException("<"+tag+"> is an invalid tag. Position="+from);
						}
						if(notag){
							throw new InvalidTagException(
									String.format("Tag is not allowed to present because one previous tag %s forbade it. Position=%d",
											currentTags.getFirst(), from));
						}
						if(tag.equals(BOLD_TEXT)){
							if(bold){
								throw new BadFormatException("Nested bold text at "+from);
							}else{
								bold=true;
							}
						}
						if(tag.equals(BARCODE_REGION)){
							if(barcode){
								throw new BadFormatException("Nested barcode at "+from);
							}else{
								barcode=true;
								notag=true;
							}
						}
						from+=tag.length()+2;
						currentTags.addFirst(tag);
					}
				}else{
					throw new BadFormatException("Tag="+tag+" cannot be recognized at position(from)="+from);
				}
			}
		}
		if(!currentTags.isEmpty()){
			String expecting="";
			for(String tag:currentTags){
				expecting+=tag+",";
			}
			throw new BadFormatException("Tags without closure: "+expecting);
		}
		return true;
	}
	String spaceStr(int len) {
		return new String(new char[len]).replace("\0", " ");
	}
	String makeFiller(int len){
		return "<"+SMALL_TEXT+">"+spaceStr(len)+"</"+SMALL_TEXT+">";
	}
	String makeFiller(int len, String textSize){
		return "<"+textSize+">"+spaceStr(len)+"</"+textSize+">";
	}
	String processAlignment(String s, int lineWidth, String alignMode,int maxWidth,String biggestTextMode){
		int remain=maxWidth-lineWidth;
		//String measureUnit=(biggestTextMode.equals(GIGANTIC_TEXT)||biggestTextMode.equals(ENORMOUS_TEXT))?
		//		biggestTextMode:SMALL_TEXT;
		String measureUnit=SMALL_TEXT;

		if(alignMode.equals(ALIGN_CENTER)){
			int numFillerEachSide=remain/2/SIZE_TAG_WIDTH_MAP.get(measureUnit);
			s=makeFiller(numFillerEachSide,measureUnit)+s;
		}else if(alignMode.equals(ALIGN_RIGHT)){
			int numFiller=remain/SIZE_TAG_WIDTH_MAP.get(SMALL_TEXT);
			s=makeFiller(numFiller,measureUnit)+s;
		}
		return s;
	}
	List<String> divideToMultiLines(String s, int maxWidth){
		s+="\n";
		String currentLine="";
		List<String> lines=new ArrayList<String>();
		int currentFontWidth=12;
		int currentPos=0;
		int from=0,to=s.length();
		String alignMode=ALIGN_LEFT;
		ArrayDeque<String> textModeQueue=new ArrayDeque<String>();
		String currentLineBiggestTextMode=REGULAR_TEXT;
		textModeQueue.push(currentLineBiggestTextMode);
		ArrayDeque<String> alignQueue=new ArrayDeque<String>();
		alignQueue.push(alignMode);
		boolean noInterrupt=false;
		while(to>from){
			//System.out.println(s.charAt(from));
			if(s.charAt(from)=='<'){
				String tag=getCurrentTag(s,from);
				from+=tag.length()+2;
				if(tag.equals(BARCODE_REGION)){
					String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
					currentPos=0;
					lines.add(alignedLine);
					currentLine=new String("");
					noInterrupt=true;
				}else if(tag.equals("/"+BARCODE_REGION)){
//					currentLine+="</barcode>";
//					String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
//					currentPos=0;
//					lines.add(alignedLine);
//					currentLine=new String("");
					noInterrupt=false;
				}
				if(tag.equals(ALIGN_LEFT)||tag.equals(ALIGN_CENTER)||tag.equals(ALIGN_RIGHT)){
					if(from!=0){
						if(s.charAt(from-1)!='\n'){
							String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
							currentPos=0;
							lines.add(alignedLine);
							currentLine=new String("");
						}
					}
					alignMode=tag;
					alignQueue.addFirst(tag);
				}else if(tag.equals("/"+ALIGN_LEFT)||tag.equals("/"+ALIGN_CENTER)||tag.equals("/"+ALIGN_RIGHT)){
					if(from<to){
//						if(from!='\n'){
							String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
							currentPos=0;
							lines.add(alignedLine);
							currentLine=new String("");
//						}
					}
					alignQueue.removeFirst();
					alignMode=alignQueue.getFirst();
					continue;
				}else{
					if(SIZE_TAG_WIDTH_MAP.containsKey(tag)){
						currentFontWidth=SIZE_TAG_WIDTH_MAP.get(tag);
						currentLineBiggestTextMode=tag;
						textModeQueue.addFirst(tag);
					}else if(tag.charAt(0)=='/' && SIZE_TAG_WIDTH_MAP.containsKey(tag.substring(1))){
						textModeQueue.removeFirst();
						currentLineBiggestTextMode=textModeQueue.getFirst();
						currentFontWidth=SIZE_TAG_WIDTH_MAP.get(currentLineBiggestTextMode);
					}
					currentLine+=("<"+tag+">");
					//System.out.println("tag="+tag);
				}
				//strip off align tag, add other tags to text

			}else{
				if((!currentLine.equals(""))&&
						(s.charAt(from)=='\n'||currentPos+currentFontWidth>=maxWidth)&&
						(!noInterrupt)){
					String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
					currentPos=0;
					lines.add(alignedLine);
					currentLine=new String("");
					if(s.charAt(from)=='\n')from++;
				}else{
					currentPos+=currentFontWidth;
					currentLine+=s.charAt(from);
					from++;
				}
			}
		}
		if(!currentLine.isEmpty()){
			String alignedLine=processAlignment(currentLine,currentPos,alignMode,maxWidth,currentLineBiggestTextMode);
			//System.out.println("debug:"+alignedLine);
			//System.out.println("debug: mode="+alignMode);
			lines.add(alignedLine);
		}
		return lines;
	}
	public String convert(String s) throws Exception{
		return convert(s,null);
	}
	public String convert(String s, Integer width) throws Exception{
		if(!validate(s))return "";
		List<String> lines=divideToMultiLines(s,width==null?560:width);
		ArrayDeque<String> textMode=new ArrayDeque<String>();
		textMode.addFirst(REGULAR_TEXT);
		String output=""+SIZE_TAG_COMMAND_MAP.get(REGULAR_TEXT);
		linesloop:
		for(String line:lines){
			int pos=0;
			while(line.length()>pos){
				char c=line.charAt(pos);
				if(c=='<'){
					String tag=getCurrentTag(line,pos);
					if(tag.equals(BOLD_TEXT)){
						output+=PRTCMD_BOLD_TEXT_ENABLE;
					}else if(tag.equals("/"+BOLD_TEXT)){
						output+=PRTCMD_BOLD_TEXT_DISABLE;
					}else if(tag.equals(BARCODE_REGION)){
						output+="\n\n"+BARCODE_CMD;
						output+=BARCODE_MODE;
						String code=BarcodeBuilder.buildCodeB(getCurrentTagContent(pos+tag.length()+2,line));
						System.out.println(code);
						output+=(char)(code.length()+BARCODE_CODEB.length());
						output+=BARCODE_CODEB;
						output+=code;
						output+="\n";
						continue linesloop;
//					}else if(tag.equals("/"+BARCODE_REGION)){
//						output+=BARCODE_ENDING;
//						output+="\n\n";
					}else if(SIZE_TAG_COMMAND_MAP.containsKey(tag)){
						output+= SIZE_TAG_COMMAND_MAP.get(tag);
						textMode.addFirst(tag);
						//System.out.println("adding: "+tag);
					}else if(SIZE_TAG_COMMAND_MAP.containsKey(tag.substring(1))){
						textMode.removeFirst();
						String lastTag=textMode.getFirst();
						//System.out.println("removing: "+lastTag);
						output+=SIZE_TAG_COMMAND_MAP.get(lastTag);
					}
					pos+=tag.length()+2;
				}else{
					output+=c;
					pos++;
				}
				
			}
			output+="\n";
		}
		//output+="\n\n\n\n\n"+ (char)27+"m\n";
		return output;
	}
	public String getCurrentTagContent(int pos,String str){
		return str.substring(pos,str.indexOf("</", pos));
	}
	public void saveToFile(String str, String file) throws IOException{
		//Files.write(str.getBytes(Charsets.US_ASCII), new File(file));
		File f = new File(file);
		 
		// if file doesnt exists, then create it
		if (!f.exists()) {
			f.createNewFile();
		}

		FileWriter fw = new FileWriter(f.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(str);
		bw.close();
		
//		Files.write(str, new File(file),Charsets.US_ASCII);
	}
	public String getCurrentTag(String s, int pos){
		int to=-1;
		for(int i=pos;i<s.length();i++){
			

			if(s.charAt(i)=='>'){
				to=i;
				break;
			}
			if(i-pos-1>MAX_TAG_LENGTH){
				//System.out.println("to="+to+"\n");
				return null;
			}
		}
		
		if(to==-1){return null;}
		if(to-pos<=1){return null;}
		return s.substring(pos+1,to);
	}
	public String reverseGetCurrentTag(String s, int pos){
		int from=-1;
		for(int i=pos;i>0;i--){

			if(s.charAt(i)=='<'){
				from=i+1;
				break;
			}
			if(pos-i+1>MAX_TAG_LENGTH){
				return null;
			}
		}
		if(from==-1)return null;
		return s.substring(from, pos+1);
	}
	public static void main(String[] args){
		ESCPOSTagParser tp=new ESCPOSTagParser();
		//String data="<right><b>Add beef to potato strips. I will come soon.</b></right>abcd<right>Not bold Add beef to potato strips. I will come soon.</right>\n<h3>this is another test</h3>\n<right><h4>testh4</h4>\n<h5>superbig</h5>\n <h6>gigantic</h6></right>";
		//String data="test<right>test from ali at e29</right>dsadasfsafafgsagsagsa<barcode>123dsadsadsadasdadsadsadsadsadsdsasdsadasdasdsadsadsadas45678</barcode>";
		String data="61437208990nn<barcode>123321</barcode>";
		try{
			System.out.println(tp.validate(data));
			List<String> lines=tp.divideToMultiLines(data,480);
			for(String line:lines){
				System.out.println(line);
			}
			System.out.println("done");
			String result=tp.convert(data);
			System.out.println("convert done");
			tp.saveToFile(result,"test2");
			System.out.println("done2");
		}catch(Exception ex){
			ex.printStackTrace();
			
		}
		
	}
	
}
