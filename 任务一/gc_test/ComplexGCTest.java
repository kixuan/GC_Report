import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ComplexGCTest {
    private static final int OBJECT_SIZE = 1024 * 1024; // 1MB
    private static final int NUM_OBJECTS = 50000; // Number of objects to create
    private static final int NUM_THREADS = 8; // Number of concurrent threads

    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        Random random = new Random();

        // Create threads to simulate concurrent memory allocation
        for (int t = 0; t < NUM_THREADS; t++) {
            new Thread(() -> {
                for (int i = 0; i < NUM_OBJECTS; i++) {
                    if (random.nextBoolean()) {
                        list.add(new byte[OBJECT_SIZE]); // Allocate memory
                    } else {
                        synchronized (list) {
                            if (!list.isEmpty()) {
                                list.remove(random.nextInt(list.size())); // Deallocate memory
                            }
                        }
                    }
                    if (i % 100 == 0) {
                        try {
                            Thread.sleep(10); // Simulate work
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }).start();
        }

        // Keep the main thread alive to allow threads to finish
        try {
            Thread.sleep(60000); // Run for 60 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
