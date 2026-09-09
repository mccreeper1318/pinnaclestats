package io.github.mccreeper1318.pinnaclestats;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Drains queued refresh batches while taking a fresh settings snapshot at the
 * beginning of every batch. This keeps requests queued after a reload from
 * inheriting the configuration generation of an earlier in-flight batch.
 */
final class RefreshBatchDrain {
    @FunctionalInterface
    interface Processor<T> {
        boolean process(RefreshRequestQueue.Batch batch, T settingsSnapshot);
    }

    record Result<T>(boolean refreshed, T lastSuccessfulSettings) {}

    private RefreshBatchDrain() {}

    static <T> Result<T> drain(
            RefreshRequestQueue queue,
            Supplier<T> settingsSupplier,
            BooleanSupplier continueProcessing,
            Processor<T> processor
    ) {
        boolean refreshed = false;
        T lastSuccessfulSettings = null;

        while (continueProcessing.getAsBoolean()) {
            RefreshRequestQueue.Batch batch = queue.takeNext();
            if (batch.isEmpty()) break;

            T settingsSnapshot = settingsSupplier.get();
            if (processor.process(batch, settingsSnapshot)) {
                refreshed = true;
                lastSuccessfulSettings = settingsSnapshot;
            }
        }

        return new Result<>(refreshed, lastSuccessfulSettings);
    }
}
