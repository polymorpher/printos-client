package printos.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import printos.client.ESCPOSTagParser;
import printos.client.ESCPTagParser;

import javax.print.*;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/**
 * Created by polymorpher on 7/02/15.
 */
public class TestPrint {
    static String PRINTER_NAME="";
    static final Logger logger = LoggerFactory.getLogger(TestPrint.class);
    static ArrayList<PrintService> serviceList;

    static void selectPrinter(String name) {
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
            System.out.println("We FOUND \"" + name + "\" !!!!!!!!!!!");
        }
    }

    public static void main(String[] args) throws Exception {
//        String s = processPrintData(data);
        selectPrinter("EPSON_TM-T20");
        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        DocPrintJob printJob = serviceList.get(0).createPrintJob();
//        printJob.addPrintJobListener(new Listener());
        Doc document = new SimpleDoc(new ByteArrayInputStream(Files.readAllBytes(Paths.get("test2"))), flavor, null);
        printJob.print(document, null);
        logger.info("Printed");
    }

    class Listener implements PrintJobListener {


        @Override
        public void printDataTransferCompleted(PrintJobEvent arg0) {

        }

        @Override
        public void printJobCanceled(PrintJobEvent arg0) {


        }

        @Override
        public void printJobCompleted(PrintJobEvent arg0) {


        }

        @Override
        public void printJobFailed(PrintJobEvent arg0) {


        }

        @Override
        public void printJobNoMoreEvents(PrintJobEvent arg0) {

        }

        @Override
        public void printJobRequiresAttention(PrintJobEvent arg0) {

        }
    }
}
