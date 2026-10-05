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
 * A frame of a sampled stack.
 *
 * @param className the binary class name with dots, such as {@code java.util.HashMap$Node}, with the address of a hidden
 *                  class dropped ({@code demo.Main$$Lambda}); empty for a native frame without a class
 * @param descriptor the JVM method descriptor, or empty when the recording has none
 * @author VISTALL
 * @since 2026-10-05
 */
public record JfrFrame(String className, String methodName, String descriptor) {
    public boolean isNative() {
        return className.isEmpty();
    }
}
