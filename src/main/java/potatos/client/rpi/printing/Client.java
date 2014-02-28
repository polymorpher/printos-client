package potatos.client.rpi.printing;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.util.*;
import com.google.gson.*;
import java.text.*;
import java.net.URLDecoder;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;

import com.google.common.base.Charsets;
import com.google.common.io.*;
import java.util.concurrent.locks.*;
import potatos.client.model.*;
public class Client {
//	static String ROOT_DIR="/home/pi";
	//static String ROOT_DIR="/home/polymorpher/workspace-javaee/PotatOSCloudPrintClient";
	static String configfile = "PrintOSconfig.ini";
	static int ERROR_NONE=0;
	static int ERROR_JSON=1;
	static int ERROR_TAG=2;
	static int ERROR_PRINTER=3;
	static int ERROR_OTHERS=1337;
	static int version=6;
	URL url;
	URLConnection connection;
	DateFormat formatter;
	FileWriter fstream;
	BufferedWriter log;
	public Set<Integer> printStack=new HashSet<Integer>();
	public ReentrantLock printStackLock=new ReentrantLock();
	HashMap<String, String> config=new HashMap<String, String>();
	TagParser parser=new TagParser();
	int sleeptime = 1000;
	Gson gson=new Gson();
	String PRINTER_NAME="";
	String CUT_EPSON=""+(char)29+'V'+(char)0;
	String CUT_LEGACY=""+(char)27+'m';
	String cutCommand="";
	
	public static void main(String[] args) {
		Client c=new Client();
		if(args.length>=1){
			if(args[0].equals("-cleanupgrade")){
//				c.cleanUpgradeFile(Integer.parseInt(args[2]));
			}else if(args[0].equals("-u")){
				c.writeLog("upgrading!");
				try{
					Thread.sleep(5000);
				}catch(Exception e){
					System.out.println(e.toString());
				}
//				c.deleteOldVersion(Integer.parseInt(args[2]));
//				c.copyTempFilesOver();
//				c.exitAndStartNormally();
			}else if(args[0].equals("-v")){
				System.out.println("version="+Client.version);
				System.exit(0);
			}
		}
		c.start();
	}
	
	 class ReceiptData {
		String mode;
		String title;
		String Items[][]; // Each sub array: 0:ID, 1:Name, 2:Quantity 3:Price
		String Total;
		String Discount;
		String Name;
		String Phone;
		String Email;
		String Method;
		String Comments;
		Long CreationTime;
		Long RequiredTime;
		Integer lineWidth;
		String barcode;
	}
	class Response{
		Boolean pass;
		Integer version;
		Integer[] ids;
		String error;
		String[] data;
	}
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}
	Client() {
		formatter=new SimpleDateFormat("h:mm:ss a,dd-MMM-yy");
		try{
			fstream = new FileWriter("out.log", true);
			log = new BufferedWriter(fstream);
		}catch(Exception e){
			System.err.println("Cannot create log file:\n"+e.toString());
			return;
		}
		try{
			config = readConfig(configfile);
		}catch(Exception e){
			writeLog(e.toString());
			System.exit(1);
		}
		
		if (config.containsKey("sleep")) {
			sleeptime = Integer.parseInt(config.get("sleep")) * 1000;
		}	 
		if (config.containsKey("printer")) {
			selectPrinter(config.get("printer"));
		} else {
			writeLog("Printer is not defined in config file. Syntax: printer=aaa;bbb;...");
			System.exit(2);
		}
		
	}
	
	public static String spaceStr(int len) {
		return new String(new char[len]).replace("\0", ".");
	}
	public void writeLog(String s){
		System.err.println(s);
		try{
			log.write(formatter.format(new java.util.Date())+" : ");
			log.write(s);
			log.newLine();
			log.flush();
		}catch(Exception e){
			System.err.println("Failed to log");
		}
	}
	String filterToPrintableString(String s, int width){
		String ns=s.substring(0, 0);
		char lc=s.charAt(0);
		char clen=1;
		for(int i=1;i<s.length();i++){
			char cc=s.charAt(i);
			if(cc=='\n'){
				if(lc!='\n'){
					ns+=cc;
				}else{
					ns+=spaceStr(width);
					ns+='\n';
				}
				clen=0;
			}else if(clen>=width){
				ns+='\n';
				ns+=cc;
				clen=1;
			}else{
				ns+=cc;
				clen++;
			}
			lc=cc;
		}
		return ns;
	}
	String processReceipt(Ticket rd, Integer id) throws Exception {
		String r = "";
		int width = rd.getLineWidth()==null?560:rd.getLineWidth();
		if(rd.getMode()==null||rd.getMode().equals("receipt")){
			r += "<center><h4>"+rd.getTitle()+"</h4></center>\n\n";
			r += "Items:\n";
			if(rd.getTicketItems()!=null){
				for(TicketItem ti:rd.getTicketItems()){
					r+="------------------\n";
					r+=String.format("<h3>#%d %s%s</h3><right>%d * $%.2f</right>",
							ti.getId(),
							ti.getName(),
							ti.getPortion()==null?"":"."+ti.getPortion(),
							ti.getQuantity(),
							ti.getPrice());
					for(TicketItemProperty tip:ti.getProperties()){
						r+=String.format("-- %s<right>%d * $%.2f</right>", tip.getName(),tip.getQuantity(),tip.getPrice());
					}
				}
			}
			r+="------------------\n";
			r+=String.format("Subtotal: <right>%f</right>\n",rd.getSubtotal());
			if(rd.getTax()!=null)r+=String.format("Tax: <right>%f</right>\n",rd.getTax());
			if(rd.getServiceCharge()!=null&&rd.getServiceCharge()>0)r+=String.format("Service: <right>%f</right>\n",rd.getServiceCharge());
			if(rd.getDiscount()!=null&&rd.getDiscount()>0)r+=String.format("Discount: <right>%f</right>\n", rd.getDiscount());
			r+=String.format("Total: <right>%f</right>\n",rd.getTotal());
			
			if(rd.getType()=="online"){
				r += "Name: " + rd.getName() + "\n";
				r += "Phone: " + rd.getPhone() + "\n";
				r += "Email: " + rd.getEmail() + "\n";
				r += "Comments: <h3>" + rd.getComments() + "</h3>\n";
			}
			if(rd.getBarcode()!=null){
				r += "Barcode: "+rd.getBarcode()+"\n<barcode>"+rd.getBarcode()+"</barcode>\n";
			}
			r=parser.convert(r);
			//r=filterToPrintableString(r,width);
		}else if(rd.getMode().equals("tagged")){
			r += parser.convert(rd.getComments());
		}
		r += "\n\nPowered By PrintOS http://printos.io/\n";
		r += "Cloud printing & receipt ad solution\n\n";
		r += "Designed By PotatOS http://potatos.cc/\n";
		r += "A leading cloud restaurant system provider";
		r +="\n\n\n\n\n\n";
		r += cutCommand;
		//System.out.println(r);
		return r;
	}
	void postStatus(Integer id, String errorMessage, boolean reset, boolean printed, int errorCode){
		try{
			Poster poster=new Poster();
			poster.add("username", config.get("username"));
			poster.add("password", config.get("password"));
			poster.add("id", id.toString());
			poster.add("status", errorMessage==null||errorMessage.isEmpty()?" ":errorMessage);
			poster.add("reset",reset?"1":"0");
			poster.add("printed",printed?"1":"0");
			poster.add("error_code",Integer.toString(errorCode));
			URLConnection statusConnection=new URL(config.get("statusURL")).openConnection();
			poster.post(statusConnection);
			BufferedReader br=new BufferedReader(new InputStreamReader(statusConnection.getInputStream(),Charsets.UTF_8));
			String ret=readAll(br);
			System.out.println("ret="+ret);
			Response response=gson.fromJson(ret, Response.class);
			if(!response.pass){
				writeLog("Failed to update order status. Id="+id+"\nMessage from server=\n"+response.error);
			}
		}catch(Exception e){
			writeLog(e.toString());
			for(StackTraceElement s:e.getStackTrace())
				writeLog(s.toString());
		}
	}
	ArrayList<PrintService> serviceList;
	
	void selectPrinter(String name) {
		String[] retPrinters=name.split(";");
		ArrayList<String> requiredPrinters=new ArrayList<String>();
		for(int i=0;i<retPrinters.length;i++){
			requiredPrinters.add(retPrinters[i]);
			System.out.println("--Required "+retPrinters[i]);
		}
		HashSet<String> missingPrinters=new HashSet<String>();
		missingPrinters.addAll(requiredPrinters);
		PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
		serviceList = new ArrayList<PrintService>();
		for (int i = 0; i < services.length; i++) {
			System.out.println(services[i].getName());
			for(int ind=0;ind<requiredPrinters.size();ind++){
				String printerName=requiredPrinters.get(ind);
				if (services[i].getName().equals(printerName)) {
					serviceList.add(services[i]);
					missingPrinters.remove(printerName);
					writeLog("--Found "+printerName);
				}
			}
		}
		if (!missingPrinters.isEmpty()) {
			writeLog("Some of the configured printers ("+name+") doesn't exist.");
			for(int ind=0;ind<missingPrinters.size();ind++){
				String printerName=requiredPrinters.get(ind);
				writeLog("--"+printerName);
			}
			return;
		}else{
			PRINTER_NAME=name;
			if(PRINTER_NAME.equals("Icod-T90")){
				cutCommand=CUT_LEGACY;
			}else{
				cutCommand=CUT_EPSON;
			}
			System.out.println("We FOUND \"" + name + "\" !!!!!!!!!!!");
		}
	}
	

	
	static HashMap<String, String> readConfig(String filename) throws Exception {
		FileInputStream fis = new FileInputStream(filename);
		DataInputStream dis = new DataInputStream(fis);
		BufferedReader br = new BufferedReader(new InputStreamReader(dis));
		
		HashMap<String, String> r = new HashMap<String, String>();
		
		String line;
		while ((line = br.readLine()) != null) {
			if (line.trim().length() != 0 && line.trim().charAt(0) != '#') {
				int pos = line.indexOf('=');
				if (pos == -1) {
					br.close();
					fis.close();
					throw new Exception("Bad line in config file: " + filename);
				}
				r.put(line.substring(0, pos), line.substring(pos+1));
			}
		}
		br.close();
		fis.close();
		
		return r;
	}
	
	/* Poster:
		Simplifies POSTing. Just create a Poster and add(Name, Value) names and
		their values, then run(URL).
	*/
	static class Poster {
		String post;
		Poster() {
			post = "";
		}
		
		void add(String name, String value) {
			try {
				if (post != "") post += "&";
				post += URLEncoder.encode(name, "UTF-8") + "=" +
						URLEncoder.encode(value, "UTF-8");
			} catch (Exception e) {}
		}
		
		void post(URLConnection connection) throws Exception {
			connection.setDoOutput(true);
			OutputStreamWriter wr
				= new OutputStreamWriter(connection.getOutputStream());
			wr.write(post);
			wr.flush();
			wr.close();
		}
		
		void reset() {
			post = "";
		}
	}
	class JobStatusMonitor implements Runnable{
		Integer receiptId;
		Integer jobId;
		String printerName;
		int numChecks=30;
		Client client;
		public JobStatusMonitor(int _receiptId, int _jobId, String _printerName, Client client){
			jobId=_jobId;printerName=_printerName;receiptId=_receiptId;
			this.client=client;
		}
		public void run(){
			String fullName=printerName+"-"+jobId.toString();
			for(int i=0;i<numChecks;i++){
				try{
					
					String command="lpstat -W completed -P "+printerName+" |grep "+fullName;
					ProcessBuilder pb=new ProcessBuilder("bash","-c",command);
					//System.out.println("command="+command);
					Process p=pb.start();
				    BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream())); 
				    String output="";
				    String line=reader.readLine(); 
				    while(line!=null) 
				    { 
				    	output+=line;
					    line=reader.readLine(); 
				    }
				    p.waitFor();
				    if(!output.isEmpty()){
				    	postStatus(receiptId,"Completed",false,true,ERROR_NONE);
				    	client.printStackRemove(receiptId);
				    	return;
				    }
				    Thread.sleep(1500);
				}catch(Exception e){
					postStatus(receiptId,"Exception when checking status of job "+fullName, true, false, ERROR_OTHERS);
					client.printStackRemove(receiptId);
					writeLog(e.toString());
					for(StackTraceElement s:e.getStackTrace())
						writeLog(s.toString());
				}
			}
			postStatus(receiptId,"Failed after job expired. It could be the printer is offline, or run out of paper roll.",
					true, false, ERROR_PRINTER);
			client.printStackRemove(receiptId);
			writeLog(fullName+" waiting time expired. Deleting...");
			try{
				ProcessBuilder pb=new ProcessBuilder("bash","-c","cancel "+fullName);
				Process p=pb.start();
			    BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream())); 
			    String output="";
			    String line=reader.readLine(); 
			    while(line!=null) 
			    { 
			    	output+=line+"\n";
				    line=reader.readLine(); 
			    }
			    p.waitFor();
			    writeLog(output);
			}catch(Exception e){
				postStatus(receiptId,"Exception when cancelling job "+fullName,true,false,ERROR_OTHERS);
				writeLog(e.toString());
				for(StackTraceElement s:e.getStackTrace())
					writeLog(s.toString());
			}
		}
	}
	public void printStackRemove(Integer id){
		printStackLock.lock();
		try{
			if(printStack.contains(id)){
				printStack.remove(id);
			}
		}finally{
			printStackLock.unlock();
		}
	}
//	void doPrint(Ticket rd, Integer id){
//		if(rd==null)return;
//		printStackLock.lock();
//		try{
//			if(printStack.contains(id)){
//				return;
//			}else{
//				printStack.add(id);
//			}
//		}finally{
//			printStackLock.unlock();
//		}
//		try{
//			String s=processReceipt(rd,id);
//			System.out.println(s);
//			Files.write(s, new File(ROOT_DIR+"/piprt.tmp"),Charsets.US_ASCII);
//			ProcessBuilder pb=new ProcessBuilder("lp","-o","raw",ROOT_DIR+"/piprt.tmp");
//			Process p=pb.start();
//		    BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream())); 
//		    String output="";
//		    String line=reader.readLine(); 
//		    while(line!=null) 
//		    { 
//			    System.out.println(line); 
//			    output+=line+"\n";
//			    line=reader.readLine(); 
//		    }
//			int ret=p.waitFor();
//			int jobId=-1;
//			if(output.contains("request id is")){
//				String[] tempSplit=output.split(" ");
//				String fullIdName=tempSplit[3];
//				jobId=Integer.parseInt(fullIdName.substring(PRINTER_NAME.length()+1));
//				//System.out.println("jobid="+jobId);
////		    int ret=0;
//				if(ret!=0){
//					postStatus(id,"Printing device error. Needs on-site/remote investigation.",true,false,ERROR_OTHERS);
//					printStackRemove(id);
//				}else{
//					Thread mon=new Thread(new JobStatusMonitor(id,jobId,PRINTER_NAME, this));
//					mon.run();
//				}
//			}else{
//				postStatus(id,"Unexpected lp output. Needs on-site/remote investigation.",true,false,ERROR_OTHERS);
//				printStackRemove(id);
//			}
//		}catch(Exception e){
//			if(e instanceof TagParsingException){
//				postStatus(id,e.getClass().getName()+" "+e.getMessage(),false,false,ERROR_TAG);
//			}else{
//				postStatus(id,e.getClass().getName()+" "+e.getMessage(),true,false,ERROR_OTHERS);
//			}
//			writeLog(e.toString());
//			for(StackTraceElement s:e.getStackTrace())
//				writeLog(s.toString());
//			printStackRemove(id);
//		}
//		
//	}
	class Listener implements PrintJobListener{
		int id;
		public Listener(int id){
			this.id=id;
		}
		@Override
		public void printDataTransferCompleted(PrintJobEvent arg0) {
			
		}

		@Override
		public void printJobCanceled(PrintJobEvent arg0) {
			postStatus(id,"PrintJob cancelled by printer! "+arg0.toString(),false,false,ERROR_OTHERS);
			writeLog("PrintJob ["+id+"] cancelled by printer. msg:"+arg0.toString());
			printStackRemove(id);
		}

		@Override
		public void printJobCompleted(PrintJobEvent arg0) {
	    	postStatus(id,"Completed",false,true,ERROR_NONE);
	    	printStackRemove(id);
		}

		@Override
		public void printJobFailed(PrintJobEvent arg0) {
			postStatus(id,"PrintJob failed! "+arg0.toString() ,false,false,ERROR_OTHERS);
			writeLog("PrintJob ["+id+"] failed "+arg0.toString());
			printStackRemove(id);
		}

		@Override
		public void printJobNoMoreEvents(PrintJobEvent arg0) {
	    	postStatus(id,"Completed",false,true,ERROR_NONE);
	    	printStackRemove(id);
		}

		@Override
		public void printJobRequiresAttention(PrintJobEvent arg0) {
			postStatus(id,"PrintJob error! "+arg0.toString() ,false,false,ERROR_OTHERS);
			writeLog("PrintJob ["+id+"] error "+arg0.toString());
			printStackRemove(id);
		}
	}
	void doPrint(Ticket rd, Integer id){
		if(rd==null)return;
		printStackLock.lock();
		try{
			if(printStack.contains(id)){
				return;
			}else{
				printStack.add(id);
			}
		}finally{
			printStackLock.unlock();
		}
		try{
			String s=processReceipt(rd,id);
			
			DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
//			Files.write(s, new File(ROOT_DIR+"/piprt.tmp"),Charsets.US_ASCII);
//			FileInputStream fis = new FileInputStream("piprt.tmp");
			DocPrintJob printJob = serviceList.get(0).createPrintJob();
			printJob.addPrintJobListener(new Listener(id));
			Doc document = new SimpleDoc(new ByteArrayInputStream(s.getBytes(Charsets.US_ASCII)), flavor, null);
			
			printJob.print(document, null);
			
			
			System.out.println(s);
		}catch(Exception e){
			if(e instanceof TagParsingException){
				postStatus(id,e.getClass().getName()+" "+e.getMessage(),false,false,ERROR_TAG);
			}else{
				postStatus(id,e.getClass().getName()+" "+e.getMessage(),true,false,ERROR_OTHERS);
			}
			writeLog(e.toString());
			for(StackTraceElement s:e.getStackTrace())
				writeLog(s.toString());
			printStackRemove(id);
		}
		
	}
//	void doPrint(String s){
//		System.out.println(s);
//		try{
//			Files.write(s, new File(ROOT_DIR+"/piprt.tmp"),Charsets.US_ASCII);
//			ProcessBuilder pb=new ProcessBuilder("lp","-o","raw",ROOT_DIR+"/piprt.tmp");
//			Process p=pb.start();
//		    BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream())); 
//		    String line=reader.readLine(); 
//		    while(line!=null) 
//		    { 
//			    System.out.println(line); 
//			    line=reader.readLine(); 
//		    } 
//			int ret=p.waitFor();
//			if(ret!=0){
//				writeLog("Printing Error: ret="+ret+" order="+s);
//			}
//		}catch(Exception e){
//			writeLog(e.toString());
//		}
//	}
	public String stripSlashes(String str){
		str.replace("\\\"", "\"" );
		str.replace("\\\\", "\\");
		str.replace("\\\'", "\'");
		return str;
	}
	public void start(){
		while (true) {
			try {
				url = new URL(config.get("url"));
				connection = url.openConnection();
				
				Poster poster = new Poster();
				poster.add("username", config.get("username"));
				poster.add("password", config.get("password"));
				poster.add("version", "2");
				poster.post(connection);
				
				BufferedReader br=new BufferedReader(new InputStreamReader(connection.getInputStream(),Charsets.UTF_8));
				
				String ret=readAll(br);
				Response response=gson.fromJson(ret, Response.class);
				if(response.version>Client.version){
					//this.beginUpgrade();
				}
				if(response.pass){
					for(int i=0;i<response.data.length;i++){
						String receipt=response.data[i];
						Integer id=response.ids[i];
						Ticket rd=null;
						try{
							rd=gson.fromJson(stripSlashes(receipt), Ticket.class);
						}catch(Exception e){
							if(e instanceof JsonSyntaxException){
								postStatus(id,"Invalid receipt data. Bad JSON format", false,false,ERROR_JSON);
							}
							continue;
						}
						System.out.println("received id="+id.toString() +". Printing...");
						doPrint(rd,id);
						System.out.println("id="+id.toString());
					}
				} else if (response.pass==false) {
					writeLog("Login failed. Message from server:\n"+response.error);
					throw new Exception("Authentication Error");
				} else {
					throw new Exception("Server error: null authentication response");
				}
				Thread.sleep(sleeptime);
				br.close();
			} catch (Exception e) {
				writeLog(e.toString());
				for(StackTraceElement s:e.getStackTrace())
					writeLog(s.toString());
				try {
					Thread.sleep(sleeptime);
				} catch (InterruptedException e1) {
				}
			}
		}
	}
}
	
