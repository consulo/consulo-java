/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.profiler.jfr;

import one.jfr.ClassRef;
import one.jfr.JfrReader;
import one.jfr.MethodRef;
import one.jfr.StackTrace;
import one.jfr.event.ExecutionSample;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the sampled stacks of a JFR file: execution, native method and CPU-time samples.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrSampleReader {
    private final JfrReader myReader;
    private final Map<Long, JfrFrame> myFrames = new HashMap<>();
    private final Map<Integer, JfrThread> myThreads = new HashMap<>();

    private JfrSampleReader(JfrReader reader) {
        myReader = reader;
    }

    /**
     * Calls the visitor for every sample, in file order.
     *
     * @param checkCanceled called between events; it throws to stop reading
     */
    public static void read(Path file, JfrSampleVisitor visitor, Runnable checkCanceled) throws IOException {
        try (JfrReader reader = new JfrReader(file.toString())) {
            JfrSampleReader sampleReader = new JfrSampleReader(reader);
            ExecutionSample sample;
            while ((sample = reader.readEvent(ExecutionSample.class)) != null) {
                checkCanceled.run();
                sampleReader.accept(sample, visitor);
            }
        }
    }

    private void accept(ExecutionSample sample, JfrSampleVisitor visitor) {
        StackTrace stackTrace = myReader.stackTraces.get(sample.stackTraceId);
        if (stackTrace == null) {
            return;
        }

        List<JfrFrame> frames = new ArrayList<>(stackTrace.methods.length);
        for (long methodId : stackTrace.methods) {
            JfrFrame frame = toFrame(methodId);
            if (frame != null) {
                frames.add(frame);
            }
        }
        if (!frames.isEmpty()) {
            visitor.visit(toKind(sample.threadState), toThread(sample.tid), frames, sample.samples());
        }
    }

    private static JfrSampleKind toKind(int threadState) {
        return switch (threadState) {
            case ExecutionSample.CPU_TIME_SAMPLE -> JfrSampleKind.CPU_TIME;
            case ExecutionSample.NATIVE_METHOD_SAMPLE -> JfrSampleKind.NATIVE;
            default -> JfrSampleKind.EXECUTION;
        };
    }

    private @Nullable JfrFrame toFrame(long methodId) {
        JfrFrame cached = myFrames.get(methodId);
        if (cached != null) {
            return cached;
        }

        MethodRef method = myReader.methods.get(methodId);
        if (method == null) {
            return null;
        }

        ClassRef classRef = myReader.classes.get(method.cls);
        String className = classRef == null ? "" : stripHiddenClassSuffix(symbol(classRef.name).replace('/', '.'));
        JfrFrame frame = new JfrFrame(className, symbol(method.name), symbol(method.sig));
        myFrames.put(methodId, frame);
        return frame;
    }

    private String symbol(long id) {
        byte[] bytes = myReader.symbols.get(id);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private JfrThread toThread(int tid) {
        return myThreads.computeIfAbsent(tid, key -> {
            Long javaId = myReader.javaThreads.get(key);
            long id = javaId == null || javaId <= 0 ? -key : javaId;
            String name = myReader.threads.get(key);
            return new JfrThread(id, name == null || name.isEmpty() ? "Thread " + id : name);
        });
    }

    static String stripHiddenClassSuffix(String className) {
        int index = className.lastIndexOf(".0x");
        if (index <= 0) {
            return className;
        }
        for (int i = index + 3; i < className.length(); i++) {
            if (Character.digit(className.charAt(i), 16) < 0) {
                return className;
            }
        }
        return className.substring(0, index);
    }
}
