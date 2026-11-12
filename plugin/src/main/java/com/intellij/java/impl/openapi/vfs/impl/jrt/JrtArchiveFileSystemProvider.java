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
package com.intellij.java.impl.openapi.vfs.impl.jrt;

import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.virtualFileSystem.archive.ArchiveFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystemProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The modules of the modular runtimes: {@code jrt://<home>!/<module>/<path>} - and of the runtimes of other platforms,
 * {@code platform-jrt://<platform id>/<home>!/<module>/<path>}.
 *
 * @author VISTALL
 * @since 2026-11-12
 */
@ExtensionImpl
public class JrtArchiveFileSystemProvider implements ArchiveFileSystemProvider {
    @Override
    public String getProtocol() {
        return JrtFileSystem.PROTOCOL;
    }

    @Override
    public ArchiveFile createArchiveFile(Path path) throws IOException {
        return new JrtArchiveFile(path);
    }

    @Override
    public boolean isArchiveDirectory(Path directory) {
        return OwnJdkUtil.isModularRuntime(directory) && !OwnJdkUtil.isExplodedModularRuntime(directory);
    }
}
