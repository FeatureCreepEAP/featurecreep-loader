package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import featurecreep.loader.eventviewer.EventViewer;

class EventViewerTest {
    @Test
    void namedInvocationCreatesAndDispatchesEvent() throws Exception {
        EventViewer viewer = new EventViewer();
        AtomicInteger calls = new AtomicInteger();
        viewer.registerMonoFunctionListener("test.event", payload -> {
            calls.incrementAndGet();
            return payload;
        });

        Object[] first = viewer.invokeEvent("test.event", "one");
        Object[] second = viewer.invokeEvent("test.event", "two");

        assertEquals("one", first[0]);
        assertEquals("two", second[0]);
        assertEquals(2, calls.get());
        assertEquals(1, viewer.getEvents().size());
    }

    @Test
    void repeatedInvocationUpdatesPayloadWithoutDuplicatingEvent() throws Exception {
        EventViewer viewer = new EventViewer();
        viewer.invokeEvent("empty.event");
        viewer.invokeEvent("empty.event", 42);

        assertEquals(1, viewer.getEvents().size());
        assertEquals(42, viewer.getEvent("empty.event").getParams()[0]);
    }

    @Test
    void concurrentInvocationsOfOneNameKeepSingleEvent() throws Exception {
        EventViewer viewer = new EventViewer();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        viewer.invokeEvent("shared.event", i);
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, viewer.getEvents().size());
    }

    @Test
    void concurrentAddEventKeepsEveryEvent() throws Exception {
        EventViewer viewer = new EventViewer();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        viewer.addEvent("bulk.event", i);
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(4000, viewer.getEvents().size());
    }
}
