/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiImportStaticStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.util.lang.ObjectUtil;

public class ImportsHighlightUtil {
  public static HighlightInfo checkStaticOnDemandImportResolvesToClass(PsiImportStaticStatement statement) {
    if (statement.isOnDemand() && statement.resolveTargetClass() == null) {
      PsiJavaCodeReferenceElement ref = statement.getImportReference();
      if (ref != null) {
        PsiElement resolve = ref.resolve();
        if (resolve != null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ObjectUtil.notNull(ref.getReferenceNameElement(), ref)).descriptionAndTooltip("Class " + ref
              .getCanonicalText() + " not found").create();
        }
      }
    }
    return null;
  }
}
