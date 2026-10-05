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

import consulo.java.profiler.localize.JavaProfilerLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.profiler.Failure;
import consulo.execution.profiler.NewCallTreeOnlyProfilerData;
import consulo.execution.profiler.ProfilerDumpFileParser;
import consulo.execution.profiler.ProfilerDumpParserProvider;
import consulo.execution.profiler.Success;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;

import java.io.IOException;

/**
 * Opens {@code .jfr} recordings as CPU snapshots.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
@ExtensionImpl
public class JfrDumpParserProvider implements ProfilerDumpParserProvider {
    public static final String EXTENSION = "jfr";

    private static final Logger LOG = Logger.getInstance(JfrDumpParserProvider.class);

    @Override
    public String getId() {
        return "java.jfr";
    }

    @Override
    public LocalizeValue getName() {
        return JavaProfilerLocalize.jfrName();
    }

    @Override
    public String getRequiredFileExtension() {
        return EXTENSION;
    }

    @Override
    public boolean isExclusiveExtension() {
        return true;
    }

    @Override
    public ProfilerDumpFileParser createParser(Project project) {
        return (file, indicator) -> {
            try {
                return new Success(new NewCallTreeOnlyProfilerData(
                    JfrStackReader.read(file.toPath(), false, indicator),
                    JavaCallStackElementRenderer.INSTANCE
                ));
            }
            catch (IOException e) {
                LOG.warn("Can't read the JFR file " + file, e);
                return new Failure(e.getMessage());
            }
        };
    }
}
