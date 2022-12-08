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
package com.intellij.java.impl.openapi.roots.ui.configuration;

import com.intellij.java.impl.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.java.language.impl.JavaFileType;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.SystemInfo;
import consulo.component.ProcessCanceledException;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.file.FileTypeManager;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileVisitor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class JavaVfsSourceRootDetectionUtil {
  private JavaVfsSourceRootDetectionUtil() {}

  /**
   * Scan directory and detect java source roots within it. The source root is detected as the following:
   * <ol>
   * <li>It contains at least one Java file.</li>
   * <li>Java file is located in the sub-folder that matches package statement in the file.</li>
   * </ol>
   *
   * @param dir a directory to scan
   * @param progressIndicator
   * @return a list of found source roots within directory. If no source roots are found, a empty list is returned.
   */
  @Nonnull
  public static List<VirtualFile> suggestRoots(@Nonnull VirtualFile dir, @Nonnull final ProgressIndicator progressIndicator) {
    if (!dir.isDirectory()) {
      return List.of();
    }

    final FileTypeManager typeManager = FileTypeManager.getInstance();
    final ArrayList<VirtualFile> foundDirectories = new ArrayList<VirtualFile>();
    try {
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor() {
        @Nonnull
        @Override
        public Result visitFileEx(@Nonnull VirtualFile file) {
          progressIndicator.checkCanceled();

          if (file.isDirectory()) {
            if (typeManager.isFileIgnored(file) || StringUtil.startsWithIgnoreCase(file.getName(), "testData")) {
              return SKIP_CHILDREN;
            }
          }
          else {
            FileType type = typeManager.getFileTypeByFileName(file.getName());
            if (type == JavaFileType.INSTANCE) {
              VirtualFile root = suggestRootForJavaFile(file);
              if (root != null) {
                foundDirectories.add(root);
                return skipTo(root);
              }
            }
          }

          return CONTINUE;
        }
      });
    }
    catch (ProcessCanceledException ignore) { }

    return foundDirectories;
  }

  @Nullable
  private static VirtualFile suggestRootForJavaFile(VirtualFile javaFile) {
    if (javaFile.isDirectory()) return null;

    CharSequence chars = javaFile.loadText();

    String packageName = JavaSourceRootDetectionUtil.getPackageName(chars);
    if (packageName != null){
      VirtualFile root = javaFile.getParent();
      int index = packageName.length();
      while(index > 0){
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = root.getName();
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken) {
          return null;
        }
        root = root.getParent();
        if (root == null){
          return null;
        }
        index = index1;
      }
      return root;
    }

    return null;
  }
}
