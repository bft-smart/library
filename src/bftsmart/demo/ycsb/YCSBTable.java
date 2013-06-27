package bftsmart.demo.ycsb;

import java.io.Serializable;
import java.util.HashMap;
import java.util.TreeMap;

public class YCSBTable extends TreeMap<String, HashMap<String, byte[]>> implements Serializable {
	private static final long	serialVersionUID	= 3786544460082473686L;
}
