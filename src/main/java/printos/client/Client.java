package printos.client;

import java.io.*;
import java.util.HashMap;
import java.util.*;

import com.google.gson.*;

import java.nio.charset.Charset;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;

import printos.client.util.*;
//import com.google.common.base.Charsets;
//import com.google.common.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.ning.http.client.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import printos.client.PrinterUtil;

public class Client {
    static final Logger logger = LoggerFactory.getLogger(Client.class);
    public static String configFile = "PrintOSconfig.ini";
    static int ERROR_NONE = 0;
    static int ERROR_TAG = 2;
    static int ERROR_PRINTER = 3;
    static int ERROR_PRINT_JOB_ATTENTION = 4;
    static int ERROR_OTHERS = 1337;
    static int MAXIMUM_WAIT_MILLIS_BEFORE_SHUTDOWN = 30000;
    static AtomicInteger numListeners = new AtomicInteger(0);
    static List<PrintProcessor> registeredProcessors = new ArrayList<>();

    String bleepSoundPath;
    Map<UUID, String> printJobs = new ConcurrentHashMap<UUID, String>();
    Map<String, String> config = new HashMap<String, String>();
    AbstractTagParser parser;
    int sleeptime = 1000;
    Gson gson = new Gson();
    String PRINTER_NAME = "";
    String CUT_EPSON = "" + (char) 29 + 'V' + (char) 0;
    String CUT_LEGACY = "" + (char) 27 + 'm';
    String cutCommand = CUT_EPSON;
    int defaultLineWidth = 560;
    List<Cookie> cookieMonster = new ArrayList<Cookie>();
    DateTime cookieMonsterLastUpdate = DateTime.now();
    LoginInfo loginInfo;
    Long lastLookupTime = null;
    AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
    ArrayList<PrintService> serviceList;
    ArrayList<PrintProcessor> usbPrintProcessors;

    public Client() {
        try {
            config = readConfig(configFile);
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
            bleepSoundPath = "/beep.wav";
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

    boolean hasSystemPrinter() {
        return serviceList.size() > 0;
    }

    static <T> java.util.List<T> convert(scala.collection.Seq<T> seq) {
        return scala.collection.JavaConversions.seqAsJavaList(seq);
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            configFile = args[0];
        }
        addShutdownHook();
        PrinterUtil.init();
        Client c = new Client();
        if (!c.hasSystemPrinter()) {
            logger.info("No available system printer. Initialising USB module");
            try {
                List<PrinterUSBInfo> infos = convert(PrinterUtil.findPrinters());
                if (infos.size() == 0) {
                    logger.info("USB Module cannot find any USB Printer. Exiting.");
                    System.exit(1);
                }
                c.usbPrintProcessors = new ArrayList<>();
                for (PrinterUSBInfo info : infos) {
                    PrintProcessor pp = new PrintProcessor(info);
                    c.usbPrintProcessors.add(pp);
                    registeredProcessors.add(pp);
                    pp.run();
                }
            } catch (Exception e) {
                logger.error(getStackTrace(e));
            }
        }
        c.start();
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
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

    public static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.info("Shutting down...");
                try {
                    for (PrintProcessor pp : registeredProcessors) {
                        pp.shut();
                    }
                } catch (Exception e) {
                    logger.error("Error during cleaning up bound USB printers: " + e.getMessage());
                }
                Long now = System.currentTimeMillis();
                try {
                    while (numListeners.get() > 0) {
                        if (System.currentTimeMillis() - now > MAXIMUM_WAIT_MILLIS_BEFORE_SHUTDOWN) {
                            break;
                        } else {
                            Thread.sleep(1000);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error during shutdown: " + e.getMessage());
                }
                logger.info("Shutdown complete.");
            }
        });
    }

    private void loginToServer() throws Exception {

//        Gson gson = new Gson();
        String body = gson.toJson(loginInfo);

        Future<Response> fres = asyncHttpClient.preparePost(config.get("login_api")).setBody(body)
                .setFollowRedirects(false).setHeader("Content-Type", "application/json").execute();
        Response res = fres.get();
        int rcode = res.getStatusCode();
        List<Cookie> cookies = res.getCookies();

        if (cookies.size() > 0) {
            logger.info("cookieMonster:" + cookieMonster.toString());
            cookieMonster = cookies;
            cookieMonsterLastUpdate = DateTime.now();
            logger.info("Updateing cookieMonster to:" + cookieMonster.toString());
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
            logger.info("Parsing response:" + res.getResponseBody());
            return gson.fromJson(res.getResponseBody(), PrintJobQueue.class);
        } else {
            logger.error("Error on lookup [" + rcode + "] " + res.getResponseBody());
            return getJobs(numAttempts - 1);
        }
    }

    private PrintJobQueue getJobs() throws Exception {
        return getJobs(1);
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
        r += parser.convert(data, defaultLineWidth);
        r += "\n\n\n\n\n\n";
        r += cutCommand;
        return r;
    }

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
        System.out.println("Printers in service:");
        System.out.println(serviceList);
        if (!missingPrinters.isEmpty()) {
            logger.error("Some of the configured printers (" + name + ") doesn't exist.");
            for (int ind = 0; ind < missingPrinters.size(); ind++) {
                String printerName = requiredPrinters.get(ind);
                logger.error("--" + printerName);
            }
            parser = new ESCPOSTagParser();
            return;
        } else {
            PRINTER_NAME = name;
            if (PRINTER_NAME.toLowerCase().equals("icod-t90")) {
                cutCommand = CUT_LEGACY;
            } else {
                cutCommand = CUT_EPSON;
            }
            if (PRINTER_NAME.toLowerCase().contains("dascom") && PRINTER_NAME.contains("2610")) {
                parser = new ESCPTagParser();
                defaultLineWidth = 800;
            } else {
                parser = new ESCPOSTagParser();
            }
            System.out.println("We FOUND \"" + name + "\" !!!!!!!!!!!");
        }
    }

    void doPrintSystem(String s, UUID id) throws Exception {
        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        DocPrintJob printJob = serviceList.get(0).createPrintJob();
        printJob.addPrintJobListener(new Listener(id));
        numListeners.incrementAndGet();
        Doc document = new SimpleDoc(new ByteArrayInputStream(s.getBytes(Charset.forName("ASCII"))), flavor, null);
        printJob.print(document, null);
    }

    void doPrintUSB(String data, UUID id) throws Exception {
        for (PrintProcessor pp : this.usbPrintProcessors) {
            pp.addJob(data.getBytes(), new USBListener(id));
            numListeners.incrementAndGet();
        }
    }

    void doPrint(String data, UUID id) {
        logger.info("Printing id=" + id.toString());
        try {
            String s = processPrintData(data);
            if (this.hasSystemPrinter()) {
                doPrintSystem(s, id);
            } else {
                doPrintUSB(s, id);
            }
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
                        if (!printJobs.containsKey(job.id)) {
                            printJobs.put(job.id, job.data);
                        }
//                        doPrint(job.data, job.id);
                    }
                }
                for (Map.Entry<UUID, String> kv : printJobs.entrySet()) {
                    doPrint(kv.getValue(), kv.getKey());
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

    class USBListener implements USBListenerInterface {
        UUID id;

        public USBListener(UUID id) {
            this.id = id;
        }

        public void complete() {
            JobUpdate u = new JobUpdate(id, "SentToPrinter", ERROR_NONE, false, true);
            logger.info("PrintJob sent to printer: " + id.toString());
            updatePrintJob(u);
            try {
                SoundPlayer.playSound(this.getClass().getResource(bleepSoundPath));
            } catch (Exception e) {
                logger.error("Unable to play sound: " + e.getMessage());
                e.printStackTrace();
            }
            printJobs.remove(id);
            numListeners.decrementAndGet();
        }

        public void error(String reason) {
            JobUpdate u = new JobUpdate(id, "[Failed] " + reason, ERROR_PRINTER, true, false);
            updatePrintJob(u);
            numListeners.decrementAndGet();
            logger.error("PrintJob [" + id + "] failed " + reason);
        }
    }

    class Listener implements PrintJobListener {
        UUID id;
        Boolean eventAlreadyFired;

        public Listener(UUID id) {
            this.id = id;
            eventAlreadyFired = false;
        }

        void close() {
            numListeners.decrementAndGet();
        }

        @Override
        public void printDataTransferCompleted(PrintJobEvent arg0) {

        }

        @Override
        public void printJobCanceled(PrintJobEvent arg0) {
            synchronized (eventAlreadyFired) {
                if (eventAlreadyFired) return;
                eventAlreadyFired = true;
            }
            JobUpdate u = new JobUpdate(id, "PrintJob cancelled by printer! " + arg0.toString(),
                    ERROR_OTHERS, true, false);
            updatePrintJob(u);
            logger.info("PrintJob [" + id + "] cancelled by printer. msg:" + arg0.toString());
//            printJobs.remove(id);
            close();

        }

        @Override
        public void printJobCompleted(PrintJobEvent arg0) {
            synchronized (eventAlreadyFired) {
                if (eventAlreadyFired) return;
                eventAlreadyFired = true;
            }
            JobUpdate u = new JobUpdate(id, "Completed",
                    ERROR_NONE, false, true);

            logger.info("PrintJob Completed: " + id.toString());
            updatePrintJob(u);
            try {
                SoundPlayer.playSound(this.getClass().getResource(bleepSoundPath));
            } catch (Exception e) {
                logger.error("Unable to play sound: " + e.getMessage());
                e.printStackTrace();
            }
            printJobs.remove(id);
            close();

        }

        @Override
        public void printJobFailed(PrintJobEvent arg0) {
            synchronized (eventAlreadyFired) {
                if (eventAlreadyFired) return;
                eventAlreadyFired = true;
            }
            JobUpdate u = new JobUpdate(id, "Failed: " + arg0.toString(), ERROR_PRINTER, true, false);
            updatePrintJob(u);
            logger.error("PrintJob [" + id + "] failed " + arg0.toString());
//            printJobs.remove(id);
            close();

        }

        @Override
        public void printJobNoMoreEvents(PrintJobEvent arg0) {
            synchronized (eventAlreadyFired) {
                if (eventAlreadyFired) return;
                eventAlreadyFired = true;
            }
            JobUpdate u = new JobUpdate(id, "Completed", ERROR_NONE, false, true);

            logger.info("PrintJob Completed (No more event): " + id.toString());
            updatePrintJob(u);
            try {
                SoundPlayer.playSound(this.getClass().getResource(bleepSoundPath));
            } catch (Exception e) {
                logger.error("Unable to play sound: " + e.getMessage());
                e.printStackTrace();
            }
            printJobs.remove(id);
            close();
        }

        @Override
        public void printJobRequiresAttention(PrintJobEvent arg0) {
            synchronized (eventAlreadyFired) {
                if (eventAlreadyFired) return;
                eventAlreadyFired = true;
            }
            JobUpdate u = new JobUpdate(id, "Error: " + arg0.toString(), ERROR_PRINT_JOB_ATTENTION, true, false);
            updatePrintJob(u);
            logger.info("PrintJob [" + id + "] error " + arg0.toString());
//            printJobs.remove(id);
            close();
        }
    }

}

