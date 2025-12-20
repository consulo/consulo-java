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

/*
 * @author max
 */
package com.intellij.java.impl.find.findUsages;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiThrowStatement;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.usage.UsageTarget;
import consulo.usage.UsageTargetProvider;
import consulo.language.editor.TargetElementUtil;

import jakarta.annotation.Nullable;

@ExtensionImpl
public class ThrowsUsageTargetProvider implements UsageTargetProvider {
  @Override
  @Nullable
  public UsageTarget[] getTargets(Editor editor, PsiFile file) {
    if (editor == null || file == null) return null;

    PsiElement element = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset()));
    if (element == null) return null;

    if (element instanceof PsiKeyword && PsiKeyword.THROWS.equals(element.getText())) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(element)};
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiThrowStatement) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(parent)};
    }

    return null;
  }

  @Override
  public UsageTarget[] getTargets(PsiElement psiElement) {
    return null;
  }
}