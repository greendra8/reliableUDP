import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;



public class test {

    public static void main(String[] args) {
        File file = new File("input.txt");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[4];
            int bytesRead;

            // Read the file 4 bytes at a time
            while ((bytesRead = fis.read(bytes)) != -1) {
                // Process the bytes read; for example, you can print them
                for (int i = 0; i < bytesRead; i++) {
                    // This prints the byte as a hexadecimal string
                    System.out.print(String.format("%02X ", bytes[i]));
                }
                
                // Clear the buffer if bytesRead < 4 to prevent stale data on the last iteration
                if (bytesRead < bytes.length) {
                    for (int i = bytesRead; i < bytes.length; i++) {
                        bytes[i] = 0;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
