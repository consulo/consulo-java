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
package consulo.java.execution.configurations.fragment;

import com.dslplatform.json.CompiledJson;
import com.intellij.java.execution.ShortenCommandLine;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * How the command line of a Java run configuration is shortened: a {@link ShortenCommandLine} name, {@code null} for the
 * default of its JRE.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class ShortenCommandLineFragmentState {
    private @Nullable String myMode;

    public ShortenCommandLineFragmentState() {
    }

    public ShortenCommandLineFragmentState(@Nullable ShortenCommandLine mode) {
        myMode = mode == null ? null : mode.name();
    }

    @Attribute("mode")
    public @Nullable String getMode() {
        return myMode;
    }

    public void setMode(@Nullable String mode) {
        myMode = mode;
    }
}
