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
package com.intellij.java.impl.codeInsight.navigation;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class JavaGotoTargetRendererProvider implements GotoTargetRendererProvider {
  @Override
  public PsiElementListCellRenderer getRenderer(final PsiElement element, GotoTargetHandler.GotoData gotoData) {
    if (element instanceof PsiMethod) {
      return new MethodCellRenderer(gotoData.hasDifferentNames());
    } else if (element instanceof PsiClass) {
      return new PsiClassListCellRenderer();
    }
    return null;
  }

}
