package com.unityagent.agent.concurrency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded queue with backpressure and high-frequency event coalescing.
 * Prevents memory saturation from fast polling or heartbeat events.
 */
public class BoundedEventQueue<T> {

    private final int capacity;
    private final Deque<T> queue = new ArrayDeque<>();
    private final AtomicLong droppedCount = new AtomicLong(0);

    public BoundedEventQueue() {
        this(500);
    }

    public BoundedEventQueue(int capacity) {
        this.capacity = capacity > 0 ? capacity : 500;
    }

    /**
     * Enqueues an item with backpressure.
     * If coalescent is true and queue is full, replaces newest event.
     * Otherwise if full, discards oldest event and increments dropped counter.
     */
    public synchronized boolean offer(T item, boolean isCoalescent) {
        if (item == null) return false;

        if (queue.size() >= capacity) {
            if (isCoalescent) {
                // Replace newest coalescent item
                queue.pollLast();
                queue.offerLast(item);
                return true;
            } else {
                // Drop oldest
                queue.pollFirst();
                droppedCount.incrementAndGet();
            }
        }
        return queue.offerLast(item);
    }

    public synchronized T poll() {
        return queue.pollFirst();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized void clear() {
        queue.clear();
    }
}
