package printos.client;

/**
 * Created by polymorpher on 10/21/14.
 */
public interface AbstractTagParser {
    public String convert(String str) throws Exception;
    public String convert(String s, Integer width) throws Exception;
}
