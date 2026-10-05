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

import com.microsoft.jfr.JfrStreamingException;
import com.microsoft.jfr.Recording;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A recording started by {@link JfrRecorder#start}.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrRecording {
    private final Recording myRecording;

    JfrRecording(Recording recording) {
        myRecording = recording;
    }

    /**
     * Stops the recording, copies it into the file and closes it.
     */
    public void stopAndCopy(Path file) throws IOException {
        try {
            myRecording.stop();
            try (InputStream stream = myRecording.getStream(null, null)) {
                Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (JfrStreamingException e) {
            throw new IOException(e.getMessage(), e);
        }
        finally {
            close();
        }
    }

    /**
     * Closes the recording in the JVM, dropping its data.
     */
    public void close() {
        try {
            myRecording.close();
        }
        catch (IOException | RuntimeException ignored) {
        }
    }
}
