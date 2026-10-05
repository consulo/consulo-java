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
package consulo.java.profiler.impl.internal.jfr.snapshot;

import consulo.application.progress.ProgressIndicator;
import consulo.execution.profiler.BaseCallStackElement;
import consulo.execution.profiler.CallTreeBuilder;
import consulo.execution.profiler.Stack;
import consulo.execution.profiler.model.NativeCall;
import consulo.execution.profiler.model.NativeThread;
import consulo.java.profiler.jfr.JfrFrame;
import consulo.java.profiler.jfr.JfrSampleKind;
import consulo.java.profiler.jfr.JfrSampleReader;
import consulo.java.profiler.jfr.JfrThread;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the sampled stacks of a JFR file into a call tree. CPU-time samples win over execution samples when the file
 * has both; native method samples are added on request.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrStackReader {
    private final boolean myNativeSamples;
    private final Map<JfrFrame, BaseCallStackElement> myElements = new HashMap<>();
    private final Map<JfrThread, NativeThread> myThreads = new HashMap<>();
    private final Map<List<BaseCallStackElement>, List<BaseCallStackElement>> myFrameLists = new HashMap<>();
    private final List<Stack<BaseCallStackElement>> myExecutionSamples = new ArrayList<>();
    private final List<Stack<BaseCallStackElement>> myCpuTimeSamples = new ArrayList<>();
    private final List<Stack<BaseCallStackElement>> myNativeMethodSamples = new ArrayList<>();

    private JfrStackReader(boolean nativeSamples) {
        myNativeSamples = nativeSamples;
    }

    public static CallTreeBuilder<BaseCallStackElement> read(Path file, boolean nativeSamples, @Nullable ProgressIndicator indicator)
        throws IOException {
        JfrStackReader stackReader = new JfrStackReader(nativeSamples);
        JfrSampleReader.read(file, stackReader::accept, () -> {
            if (indicator != null) {
                indicator.checkCanceled();
            }
        });
        return stackReader.build();
    }

    private void accept(JfrSampleKind kind, JfrThread thread, List<JfrFrame> frames, long weight) {
        List<Stack<BaseCallStackElement>> target = switch (kind) {
            case EXECUTION -> myExecutionSamples;
            case CPU_TIME -> myCpuTimeSamples;
            case NATIVE -> myNativeSamples ? myNativeMethodSamples : null;
        };
        if (target == null) {
            return;
        }

        List<BaseCallStackElement> rootFirst = new ArrayList<>(frames.size());
        for (int i = frames.size() - 1; i >= 0; i--) {
            rootFirst.add(toElement(frames.get(i)));
        }
        NativeThread nativeThread = myThreads.computeIfAbsent(thread, key -> new NativeThread(key.id(), key.name()));
        target.add(new Stack<>(nativeThread, myFrameLists.computeIfAbsent(rootFirst, List::copyOf), weight));
    }

    private BaseCallStackElement toElement(JfrFrame frame) {
        return myElements.computeIfAbsent(frame, key -> key.isNative()
            ? new NativeCall("", "", key.methodName())
            : new JavaCallStackElement(key.className(), key.methodName(), key.descriptor()));
    }

    private CallTreeBuilder<BaseCallStackElement> build() {
        List<Stack<BaseCallStackElement>> stacks = myCpuTimeSamples.isEmpty() ? myExecutionSamples : myCpuTimeSamples;
        if (!myNativeMethodSamples.isEmpty()) {
            stacks = new ArrayList<>(stacks);
            stacks.addAll(myNativeMethodSamples);
        }
        List<Stack<BaseCallStackElement>> result = Collections.unmodifiableList(stacks);
        return () -> result;
    }
}
