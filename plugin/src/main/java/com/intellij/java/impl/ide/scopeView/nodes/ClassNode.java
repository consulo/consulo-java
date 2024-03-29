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
package com.intellij.java.impl.ide.scopeView.nodes;

import consulo.ide.impl.idea.ide.scopeView.nodes.BasePsiNode;
import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ClassNode extends BasePsiNode<PsiClass> implements Comparable<ClassNode>{
  public ClassNode(final PsiClass aClass) {
    super(aClass);
  }

  public String toString() {
    final PsiClass aClass = (PsiClass)getPsiElement();
    return aClass != null && aClass.isValid() ? ClassPresentationUtil.getNameForClass(aClass, false) : "";
  }

  @Override
  public boolean isDeprecated() {
    final PsiClass psiClass = (PsiClass)getPsiElement();
    return psiClass != null && psiClass.isDeprecated();
  }

  public int compareTo(final ClassNode o) {
    final int comparision = ClassTreeNode.getClassPosition((PsiClass)getPsiElement()) - ClassTreeNode.getClassPosition((PsiClass)o.getPsiElement());
    if (comparision == 0) {
      return toString().compareToIgnoreCase(o.toString());
    }
    return comparision;
  }
}
