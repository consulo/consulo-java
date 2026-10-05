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

/**
 * @author VISTALL
 * @since 2026-10-05
 */
public enum JfrSampleKind {
    /**
     * {@code jdk.ExecutionSample}: a thread running Java code.
     */
    EXECUTION,
    /**
     * {@code jdk.NativeMethodSample}: a thread in native code.
     */
    NATIVE,
    /**
     * {@code jdk.CPUTimeSample}: CPU-time sampling, JDK 25+ on Linux.
     */
    CPU_TIME
}
