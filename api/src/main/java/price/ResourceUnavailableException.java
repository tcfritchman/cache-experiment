package price;

public class ResourceUnavailableException extends Exception {

	private static final long serialVersionUID = 1L;

	public String toString() {
		return "Connection to resource failed.";
	}
}
 