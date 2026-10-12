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
package com.intellij.java.impl.openapi.roots.ui.configuration;

import consulo.content.library.LibraryTypeService;
import consulo.content.library.OrderRoot;
import consulo.content.library.ui.LibraryRootsDetector;
import consulo.java.impl.library.JavaSourceRootDetector;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This utility class contains utility methods for selecting paths.
 *
 * @author Constantine.Plotnikov
 */
public class PathUIUtils {
    private PathUIUtils() {
    }

    /**
     * This method takes a candidates for the project root, then scans the candidates and
     * if multiple candidates or non root source directories are found whithin some
     * directories, it shows a dialog that allows selecting or deselecting them.
     *
     * @param parentComponent a parent component of the dialog
     * @param project         a project the dialog is shown for
     * @param rootCandidates  a candidates for roots
     * @return a array of source folders or empty array if non was selected, completed exceptionally if the user cancelled
     */
    @RequiredUIAccess
    public static CompletableFuture<VirtualFile[]> scanAndSelectDetectedJavaSourceRoots(
        @Nullable Component parentComponent,
        @Nullable Project project,
        VirtualFile[] rootCandidates
    ) {
        return LibraryTypeService.getInstance().detectRootsAsync(
            List.of(rootCandidates),
            parentComponent,
            project,
            LibraryRootsDetector.of(List.of(new JavaSourceRootDetector())),
            List.of()
        ).thenApply(orderRoots -> {
            List<VirtualFile> result = new ArrayList<>();
            for (OrderRoot root : orderRoots) {
                result.add(root.getFile());
            }
            return VirtualFileUtil.toVirtualFileArray(result);
        });
    }
}
