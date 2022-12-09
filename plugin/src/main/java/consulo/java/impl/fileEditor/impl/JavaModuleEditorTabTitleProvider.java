/*
 * Copyright 2013-2018 consulo.io
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

package consulo.java.impl.fileEditor.impl;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.EditorTabTitleProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2018-07-15
 */
@ExtensionImpl
public class JavaModuleEditorTabTitleProvider implements EditorTabTitleProvider {
  @Nullable
  @Override
  @RequiredReadAction
  public String getEditorTabTitle(Project project, VirtualFile virtualFile) {
    if (PsiJavaModule.MODULE_INFO_CLASS.equals(virtualFile.getNameWithoutExtension())) {
      PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
      if (file instanceof PsiJavaFile) {
        PsiJavaModule moduleDeclaration = ((PsiJavaFile) file).getModuleDeclaration();
        if (moduleDeclaration != null) {
          return moduleDeclaration.getName();
        }
      }
    }
    return null;
  }
}
