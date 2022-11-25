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
package com.intellij.java.impl.ide.projectView.impl.nodes;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.PresentationData;

import java.util.Collection;

public class PsiMethodNode extends BasePsiMemberNode<PsiMethod>{
  public PsiMethodNode(Project project, PsiMethod value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }

  @Override
  public void updateImpl(PresentationData data) {
    String name = PsiFormatUtil.formatMethod(
      getValue(),
        PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                              PsiFormatUtilBase.SHOW_TYPE |
                              PsiFormatUtilBase.TYPE_AFTER |
                              PsiFormatUtilBase.SHOW_TYPE_PARAMETERS |
                              PsiFormatUtilBase.SHOW_TYPE_PARAMETER_EXTENDS |
                              PsiFormatUtilBase.SHOW_PARAMETERS,
        PsiFormatUtilBase.SHOW_TYPE
    );
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  public boolean isConstructor() {
    final PsiMethod psiMethod = getValue();
    return psiMethod != null && psiMethod.isConstructor();
  }

  @Override
  public int getWeight() {
    return isConstructor() ? 40 : 50;
  }

  @Override
  public String getTitle() {
    final PsiMethod method = getValue();
    if (method != null) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        return aClass.getQualifiedName();
      }
      else {
        return method.toString();
      }
    }
    return super.getTitle();
  }
}
