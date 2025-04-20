/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.language.projectRoots.roots;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public abstract class ExternalLibraryDescriptor {
    private final String myLibraryGroupId;
    private final String myLibraryArtifactId;
    private final String myMinVersion;
    private final String myMaxVersion;

    public ExternalLibraryDescriptor(String libraryGroupId, String libraryArtifactId) {
        this(libraryGroupId, libraryArtifactId, null, null);
    }

    public ExternalLibraryDescriptor(
        @Nonnull String libraryGroupId,
        @Nonnull String libraryArtifactId,
        @Nullable String minVersion,
        @Nullable String maxVersion
    ) {
        myLibraryGroupId = libraryGroupId;
        myLibraryArtifactId = libraryArtifactId;
        myMinVersion = minVersion;
        myMaxVersion = maxVersion;
    }

    @Nonnull
    public String getLibraryGroupId() {
        return myLibraryGroupId;
    }

    @Nonnull
    public String getLibraryArtifactId() {
        return myLibraryArtifactId;
    }

    @Nullable
    public String getMinVersion() {
        return myMinVersion;
    }

    @Nullable
    public String getMaxVersion() {
        return myMaxVersion;
    }

    public String getPresentableName() {
        return myLibraryArtifactId;
    }

    @Nonnull
    public abstract List<String> getLibraryClassesRoots();
}
