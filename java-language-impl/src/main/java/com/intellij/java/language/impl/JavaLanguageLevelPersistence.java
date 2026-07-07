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
package com.intellij.java.language.impl;

import com.intellij.java.language.LanguageLevel;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Owns the persisted Java {@link LanguageLevel} file attribute. Unlike the in-memory {@link LanguageLevel#KEY} user data, this value is
 * stable and available even during indexing, so it can be used to resolve the language level consistently for both stub building and AST
 * parsing (which avoids stub/AST mismatches when the in-memory value is temporarily absent or changing).
 */
public final class JavaLanguageLevelPersistence {
    private static final FileAttribute PERSISTENCE = new FileAttribute("language_level_persistence", 2, true);

    private JavaLanguageLevelPersistence() {
    }

    public static @Nullable LanguageLevel getPersistedLanguageLevel(VirtualFile fileOrDir) {
        try (DataInputStream stream = PERSISTENCE.readAttribute(fileOrDir)) {
            if (stream == null) {
                return null;
            }
            int ordinal = DataInputOutputUtil.readINT(stream);
            LanguageLevel[] values = LanguageLevel.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
        }
        catch (IOException e) {
            return null;
        }
    }

    public static void persistLanguageLevel(VirtualFile fileOrDir, LanguageLevel level) throws IOException {
        try (DataOutputStream stream = PERSISTENCE.writeAttribute(fileOrDir)) {
            DataInputOutputUtil.writeINT(stream, level.ordinal());
        }
    }
}
