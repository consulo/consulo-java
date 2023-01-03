
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import javax.annotation.Nonnull;

class EncapsulateFieldsViewDescriptor implements UsageViewDescriptor {
  private final PsiField[] myFields;

  public EncapsulateFieldsViewDescriptor(FieldDescriptor[] descriptors) {
    myFields = new PsiField[descriptors.length];
    for (int i = 0; i < descriptors.length; i++) {
      myFields[i] = descriptors[i].getField();
    }
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("encapsulate.fields.fields.to.be.encapsulated");
  }

  @Nonnull
  public PsiElement[] getElements() {
    return myFields;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
