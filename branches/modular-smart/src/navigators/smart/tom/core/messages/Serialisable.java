package navigators.smart.tom.core.messages;

import java.nio.ByteBuffer;

public interface Serialisable {
	
	public void serialise(ByteBuffer buf);
	
	public int getMsgSize();

}
