package navigators.smart.tests.util;


import java.util.Random;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class TestHelper {
	
	private static final Random rnd = new Random();
    
    public static byte[] createTestByte(){
    	byte[] tmp = {3,1,2,4};
    	return tmp;
    }

}
