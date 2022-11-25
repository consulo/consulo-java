/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.impl.ide.projectView.impl;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.project.ui.view.tree.PsiDirectoryNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.PresentationData;
import consulo.util.lang.StringUtil;

/**
 * @author VISTALL
 * @since 10-Jan-17
 */
public class JavaModuleRootTreeNode extends PsiDirectoryNode {
  public JavaModuleRootTreeNode(Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  @RequiredReadAction
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);

    PsiDirectory value = getValue();
    if (value == null) {
      return;
    }

    PsiFile file = value.findFile(PsiJavaModule.MODULE_INFO_CLS_FILE);
    if (file instanceof PsiJavaFile) {
      String name = "INVALID";
      PsiJavaModule moduleDeclaration = ((PsiJavaFile) file).getModuleDeclaration();
      if (moduleDeclaration != null) {
        name = StringUtil.notNullize(moduleDeclaration.getName(), name);
      }
      data.setPresentableText(name);
    }
  }
}
