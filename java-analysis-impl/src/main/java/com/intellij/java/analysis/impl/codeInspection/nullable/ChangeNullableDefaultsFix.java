/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiAnnotation;
import jakarta.annotation.Nonnull;

/**
* User: anna
* Date: 2/22/13
*/
class ChangeNullableDefaultsFix implements LocalQuickFix {
  private final NullableNotNullManager myManager;
  private final String myNotNullName;
  private final String myNullableName;

  public ChangeNullableDefaultsFix(PsiAnnotation notNull, PsiAnnotation nullable, NullableNotNullManager manager) {
    myNotNullName = notNull != null ? notNull.getQualifiedName() : null;
    myNullableName = nullable != null ? nullable.getQualifiedName() : null;
    myManager = manager;
  }

  ChangeNullableDefaultsFix(String notNull, String nullable, NullableNotNullManager manager) {
    myManager = manager;
    myNotNullName = notNull;
    myNullableName = nullable;
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Make \"" + (myNotNullName != null ? myNotNullName : myNullableName) + "\" default annotation");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    if (myNotNullName != null) {
      myManager.setDefaultNotNull(myNotNullName);
    } else {
      myManager.setDefaultNullable(myNullableName);
    }
  }
}
