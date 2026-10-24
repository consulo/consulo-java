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
package com.intellij.java.coverage;

import com.dslplatform.json.CompiledJson;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * The classes and packages a Java run configuration records coverage for; empty for every class of the project.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class JavaCoverageIncludeFragmentState {
    private List<JavaCoveragePatternState> myPatterns = new ArrayList<>();

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public List<JavaCoveragePatternState> getPatterns() {
        return myPatterns;
    }

    public void setPatterns(List<JavaCoveragePatternState> patterns) {
        myPatterns = new ArrayList<>(patterns);
    }
}
