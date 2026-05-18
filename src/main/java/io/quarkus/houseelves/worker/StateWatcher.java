package io.quarkus.houseelves.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StateWatcher {

    private volatile FileTime lastModified;

    @Inject
    OpenConnections connections;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pollState() {
        long count = connections.stream().count();
        if (count == 0) return;

        try {
            if (!Files.exists(LiveSocket.STATE_PATH)) return;
            FileTime current = Files.getLastModifiedTime(LiveSocket.STATE_PATH);
            if (lastModified == null || current.compareTo(lastModified) > 0) {
                lastModified = current;
                String state = LiveSocket.readState();
                Log.infof("State changed, pushing to %d client(s)", count);
                connections.forEach(c -> c.sendTextAndAwait(state));
            }
        } catch (IOException e) {
            Log.warnf("Failed to poll state: %s", e.getMessage());
        }
    }
}
