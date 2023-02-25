package problems.concurrency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.LongStream.range;

@Slf4j
public class RangeSequencer {
    private static final int CLIENT_TOTAL_CALLS = 100;
    private static final int WORKERS_TOTAL = 3;
    private static final int CLIENTS_TOTAL = 10;
    private static final long RANGE_SIZE = 5;

    // Switch between worker type
    private static final WorkerFactory.WorkerType WORKER_TYPE = WorkerFactory.WorkerType.NON_BLOCKING;

    public static void main(String[] args) throws Exception {
        LoadBalancer lb = new LoadBalancer(new WorkerFactory(WORKER_TYPE), WORKERS_TOTAL);
        ExecutorService clientPool = newFixedThreadPool(CLIENTS_TOTAL);

        List<Future<List<Id>>> futures = range(0, CLIENTS_TOTAL)
                .mapToObj(i -> clientPool.submit(new ClientTask(lb, CLIENT_TOTAL_CALLS, "C" + i)))
                .toList();

        clientPool.shutdown();
        clientPool.awaitTermination(10, TimeUnit.SECONDS);

        List<Id> generatedIds = futures.stream()
                .map(RangeSequencer::safeGet)
                .flatMap(Collection::stream)
                .sorted(comparing(id -> id.value))
                .toList();

        // Check #1: Completeness
        // Verify each client received an id
        final long expectedResultSetSize = CLIENTS_TOTAL * CLIENT_TOTAL_CALLS;
        if (generatedIds.size() != expectedResultSetSize) {
            log.error("Result size mismatch expected:{} got:{}", expectedResultSetSize, generatedIds.size());
        } else {
            log.info("Generated:{} ids", generatedIds.size());
        }

        // Check #2: Correctness
        // Verify all ids are unique
        Set<Long> uniqueIds = new HashSet<>();
        List<Id> duplicates = generatedIds.stream()
                .filter(x -> !uniqueIds.add(x.value))
                .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            log.error("Found duplicate ids:{}", duplicates);
        } else {
            log.info("No duplicates found");
        }

        // Check #3: Reliability
        // Verify ranges are depleted evenly without holes
        // At any point in time we can have at most:
        // (NUM_WORKERS - 1) * (RANGE_SIZE - 1)
        // not filled ids
        // Explanation: NUM_WORKERS:3, RANGE_SIZE:10, each worker generated 1 id:
        // [ W1:0, W2:10, W3:20 ] -> 20 ids missing [1-9], [11-19]
        final long maxAllowedMissingIdsCount = (WORKERS_TOTAL - 1) * (RANGE_SIZE - 1);
        long missingIdsCount = 0L;
        List<String> unfinishedRangesDescriptions = new ArrayList<>();
        for (int i = 0; i < generatedIds.size() - 1; i++) {
            long neighboringIdsDiff = generatedIds.get(i + 1).value - generatedIds.get(i).value;
            if (neighboringIdsDiff == 1) {
                // contiguous, ok
                continue;
            }

            missingIdsCount += neighboringIdsDiff - 1;

            Id id = generatedIds.get(i);
            long rangeStart = (id.value + 1) / RANGE_SIZE * RANGE_SIZE;
            long rangeEnd = rangeStart + RANGE_SIZE;
            if (id.value >= rangeStart) {
                // Unfinished range
                unfinishedRangesDescriptions.add(format("%n[%s, %s], Last id:%s, Worker:%s",
                        rangeStart, rangeEnd, id.value, id.worker));
            } else {
                // Lost range
                unfinishedRangesDescriptions.add(format("%n[%s, %s], LOST", rangeStart, rangeEnd));
            }
        }
        if (missingIdsCount > maxAllowedMissingIdsCount) {
            log.error("Missing ids count:{} exceeds max allowance:{}, unfinished:{}",
                    missingIdsCount, maxAllowedMissingIdsCount, unfinishedRangesDescriptions);
        } else {
            log.info("Id ranges depleted evenly, unfinished:{}",
                    unfinishedRangesDescriptions);
        }

        log.info("Done");
    }

    @SneakyThrows
    private static <T> T safeGet(Future<T> future) {
        return future.get();
    }

    private static class LoadBalancer {
        private final AtomicInteger next;
        private final List<IdGenerator> pool;

        public LoadBalancer(WorkerFactory workerFactory, int poolSize) {
            this.next = new AtomicInteger();
            this.pool = range(0, poolSize)
                    .mapToObj(i -> workerFactory.createWorker())
                    .collect(toList());
        }

        public IdGenerator next() {
            int next = this.next.getAndUpdate(index -> (index + 1) % pool.size());
            return pool.get(next);
        }
    }

    @RequiredArgsConstructor
    private static class WorkerFactory {
        private static final RangeAssigner RANGE_ASSIGNER = new RangeAssigner(RANGE_SIZE);
        private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
        private final WorkerType workerType;

        public IdGenerator createWorker() {
            return switch (workerType) {
                case RRW_LOCK -> new ReadWriteLockIdGenerator(
                        "W" + INSTANCE_COUNTER.incrementAndGet(),
                        RANGE_ASSIGNER);

                case NON_BLOCKING -> new NonBlockingIdGenerator(
                        "W" + INSTANCE_COUNTER.incrementAndGet(),
                        RANGE_ASSIGNER);
            };
        }

        public enum WorkerType {RRW_LOCK, NON_BLOCKING}
    }

    @RequiredArgsConstructor
    private static class RangeAssigner {
        private final AtomicLong nextRangeStart = new AtomicLong();
        private final long rangeSize;

        @SneakyThrows
        public Pair<Long, Long> nextRange() {
            long rangeStart = nextRangeStart.getAndUpdate(curr -> curr + rangeSize);
            return Pair.of(rangeStart, rangeStart + rangeSize);
        }
    }

    @RequiredArgsConstructor
    private static class ClientTask implements Callable<List<Id>> {
        private final LoadBalancer lb;
        private final long totalCalls;
        private final String id;

        @Override
        public List<Id> call() {
            return range(0, totalCalls)
                    .mapToObj(i -> {
                        IdGenerator idGenerator = lb.next();
                        long nextNumber = idGenerator.generateId();
                        Id generatedId = new Id(nextNumber, id, idGenerator.getId());
                        log.debug("ID: {}", id);
                        return generatedId;
                    })
                    .collect(toList());
        }
    }

    private record Id(long value, String client, String worker) {
        @Override
        public String toString() {
            return String.format("Generated %s:%s [%d]", client, worker, value);
        }
    }

    interface IdGenerator {
        String getId();

        long generateId();

    }

    private record RangeCounter(Pair<Long, Long> range, AtomicLong counter) {

        public long nextId() {
            return counter.getAndIncrement();
        }

        public boolean isWithin(long id) {
            return id >= range.getLeft() && id < range.getRight();
        }

        public boolean isRangeEnd(long id) {
            return id == range.getRight();
        }
    }

    @Slf4j
    private static class ReadWriteLockIdGenerator implements IdGenerator {
        @Getter
        private final String id;
        private final RangeAssigner rangeAssigner;
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();
        // In given implementation primitive long type would also work fine
        // however Atomic has more streamlined interface when used as counter
        private final AtomicLong next;

        private Pair<Long, Long> range;

        public ReadWriteLockIdGenerator(String id, RangeAssigner rangeAssigner) {
            this.id = id;
            this.rangeAssigner = rangeAssigner;
            this.range = rangeAssigner.nextRange();
            this.next = new AtomicLong(this.range.getLeft());
        }

        @Override
        public long generateId() {
            try {
                readLock.lock();
                // Note, even though this is an AtomicLong
                // if you moved it outside the read lock
                // it would've interleaved with the write lock logic
                long nextId = this.next.getAndIncrement();
                if (isWithingRange(nextId)) {
                    return nextId;
                }
            } finally {
                readLock.unlock();
            }

            try {
                writeLock.lock();
                long nextId = this.next.getAndIncrement();

                // Double check counter against the range to prevent empty ranges
                // Following situation is possible:
                // [T1] increment counter, counter is not withing the range, acquire the WriteLock
                // [T2] increment counter, counter is not withing the range, wait for the WriteLock
                // [T1] update range, update counter
                // [T2] update range, update counter
                if (isWithingRange(nextId)) {
                    // Safely return current counter value since we're inside the WriteLock
                    // e.g. readers couldn't have updated its value
                    // as all of them are blocked waiting to get access to the counter
                    log.info("[{}] Aborted range update due to the race condition nextId:{}", id, nextId);
                    return nextId;
                }

                this.range = rangeAssigner.nextRange();
                this.next.set(this.range.getLeft());

                nextId = this.next.getAndIncrement();
                log.info("[{}] Assigned new range [{}], nextId:{}", this.id, this.range, nextId);
                return nextId;
            } finally {
                writeLock.unlock();
            }
        }
        private boolean isWithingRange(long value) {
            // atomic read of the current range
            // otherwise we could get inconsistent range in the following condition
            // [t1] this.range.getLeft() -> old range's left
            // [t2] this.range.getRight() -> new range's right
            Pair<Long, Long> currRange = this.range;
            return value >= currRange.getLeft() && value < currRange.getRight();
        }

    }
    private static class NonBlockingIdGenerator implements IdGenerator {
        @Getter
        private final String id;
        private final RangeAssigner rangeAssigner;

        private final AtomicReference<RangeCounter> rangeCounter;

        public NonBlockingIdGenerator(String id, RangeAssigner rangeAssigner) {
            this.id = id;
            this.rangeAssigner = rangeAssigner;

            Pair<Long, Long> startRange = rangeAssigner.nextRange();
            this.rangeCounter = new AtomicReference<>(
                    new RangeCounter(startRange, new AtomicLong(startRange.getLeft())));
        }
        @Override
        public long generateId() {
            // Case 1: Increment within the range
            // Non-blocking, fastest
            RangeCounter currState = this.rangeCounter.get();
            long nextId = currState.nextId();
            if (currState.isWithin(nextId)) {
                log.info("[{}] WITHIN [{}]: {}", id, currState.range, nextId);
                return nextId;
            }

            // Case 2: Increment to range end
            // A thread who observed given condition is the only allowed to assign a new range
            if (currState.isRangeEnd(nextId)) {
                Pair<Long, Long> nextRange = this.rangeAssigner.nextRange();

                this.rangeCounter.set(new RangeCounter(
                        nextRange,
                        new AtomicLong(nextRange.getLeft() + 1)));

                nextId = nextRange.getLeft();
                log.info("[{}] UPD [{}]: {}", id, nextRange, nextId);
                return nextId;
            }

            // Case 3: Increment above the range
            // Busy-wait until other thread pulls a new range,
            // and we successfully generate an id withing it
            while (!currState.isWithin(nextId)) {
                log.info("[{}] ABOVE [{}]: {}", id, currState.range, nextId);
                currState = this.rangeCounter.get();
                nextId = currState.nextId();
            }
            log.info("[{}] ABOVE FINISHED [{}]: {}", id, currState.range, nextId);
            return nextId;
        }

    }

}
