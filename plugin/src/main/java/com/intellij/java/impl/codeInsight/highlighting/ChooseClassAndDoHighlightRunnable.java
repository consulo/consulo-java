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
package com.intellij.java.impl.codeInsight.highlighting;

import consulo.ide.impl.idea.codeInsight.highlighting.ChooseOneOrAllRunnable;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.codeEditor.Editor;

import java.util.ArrayList;
import java.util.List;

public abstract class ChooseClassAndDoHighlightRunnable extends ChooseOneOrAllRunnable<PsiClass> {
  public ChooseClassAndDoHighlightRunnable(PsiClassType[] classTypes, Editor editor, String title) {
    super(resolveClasses(classTypes), editor, title, PsiClass.class);
  }

  protected ChooseClassAndDoHighlightRunnable(final List<PsiClass> classes, final Editor editor, final String title) {
    super(classes, editor, title, PsiClass.class);
  }

  public static List<PsiClass> resolveClasses(final PsiClassType[] classTypes) {
    List<PsiClass> classes = new ArrayList<PsiClass>();
    for (PsiClassType classType : classTypes) {
      PsiClass aClass = classType.resolve();
      if (aClass != null) classes.add(aClass);
    }
    return classes;
  }

  @Override
  protected PsiElementListCellRenderer<PsiClass> createRenderer() {
    return new PsiClassListCellRenderer();
  }
}
