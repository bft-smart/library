package bftsmart.tests.recovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author Robin
 */
public class ErrorPrinter extends Thread {
    private final InputStream errorStream;

    ErrorPrinter(InputStream errorStream) {
        super("Error Printer Thread");
        this.errorStream = errorStream;
    }

    @Override
    public void run() {
        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(errorStream))) {
            String line;
            while ((line = in.readLine()) != null) {
                System.err.println(line);
            }
        } catch (IOException ignored) {}
    }
}
