package printos.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.*;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by polymorpher on 7/02/15.
 */
public class TestPrint {
    static final Logger logger = LoggerFactory.getLogger(TestPrint.class);

    public static void main(String[] args) throws Exception {
//        String s = processPrintData(data);
        Client client = new Client();
        client.selectPrinter("EPSON_TM-T20");

        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        DocPrintJob printJob = client.serviceList.get(0).createPrintJob();
//        printJob.addPrintJobListener(new Listener());
        Doc document = new SimpleDoc(new ByteArrayInputStream(Files.readAllBytes(Paths.get("test2"))), flavor, null);
        printJob.print(document, null);
        logger.info("Printed");
    }
}
