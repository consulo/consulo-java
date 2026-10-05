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
package consulo.java.profiler.impl.internal.jvm;

import consulo.execution.profiler.AttachableTargetProcess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Temporary {@code .jfr} files which the IDE and profiled JVMs write recordings into. They are deleted when the IDE
 * exits; a recording the user wants to keep is saved through its dump writer.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmRecordingFiles {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private JvmRecordingFiles() {
    }

    public static Path create(String kind) throws IOException {
        Path file = Files.createTempFile("consulo-" + kind + "-", ".jfr");
        file.toFile().deleteOnExit();
        return file;
    }

    public static String dumpFileName(AttachableTargetProcess targetProcess) {
        String name = targetProcess.getFullName().replaceAll("[^A-Za-z0-9._-]+", "_");
        return name + "_" + LocalDateTime.now().format(TIMESTAMP) + ".jfr";
    }
}
