/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.language;

import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.language.Language;
import consulo.language.version.LanguageVersion;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.Arrays;

/**
 * @author max
 */
public class JavaLanguage extends Language {
    public static final JavaLanguage INSTANCE = new JavaLanguage();

    private JavaLanguage() {
        super("JAVA", "text/java", "application/x-java", "text/x-java");
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaLanguageLocalize.javaLanguageDisplayName();
    }

    @Nonnull
    @Override
    public LanguageVersion[] findVersions() {
        return Arrays.stream(LanguageLevel.values()).map(LanguageLevel::toLangVersion).toArray(LanguageVersion[]::new);
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}
