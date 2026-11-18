package de.qspool.clementineremote.testing;

import static org.junit.Assert.fail;

/** Checks that code shared between threads takes the lock that guards it. */
public final class LockAssertions {

    private LockAssertions() {
    }

    /** Runs call on another thread while holding lock, and checks the call waits for it. */
    public static void assertWaitsForLock(Object lock, Runnable call) throws InterruptedException {
        Thread thread = new Thread(call);
        synchronized (lock) {
            thread.start();
            // The thread either blocks on the lock or, if the call doesn't take it, finishes.
            Thread.State state;
            while ((state = thread.getState()) != Thread.State.BLOCKED
                    && state != Thread.State.TERMINATED) {
                Thread.sleep(1);
            }
            if (state == Thread.State.TERMINATED) {
                fail("The call didn't wait for the lock");
            }
        }
        thread.join();
    }
}
