package com.kaizen.tool;

/**
 * Cooperative cancellation flag for long archive operations
 * (ZIP / 7Z / TAR create & extract).
 *
 * The UI thread calls cancel(); the background worker thread polls
 * isCancelled() at safe points (per file/entry, and every ~64KB inside
 * copy loops). Null token = no cancellation support, treated as "never
 * cancelled".
 */
public class CancelToken {
    private volatile boolean cancelled = false;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
