package navigators.smart.tom.server;

/**
 * 
 * @author mhsantos
 *
 */
public interface Recoverable {
	
	public byte[] getState();
	public void setState(byte[] state);
	
}
