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
import consulo.execution.profiler.ProfilerDumpWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saves a recording as the {@code .jfr} file the JVM wrote.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrDumpWriter implements ProfilerDumpWriter {
    private final Path myRecording;
    private final String myDumpFileName;

    public JfrDumpWriter(Path recording, String dumpFileName) {
        myRecording = recording;
        myDumpFileName = dumpFileName;
    }

    @Override
    public String getDumpFileName() {
        return myDumpFileName;
    }

    @Override
    public void writeDump(File file, ProgressIndicator indicator) throws IOException {
        Files.copy(myRecording, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
