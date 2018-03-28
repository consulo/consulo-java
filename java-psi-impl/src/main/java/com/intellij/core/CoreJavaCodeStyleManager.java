/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.LinkedHashSet;

public class CoreJavaCodeStyleManager extends JavaCodeStyleManager {
  @Override
  public boolean addImport(@Nonnull PsiJavaFile file, @Nonnull PsiClass refClass) {
    return false;
  }

  @Override
  public PsiElement shortenClassReferences(@Nonnull PsiElement element,
                                           @MagicConstant(flags = {DO_NOT_ADD_IMPORTS,
                                                   INCOMPLETE_CODE
                                           }) int flags)
    throws IncorrectOperationException {
    return null;
  }

  @Nonnull
  @Override
  public String getPrefixByVariableKind(VariableKind variableKind) {
    return "";
  }

  @Nonnull
  @Override
  public String getSuffixByVariableKind(VariableKind variableKind) {
    return "";
  }

  @Override
  public int findEntryIndex(@Nonnull PsiImportStatementBase statement) {
    return 0;
  }

  @Override
  public PsiElement shortenClassReferences(@Nonnull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void shortenClassReferences(@Nonnull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
  }

  @Override
  public void optimizeImports(@Nonnull PsiFile file) throws IncorrectOperationException {
  }

  @Override
  public PsiImportList prepareOptimizeImportsResult(@Nonnull PsiJavaFile file) {
    return null;
  }

  @Override
  public SuggestedNameInfo suggestVariableName(@Nonnull VariableKind kind,
                                               @javax.annotation.Nullable String propertyName,
                                               @javax.annotation.Nullable PsiExpression expr,
                                               @javax.annotation.Nullable PsiType type,
                                               boolean correctKeywords) {
    return SuggestedNameInfo.NULL_INFO;
  }

  @Override
  public String variableNameToPropertyName(@NonNls String name, VariableKind variableKind) {
    return null;
  }

  @Override
  public String propertyNameToVariableName(@NonNls String propertyName, VariableKind variableKind) {
    return null;
  }

  @Override
  public String suggestUniqueVariableName(@NonNls String baseName, PsiElement place, boolean lookForward) {
    int index = 0;
    PsiElement scope = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class, PsiCodeBlock.class, PsiMethod.class);
    NextName:
    while (true) {
      String name = baseName;
      if (index > 0) {
        name += index;
      }
      index++;
      if (PsiUtil.isVariableNameUnique(name, place)) {
        if (lookForward) {
          final String name1 = name;
          PsiElement run = scope;
          while (run != null) {
            class CancelException extends RuntimeException {
            }
            try {
              run.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitAnonymousClass(final PsiAnonymousClass aClass) {
                }

                @Override public void visitVariable(PsiVariable variable) {
                  if (name1.equals(variable.getName())) {
                    throw new CancelException();
                  }
                }
              });
            }
            catch (CancelException e) {
              continue NextName;
            }
            run = run.getNextSibling();
            if (scope instanceof PsiMethod) {//do not check next member for param name conflict
              break;
            }
          }

        }
        return name;
      }
    }
  }

  @Nonnull
  @Override
  public SuggestedNameInfo suggestUniqueVariableName(@Nonnull final SuggestedNameInfo baseNameInfo,
                                                     PsiElement place,
                                                     boolean ignorePlaceName,
                                                     boolean lookForward) {
    final String[] names = baseNameInfo.names;
    final LinkedHashSet<String> uniqueNames = new LinkedHashSet<String>(names.length);
    for (String name : names) {
      if (ignorePlaceName && place instanceof PsiNamedElement) {
        final String placeName = ((PsiNamedElement)place).getName();
        if (Comparing.strEqual(placeName, name)) {
          uniqueNames.add(name);
          continue;
        }
      }
      uniqueNames.add(suggestUniqueVariableName(name, place, lookForward));
    }

    return new SuggestedNameInfo(ArrayUtil.toStringArray(uniqueNames)) {
      @Override
      public void nameChosen(String name) {
        baseNameInfo.nameChosen(name);
      }
    };
  }

  @Override
  public PsiElement qualifyClassReferences(@Nonnull PsiElement element) {
    return element;
  }

  @Override
  public void removeRedundantImports(@Nonnull PsiJavaFile file) throws IncorrectOperationException {
  }

  @Override
  public Collection<PsiImportStatementBase> findRedundantImports(PsiJavaFile file) {
    return null;
  }
}
