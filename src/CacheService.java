import java.util.*;
/**
 * CacheService class manages a cache with a maximum size and implements an LRU (Least Recently Used) eviction policy.
 * @param <K> Key type for the cache
 * @param <V> Value type for the cache
 */
public class CacheService<K, V> {
    private final LinkedHashMap<K, V> cache; // Internal storage for the cache
    private final int maxSize; // Maximum size of the cache

    // Constructor initializes the CacheService with a specified maximum size.
    public CacheService(int maxSize) {
        this.maxSize = maxSize;
        // Initialize a LinkedHashMap with access-order and a removal policy
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            // Override removeEldestEntry to enforce the maximum size
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Retrieves the value associated with the specified key from the cache.
     * @param key Key to search for in the cache
     * @return Value associated with the key, or null if not found
     */
    private synchronized V getElement(K key) {
        performCacheCleanup(); // Clean up the cache before retrieval
        return cache.get(key);
    }

    /**
     * Puts a key-value pair into the cache.
     * @param key Key for the cache entry
     * @param value Value associated with the key in the cache
     */
    private synchronized void putElement(K key, V value) {
        performCacheCleanup(); // Clean up the cache before insertion
        cache.put(key, value);
    }

    // Removes the least recently used (LRU) entry from the cache.
    public void evict() {
        Iterator<Map.Entry<K, V>> iterator = cache.entrySet().iterator();
        if (iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();
            iterator.remove();
            System.out.println("Evicted: " + entry.getKey()); // Log the eviction
        }
    }

    // Ensures the cache size does not exceed the maximum size by performing cache cleanup.
    private synchronized void performCacheCleanup() {
        while (cache.size() >= maxSize) {
            evict(); // Calls the evict() method to remove an element
        }
    }

    /**
     * Handles a read request from the server by retrieving the value associated with the key.
     * @param key Key to search for in the cache
     * @return Value associated with the key, fetched for server read request
     */
    public synchronized V handleServerReadRequest(K key) {
        System.out.println("Handling server read request for key: " + key);
        return getElement(key);
    }

    /**
     * Handles a write request from the server by storing the key-value pair in the cache.
     * @param key Key for the cache entry
     * @param value Value associated with the key for server write request
     */
    public synchronized void handleServerWriteRequest(K key, V value) {
        System.out.println("Handling server write request for key: " + key);
        putElement(key, value);
    }

    /**
     * Handles a removal request from the server by removing elements associated with the specified filename.
     * @param fileName Filename for which associated elements need to be removed
     */
    public synchronized void handleServerRemovalRequest(String fileName) {
        System.out.println("Handling server removal request");
        Iterator<Map.Entry<K, V>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();
            K key = entry.getKey();
            if (key.toString().startsWith(fileName + ",")) {
                iterator.remove(); // Removes the associated entry
            }
        }
    }

    // Prints the contents of the cache (for debugging/testing purposes).
    public synchronized void printCacheContents() {
        for (Map.Entry<K, V> entry : cache.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            System.out.println("Key: " + key + ", Value: " + value);
        }
    }
}
