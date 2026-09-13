package com.unityagent.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe cancellation token for an autonomous agent run.
 * Cancelling stops future LLM iterations and prevents new tool calls.
 */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void registerListener(Runnable action) {
        if (action != null) {
            listeners.add(action);
            if (isCancelled()) {
                action.run();
            }
        }
    }
}
