package com.minicloud.api.messaging;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Ultra-high-throughput lock-free circular ring buffer queue.
 *
 * Designed for extreme messaging throughput (>1,000,000 ops/sec) with zero thread-locking,
 * zero GC allocation during message transport, and mechanical sympathy for CPU cache lines.
 *
 * Principles:
 * 1. Power-of-two bounded capacity for fast bitwise modulo: (sequence & mask).
 * 2. Independent AtomicLong sequence cursors for producers (writeSequence) and consumers (readSequence).
 * 3. Lock-free slot resolution via AtomicReferenceArray CAS.
 *
 * @param <T> Element type stored in the ring buffer
 */
public class LockFreeRingBufferQueue<T> {

    private final int capacity;
    private final int mask;
    private final AtomicReferenceArray<T> buffer;
    private final AtomicLong writeSequence = new AtomicLong(0);
    private final AtomicLong readSequence = new AtomicLong(0);

    public LockFreeRingBufferQueue() {
        this(65536); // Default 64K entries
    }

    public LockFreeRingBufferQueue(int requestedCapacity) {
        if (requestedCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        // Round up to nearest power of two
        int cap = 1;
        while (cap < requestedCapacity) {
            cap <<= 1;
        }
        this.capacity = cap;
        this.mask = this.capacity - 1;
        this.buffer = new AtomicReferenceArray<>(this.capacity);
    }

    /**
     * Non-blocking lock-free enqueue.
     *
     * @param item Element to enqueue
     * @return true if successfully inserted, false if buffer is full
     */
    public boolean offer(T item) {
        if (item == null) {
            throw new NullPointerException("Null elements not permitted");
        }

        while (true) {
            long currentRead = readSequence.get();
            long currentWrite = writeSequence.get();

            if (currentWrite - currentRead >= capacity) {
                // Buffer is completely full
                return false;
            }

            int index = (int) (currentWrite & mask);
            // Verify slot is empty before attempting sequence advance
            if (buffer.get(index) == null) {
                if (writeSequence.compareAndSet(currentWrite, currentWrite + 1)) {
                    buffer.set(index, item);
                    return true;
                }
            } else {
                // Another thread has claimed or not yet cleared; spin wait hint
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Non-blocking lock-free dequeue.
     *
     * @return Element if available, null if buffer is empty
     */
    public T poll() {
        while (true) {
            long currentRead = readSequence.get();
            long currentWrite = writeSequence.get();

            if (currentRead >= currentWrite) {
                // Buffer is empty
                return null;
            }

            int index = (int) (currentRead & mask);
            T item = buffer.get(index);

            if (item != null) {
                if (readSequence.compareAndSet(currentRead, currentRead + 1)) {
                    buffer.set(index, null);
                    return item;
                }
            } else {
                // Write sequence reserved but item not yet stored; spin wait hint
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Inspects head element without removing.
     */
    public T peek() {
        long currentRead = readSequence.get();
        int index = (int) (currentRead & mask);
        return buffer.get(index);
    }

    /**
     * Approximate number of elements currently stored.
     */
    public int size() {
        long read = readSequence.get();
        long write = writeSequence.get();
        long diff = write - read;
        return (int) Math.max(0, Math.min(diff, capacity));
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int getCapacity() {
        return capacity;
    }

    public void clear() {
        while (poll() != null) {
            // Drain buffer
        }
    }
}
