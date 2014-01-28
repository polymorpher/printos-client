package potatos.client.rpi.printing;

public class BadFormatException extends TagParsingException{
	/**
	 * 
	 */
	private static final long serialVersionUID = -709112460842476075L;

	public BadFormatException(String message){
		super(message);
	}
}
