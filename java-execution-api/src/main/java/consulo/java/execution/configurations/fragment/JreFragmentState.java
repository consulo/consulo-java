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
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The JRE a Java run configuration starts with: a JDK name or a JRE home path, {@code null} for the JDK of the module or
 * the project.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class JreFragmentState {
    private @Nullable String myPath;

    public JreFragmentState() {
    }

    public JreFragmentState(@Nullable String path) {
        setPath(path);
    }

    @Attribute("path")
    public @Nullable String getPath() {
        return myPath;
    }

    public void setPath(@Nullable String path) {
        myPath = StringUtil.nullize(path, true);
    }
}
