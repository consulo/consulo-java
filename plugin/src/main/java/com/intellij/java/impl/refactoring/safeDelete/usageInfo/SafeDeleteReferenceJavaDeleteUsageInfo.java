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
package com.intellij.java.impl.refactoring.safeDelete.usageInfo;

import com.intellij.java.impl.refactoring.safeDelete.ImportSearcher;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

/**
 * @author yole
 */
public class SafeDeleteReferenceJavaDeleteUsageInfo extends SafeDeleteReferenceSimpleDeleteUsageInfo {
  private static final Logger LOG = Logger.getInstance(SafeDeleteReferenceJavaDeleteUsageInfo.class);

  public SafeDeleteReferenceJavaDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceJavaDeleteUsageInfo(final PsiElement element,
                                                final PsiElement referencedElement,
                                                final int startOffset,
                                                final int endOffset,
                                                final boolean isNonCodeUsage,
                                                final boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  public void deleteElement() throws IncorrectOperationException {
    if (isSafeDelete()) {
      PsiElement element = getElement();
      LOG.assertTrue(element != null);
      PsiElement importStatement = ImportSearcher.getImport(element, false);
      if (importStatement != null) {
        importStatement.delete();
      }
      else {
        element.delete();
      }
    }
  }
}
