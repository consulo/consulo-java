// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.java.impl.fileEditor.impl;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.EditorTabTitleProvider;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class JavaEditorTabTitleProvider implements EditorTabTitleProvider {
  @Override
  @Nullable
  @RequiredReadAction
  public String getEditorTabTitle(@Nonnull Project project, @Nonnull VirtualFile file) {
    String fileName = file.getName();
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) return null;
    PsiJavaFile javaFile = ObjectUtil.tryCast(PsiManager.getInstance(project).findFile(file), PsiJavaFile.class);
    if (javaFile == null) return null;
    PsiJavaModule moduleDescriptor = javaFile.getModuleDeclaration();
    if (moduleDescriptor == null) return null;
    return fileName + " (" + moduleDescriptor.getName() + ")";
  }
}
