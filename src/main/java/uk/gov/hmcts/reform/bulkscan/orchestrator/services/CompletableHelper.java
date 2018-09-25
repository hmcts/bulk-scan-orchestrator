package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import java.util.concurrent.CompletableFuture;

public interface CompletableHelper {
    static CompletableFuture<Void> completeRunnable(Runnable runnable) {
        /*
         * NOTE: this is done here instead of offloading to the forkJoin pool "CompletableFuture.runAsync()"
         * because we probably should think about a threading model before doing this.
         * Maybe consider using Netflix's RxJava too (much simpler than CompletableFuture).
         */
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        try {
            runnable.run();
            completableFuture.complete(null);
        } catch (Throwable t) {
            completableFuture.completeExceptionally(t);
        }
        return completableFuture;
    }
}
