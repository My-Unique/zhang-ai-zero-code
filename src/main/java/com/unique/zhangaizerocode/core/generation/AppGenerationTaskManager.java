package com.unique.zhangaizerocode.core.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Component
public class AppGenerationTaskManager {

    private final Map<Long, GenerationTask> taskMap = new ConcurrentHashMap<>();

    public Flux<GenerationStreamEvent> start(Long appId, Supplier<Flux<String>> generationSupplier) {
        GenerationTask existingTask = taskMap.get(appId);
        if (existingTask != null && !existingTask.isFinished()) {
            return existingTask.flux();
        }

        GenerationTask task = new GenerationTask(appId);
        taskMap.put(appId, task);
        Disposable disposable = generationSupplier.get()
                .subscribe(
                        task::emitMessage,
                        error -> {
                            task.emitError(error);
                        },
                        () -> {
                            task.emitDone();
                        }
                );
        task.setDisposable(disposable);
        return task.flux();
    }

    public Flux<GenerationStreamEvent> subscribe(Long appId) {
        GenerationTask task = taskMap.get(appId);
        if (task == null) {
            return Flux.error(new IllegalStateException("当前应用没有正在生成的任务"));
        }
        return task.subscribeWithSnapshot();
    }

    public boolean stop(Long appId) {
        GenerationTask task = taskMap.remove(appId);
        if (task == null || task.isFinished()) {
            return false;
        }
        task.stop();
        return true;
    }

    private static class GenerationTask {

        private final Long appId;
        private final Sinks.Many<GenerationStreamEvent> sink = Sinks.many().replay().all();
        private final StringBuilder outputBuffer = new StringBuilder();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicLong eventCount = new AtomicLong();
        private volatile GenerationStreamEvent terminalEvent;
        private volatile Disposable disposable;

        private GenerationTask(Long appId) {
            this.appId = appId;
        }

        private Flux<GenerationStreamEvent> flux() {
            return sink.asFlux();
        }

        private Flux<GenerationStreamEvent> subscribeWithSnapshot() {
            SnapshotState snapshotState = getSnapshotState();
            Flux<GenerationStreamEvent> snapshotFlux = snapshotState.content().isBlank()
                    ? Flux.empty()
                    : Flux.just(GenerationStreamEvent.snapshot(snapshotState.content()));
            GenerationStreamEvent terminal = terminalEvent;
            if (finished.get() && terminal != null) {
                return snapshotFlux.concatWithValues(terminal);
            }
            return snapshotFlux.concatWith(sink.asFlux().skip(snapshotState.skipCount()));
        }

        private boolean isFinished() {
            return finished.get();
        }

        private void setDisposable(Disposable disposable) {
            this.disposable = disposable;
        }

        private void emitMessage(String chunk) {
            if (!finished.get()) {
                synchronized (this) {
                    outputBuffer.append(chunk);
                    eventCount.incrementAndGet();
                }
                sink.tryEmitNext(GenerationStreamEvent.message(chunk));
            }
        }

        private void emitDone() {
            if (finished.compareAndSet(false, true)) {
                GenerationStreamEvent doneEvent = GenerationStreamEvent.done();
                terminalEvent = doneEvent;
                eventCount.incrementAndGet();
                sink.tryEmitNext(doneEvent);
                sink.tryEmitComplete();
                log.info("应用生成后台任务完成，appId: {}", appId);
            }
        }

        private void emitError(Throwable error) {
            if (finished.compareAndSet(false, true)) {
                String message = error.getMessage() == null ? "生成失败" : error.getMessage();
                GenerationStreamEvent errorEvent = GenerationStreamEvent.error(message);
                terminalEvent = errorEvent;
                eventCount.incrementAndGet();
                sink.tryEmitNext(errorEvent);
                sink.tryEmitComplete();
                log.error("应用生成后台任务失败，appId: {}", appId, error);
            }
        }

        private void stop() {
            if (finished.compareAndSet(false, true)) {
                Disposable currentDisposable = disposable;
                if (currentDisposable != null && !currentDisposable.isDisposed()) {
                    currentDisposable.dispose();
                }
                GenerationStreamEvent stopEvent = GenerationStreamEvent.error("用户已停止生成");
                terminalEvent = stopEvent;
                eventCount.incrementAndGet();
                sink.tryEmitNext(stopEvent);
                sink.tryEmitComplete();
                log.info("应用生成后台任务已停止，appId: {}", appId);
            }
        }

        private synchronized SnapshotState getSnapshotState() {
            return new SnapshotState(outputBuffer.toString(), eventCount.get());
        }
    }

    private record SnapshotState(String content, long skipCount) {
    }
}
