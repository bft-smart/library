/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.tom.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    //public static long startInstant = System.currentTimeMillis();
    public static boolean debug = false;

    public static void println(String msg) {
        if (debug) {
            String dataActual = new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date());
            System.out.println(
                    "(" + dataActual
                    + " - " + Thread.currentThread().getName()
                    + ") " + msg);
        }
    }

    public static void println2(java.util.logging.Logger l,String msg) {
        if (debug) {
            String dataActual = new SimpleDateFormat("HH:mm:ss:SSS").format(new Date());
            StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
            l.info(
                    "(" + dataActual
                    //+ " - " + Thread.currentThread().getName()
                    //+ " - " + stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()+":"+stackTraceElement.getLineNumber()
                    + ") " + msg);
        }
    }
}
