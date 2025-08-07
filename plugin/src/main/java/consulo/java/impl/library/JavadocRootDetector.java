/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.java.impl.library;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.content.base.DocumentationOrderRootType;
import consulo.content.library.ui.RootDetector;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class JavadocRootDetector extends RootDetector {
    public JavadocRootDetector() {
        super(DocumentationOrderRootType.getInstance(), false, "JavaDocs");
    }

    @Nonnull
    @Override
    public Collection<VirtualFile> detectRoots(@Nonnull VirtualFile rootCandidate, @Nonnull ProgressIndicator progressIndicator) {
        List<VirtualFile> result = new ArrayList<VirtualFile>();
        collectJavadocRoots(rootCandidate, result, progressIndicator);
        return result;
    }

    private static void collectJavadocRoots(VirtualFile file, final List<VirtualFile> result, final ProgressIndicator progressIndicator) {
        VirtualFileUtil.visitChildrenRecursively(file, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@Nonnull VirtualFile file) {
                progressIndicator.checkCanceled();
                if (file.isDirectory() && file.findChild("allclasses-frame.html") != null && file.findChild("allclasses-noframe.html") != null) {
                    result.add(file);
                    return false;
                }
                return true;
            }
        });
    }
}
