package potatos.client.rpi.printing;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.util.*;

import com.fatboyindustrial.gsonjodatime.Converters;
import com.google.gson.*;

import java.text.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;

import potatos.client.util.*;
//import com.google.common.base.Charsets;
//import com.google.common.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.*;

import potatos.client.model.*;
import com.ning.http.client.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
    //	static String ROOT_DIR="/home/pi";
    //static String ROOT_DIR="/home/polymorpher/workspace-javaee/PotatOSCloudPrintClient";
    static String configfile = "PrintOSconfig.ini";
    static final Logger logger = LoggerFactory.getLogger(Client.class);
    static int ERROR_NONE = 0;
    static int ERROR_JSON = 1;
    static int ERROR_TAG = 2;
    static int ERROR_PRINTER = 3;
    static int ERROR_OTHERS = 1337;
    static int version = 6;
    static int MAX_ATTEMPTS = 5;

    String bleepSoundPath;
//    public Set<UUID> printStack = new HashSet<UUID>();
//    public ReentrantLock printStackLock = new ReentrantLock();
    Map<UUID,String> printJobs=new ConcurrentHashMap<UUID,String>();
    Map<String, String> config = new HashMap<String, String>();
    AbstractTagParser parser;
    int sleeptime = 1000;
//    Gson gson = Converters.registerDateTime(new GsonBuilder()).create();
    Gson gson=new Gson();
    String PRINTER_NAME = "";
    String CUT_EPSON = "" + (char) 29 + 'V' + (char) 0;
    String CUT_LEGACY = "" + (char) 27 + 'm';
    String cutCommand = "";

    List<Cookie> cookieMonster = new ArrayList<Cookie>();
    DateTime cookieMonsterLastUpdate = DateTime.now();
    LoginInfo loginInfo;
    Long lastLookupTime = null;

    public static void main(String[] args) {
        Client c = new Client();
        c.start();
    }

    static class PrintJob {
        UUID id;
        String data;
    }

    static class PrintJobQueue {
        PrintJob[] jobs;
        Long timestamp;
    }

    static class JobUpdate {
        UUID id;
        String status;
        Integer errorCode;
        Boolean keepActive;
        Boolean printed;

        public JobUpdate(UUID id, String status, Integer errorCode, Boolean keepActive, Boolean printed) {
            this.id = id;
            this.status = status;
            this.errorCode = errorCode;
            this.printed = printed;
            this.keepActive = keepActive;
        }

        public String toString() {
            return "[id: " + id.toString() +
                    ", status: " + status +
                    " errorCode: " + errorCode.toString() +
                    " printed: " + printed.toString() + "]";
        }
    }

    static class LoginInfo {
        String userUsername;
        String destinationUsername;
        String password;

        public LoginInfo(String user, String dest, String pass) {
            userUsername = user;
            destinationUsername = dest;
            password = pass;
        }
    }

    private void loginToServer() throws Exception {

//        Gson gson = new Gson();
        String body = gson.toJson(loginInfo);
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        Future<Response> fres = asyncHttpClient.preparePost(config.get("login_api")).setBody(body)
                .setFollowRedirects(false).setHeader("Content-Type","application/json").execute();
        Response res = fres.get();
        int rcode = res.getStatusCode();
        List<Cookie> cookies = res.getCookies();

        if(cookies.size()>0) {
            logger.info("cookieMonster:"+cookieMonster.toString());
            cookieMonster = cookies;
            cookieMonsterLastUpdate = DateTime.now();
            logger.info("Updateing cookieMonster to:"+cookieMonster.toString());
        }
        if (rcode != 200) {
            logger.error("Error on login: [" + String.valueOf(rcode) + "] " + res.getResponseBody());
        } else {
            logger.info("Login successful. ");
        }
    }

    private void updatePrintJob(JobUpdate u, int numAttempts)
            throws Exception {
        if (numAttempts == 0) {
            return;
        }
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        String body = gson.toJson(u);
        AsyncHttpClient.BoundRequestBuilder builder = asyncHttpClient.preparePost(config.get("update_api"))
                .setBody(body).setFollowRedirects(true).setHeader("Content-Type", "application/json");
        for (Cookie cookie : cookieMonster) {
            builder = builder.addCookie(cookie);
        }
        Future<Response> fres = builder.setBody(body).execute();
        Response res = fres.get();
        int rcode = res.getStatusCode();
        if (rcode == 404) {
            logger.error("Login failure on update [" + rcode + "] " + res.getResponseBody());
            loginToServer();
            updatePrintJob(u, numAttempts - 1);
        } else if (rcode != 200) {
            logger.error("Error on update: [" + String.valueOf(rcode) + "] " + res.getResponseBody());
            updatePrintJob(u, numAttempts - 1);
        } else {
            logger.info("Update successful.");
        }
    }

    private void updatePrintJob(JobUpdate u) {
        try {
            updatePrintJob(u, 5);
        } catch (Exception e) {
            logger.info(getStackTrace(e));
        }
    }

    private PrintJobQueue getJobs(int numAttempts)
            throws Exception {
        if (numAttempts == 0) return null;
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
        AsyncHttpClient.BoundRequestBuilder builder;
        Future<Response> fres = null;
        if (lastLookupTime == null) {
            builder = asyncHttpClient.prepareGet(config.get("lookup_api")).setFollowRedirects(true);
        } else {
            builder = asyncHttpClient.prepareGet(config.get("lookupafter_api") +
                        "/" + new DateTime(lastLookupTime).toString())
                    .setFollowRedirects(true);
        }
        for (Cookie cookie : cookieMonster) {
            builder = builder.addCookie(cookie);
        }
        fres = builder.execute();
        Response res = fres.get();
        int rcode = res.getStatusCode();
        if (rcode == 404) {
            logger.error("Login failure on lookup [" + rcode + "] " + res.getResponseBody());
            loginToServer();
            return getJobs(numAttempts - 1);
        } else if (rcode == 200) {
            logger.info("Parsing response:"+res.getResponseBody());
            return gson.fromJson(res.getResponseBody(), PrintJobQueue.class);
        } else {
            logger.error("Error on lookup [" + rcode + "] " + res.getResponseBody());
            return getJobs(numAttempts - 1);
        }
    }

    private PrintJobQueue getJobs() throws Exception {
        return getJobs(MAX_ATTEMPTS);
    }

//    //member function!
//    private void updateAndLogin(JobUpdate u, int numAttemptsRemaining) {
//        if (numAttemptsRemaining == 0) {
//            logger.error("Update-login exceeds maximum number of attempts");
//            return;
//        }
//        if (cookieMonster != null) {
//            int rcode = -1;
//            try {
//                rcode = updatePrintJob(u, cookieMonster);
//            } catch (Exception e) {
//                logger.error(getStackTrace(e));
//            }
//            if (rcode != 200) {
//                updateAndLogin(u, numAttemptsRemaining - 1);
//            } else {
//                logger.debug("Update succeeded " + u.toString());
//                return;
//            }
//        } else {
//            try {
//                cookieMonster = loginToServer(loginInfo);
//            } catch (Exception e) {
//                logger.error(getStackTrace(e));
//            }
//            updateAndLogin(u, numAttemptsRemaining - 1);
//        }
//    }
//
//    private void updateAndLogin(JobUpdate u) {
//        updateAndLogin(u, 5);
//    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    Client() {
        try {
            config = readConfig(configfile);
        } catch (Exception e) {
            logger.error(e.toString());
            System.exit(1);
        }

        if (config.containsKey("sleep")) {
            sleeptime = Integer.parseInt(config.get("sleep")) * 1000;
        }
        if (config.containsKey("sound")) {
            bleepSoundPath = config.get("sound");
        } else {
            bleepSoundPath = "bell.wav";
        }
        if (config.containsKey("printer")) {
            selectPrinter(config.get("printer"));
        } else {
            logger.error("Printer is not defined in config file. Syntax: printer=aaa;bbb;...");
            System.exit(2);
        }
        loginInfo = new LoginInfo(config.get("user"), config.get("dest"), config.get("pass"));
        try {
            loginToServer();
        } catch (Exception e) {
            logger.error(getStackTrace(e));
        }
    }

    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static String spaceStr(int len) {
        return new String(new char[len]).replace("\0", ".");
    }

    String filterToPrintableString(String s, int width) {
        String ns = s.substring(0, 0);
        char lc = s.charAt(0);
        char clen = 1;
        for (int i = 1; i < s.length(); i++) {
            char cc = s.charAt(i);
            if (cc == '\n') {
                if (lc != '\n') {
                    ns += cc;
                } else {
                    ns += spaceStr(width);
                    ns += '\n';
                }
                clen = 0;
            } else if (clen >= width) {
                ns += '\n';
                ns += cc;
                clen = 1;
            } else {
                ns += cc;
                clen++;
            }
            lc = cc;
        }
        return ns;
    }

    String processPrintData(String data) throws Exception {
        String r = "";
        r += parser.convert(data);
        r += "\n\n\n\n\n\n";
        r += cutCommand;
        return r;
    }

    ArrayList<PrintService> serviceList;

    void selectPrinter(String name) {
        String[] retPrinters = name.split(";");
        ArrayList<String> requiredPrinters = new ArrayList<String>();
        for (int i = 0; i < retPrinters.length; i++) {
            requiredPrinters.add(retPrinters[i]);
            System.out.println("--Required " + retPrinters[i]);
        }
        HashSet<String> missingPrinters = new HashSet<String>();
        missingPrinters.addAll(requiredPrinters);
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        serviceList = new ArrayList<PrintService>();
        for (int i = 0; i < services.length; i++) {
            System.out.println(services[i].getName());
            for (int ind = 0; ind < requiredPrinters.size(); ind++) {
                String printerName = requiredPrinters.get(ind);
                if (services[i].getName().equals(printerName)) {
                    serviceList.add(services[i]);
                    missingPrinters.remove(printerName);
                    logger.info("--Found " + printerName);
                }
            }
        }
        if (!missingPrinters.isEmpty()) {
            logger.error("Some of the configured printers (" + name + ") doesn't exist.");
            for (int ind = 0; ind < missingPrinters.size(); ind++) {
                String printerName = requiredPrinters.get(ind);
                logger.error("--" + printerName);
            }
            return;
        } else {
            PRINTER_NAME = name;
            if (PRINTER_NAME.equals("Icod-T90")) {
                cutCommand = CUT_LEGACY;
            } else {
                cutCommand = CUT_EPSON;
            }
            if(PRINTER_NAME.contains("Dascom")&&PRINTER_NAME.contains("2610")){
                parser=new ESCPTagParser();
            }else{
                parser=new ESCPOSTagParser();
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
                r.put(line.substring(0, pos), line.substring(pos + 1));
            }
        }
        br.close();
        fis.close();

        return r;
    }

//    public void printStackRemove(UUID id) {
//        printStackLock.lock();
//        try {
//            if (printStack.contains(id)) {
//                printStack.remove(id);
//            }
//        } finally {
//            printStackLock.unlock();
//        }
//    }

    class Listener implements PrintJobListener {
        UUID id;

        public Listener(UUID id) {
            this.id = id;
        }

        @Override
        public void printDataTransferCompleted(PrintJobEvent arg0) {

        }

        @Override
        public void printJobCanceled(PrintJobEvent arg0) {
            JobUpdate u = new JobUpdate(id, "PrintJob cancelled by printer! " + arg0.toString(),
                    ERROR_OTHERS, true, false);
            updatePrintJob(u);
            logger.info("PrintJob [" + id + "] cancelled by printer. msg:" + arg0.toString());
//            printJobs.remove(id);
        }

        @Override
        public void printJobCompleted(PrintJobEvent arg0) {
            JobUpdate u = new JobUpdate(id, "Completed",
                    ERROR_NONE, false, true);
            updatePrintJob(u);
            logger.info("PrintJob Completed: " + id.toString());
            try {
                SoundPlayer.playSound(this.getClass().getResource(bleepSoundPath));
            } catch (Exception e) {
                logger.error("Unable to play sound: " + e.getMessage());
                e.printStackTrace();
            }
            printJobs.remove(id);
        }

        @Override
        public void printJobFailed(PrintJobEvent arg0) {
            JobUpdate u = new JobUpdate(id, "PrintJob failed! " + arg0.toString(), ERROR_OTHERS, true, false);
            logger.error("PrintJob [" + id + "] failed " + arg0.toString());
//            printJobs.remove(id);
        }

        @Override
        public void printJobNoMoreEvents(PrintJobEvent arg0) {
            JobUpdate u = new JobUpdate(id, "Completed", ERROR_NONE, false, true);
            updatePrintJob(u);
            logger.info("PrintJob Completed: " + id.toString());
            try {
                SoundPlayer.playSound(this.getClass().getResource(bleepSoundPath));
            } catch (Exception e) {
                logger.error("Unable to play sound: " + e.getMessage());
                e.printStackTrace();
            }
            printJobs.remove(id);
        }

        @Override
        public void printJobRequiresAttention(PrintJobEvent arg0) {
            JobUpdate u = new JobUpdate(id, "PrintJob error! " + arg0.toString(), ERROR_OTHERS, true, false);
            logger.info("PrintJob [" + id + "] error " + arg0.toString());
//            printJobs.remove(id);
        }
    }

    void doPrint(String data, UUID id) {
        logger.info("Printing id="+id.toString());
        try {
            String s = processPrintData(data);
            DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
            DocPrintJob printJob = serviceList.get(0).createPrintJob();
            printJob.addPrintJobListener(new Listener(id));
            Doc document = new SimpleDoc(new ByteArrayInputStream(s.getBytes(Charset.forName("ASCII"))), flavor, null);
            printJob.print(document, null);
            logger.info("Printed: " + data);
        } catch (Exception e) {
            if (e instanceof TagParsingException) {
                updatePrintJob(new JobUpdate(id, getStackTrace(e), ERROR_TAG, false, false));
                printJobs.remove(id);
            } else {
                updatePrintJob(new JobUpdate(id, getStackTrace(e), ERROR_OTHERS, true, false));
            }
            logger.error("Print failure (id=" + id.toString() + ") due to the following exception:" + e.toString());

        }
    }

    public void start() {
        while (true) {
            try {
                PrintJobQueue queue = getJobs();
                if (queue != null) {
                    lastLookupTime = queue.timestamp;
                    for (PrintJob job : queue.jobs) {
                        if(!printJobs.containsKey(job.id)){
                            printJobs.put(job.id,job.data);
                        }
//                        doPrint(job.data, job.id);
                    }
                }
                for(Map.Entry<UUID,String> kv:printJobs.entrySet()){
                    doPrint(kv.getValue(),kv.getKey());
                }
                Thread.sleep(sleeptime);
            } catch (Exception e) {
                logger.error(getStackTrace(e));
                try {
                    Thread.sleep(sleeptime);
                } catch (InterruptedException e1) {
                    logger.error(getStackTrace(e1));
                }
            }
        }
    }
}

