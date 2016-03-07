package printos.client;

/**
 * Created by polymorpher on 3/7/16.
 */
public interface USBListenerInterface {

    void complete();

    void error(String reason);
}
