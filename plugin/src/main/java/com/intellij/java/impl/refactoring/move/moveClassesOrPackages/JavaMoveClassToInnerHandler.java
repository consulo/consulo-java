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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author Max Medvedev
 */
@ExtensionImpl(id = "java")
public class JavaMoveClassToInnerHandler implements MoveClassToInnerHandler {
  private static final Logger LOG = Logger.getInstance(JavaMoveClassToInnerHandler.class);

  @Override
  public PsiClass moveClass(@Nonnull PsiClass aClass, @Nonnull PsiClass targetClass) {
    if (aClass.getLanguage() != JavaLanguage.INSTANCE) {
      return null;
    }

    ChangeContextUtil.encodeContextInfo(aClass, true);
    PsiClass newClass = (PsiClass)targetClass.addBefore(aClass, targetClass.getRBrace());
    if (targetClass.isInterface()) {
      PsiUtil.setModifierProperty(newClass, PsiModifier.PACKAGE_LOCAL, true);
    }
    else {
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, true);
    }
    return (PsiClass)ChangeContextUtil.decodeContextInfo(newClass, null, null);
  }

  @Override
  public List<PsiElement> filterImports(@Nonnull List<UsageInfo> usageInfos, @Nonnull Project project) {
    final List<PsiElement> importStatements = new ArrayList<PsiElement>();
    if (!CodeStyleSettingsManager.getSettings(project).INSERT_INNER_CLASS_IMPORTS) {
      filterUsagesInImportStatements(usageInfos, importStatements);
    }
    else {
      //rebind imports first
      Collections.sort(usageInfos, new Comparator<UsageInfo>() {
        public int compare(UsageInfo o1, UsageInfo o2) {
          return PsiUtil.BY_POSITION.compare(o1.getElement(), o2.getElement());
        }
      });
    }
    return importStatements;
  }

  private static void filterUsagesInImportStatements(final List<UsageInfo> usages, final List<PsiElement> importStatements) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext(); ) {
      UsageInfo usage = iterator.next();
      PsiElement element = usage.getElement();
      if (element == null) continue;
      PsiImportStatement stmt = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
      if (stmt != null) {
        importStatements.add(stmt);
        iterator.remove();
      }
    }
  }

  public void retargetClassRefsInMoved(@Nonnull final Map<PsiElement, PsiElement> oldToNewElementsMapping) {
    for (final PsiElement newClass : oldToNewElementsMapping.values()) {
      if (newClass.getLanguage() != JavaLanguage.INSTANCE) continue;
      newClass.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceElement(final PsiJavaCodeReferenceElement reference) {
          PsiElement element = reference.resolve();
          if (element instanceof PsiClass) {
            for (PsiElement oldClass : oldToNewElementsMapping.keySet()) {
              if (PsiTreeUtil.isAncestor(oldClass, element, false)) {
                PsiClass newInnerClass =
                  findMatchingClass((PsiClass)oldClass, (PsiClass)oldToNewElementsMapping.get(oldClass), (PsiClass)element);
                try {
                  reference.bindToElement(newInnerClass);
                  return;
                }
                catch (IncorrectOperationException ex) {
                  LOG.error(ex);
                }
              }
            }
          }
          super.visitReferenceElement(reference);
        }
      });
    }
  }


  private static PsiClass findMatchingClass(final PsiClass classToMove, final PsiClass newClass, final PsiClass innerClass) {
    if (classToMove == innerClass) {
      return newClass;
    }
    PsiClass parentClass = findMatchingClass(classToMove, newClass, innerClass.getContainingClass());
    PsiClass newInnerClass = parentClass.findInnerClassByName(innerClass.getName(), false);
    assert newInnerClass != null;
    return newInnerClass;
  }

  public void retargetNonCodeUsages(@Nonnull final Map<PsiElement, PsiElement> oldToNewElementMap,
                                    @Nonnull final NonCodeUsageInfo[] nonCodeUsages) {
    for (PsiElement newClass : oldToNewElementMap.values()) {
      if (newClass.getLanguage() != JavaLanguage.INSTANCE) continue;
      newClass.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(final PsiElement element) {
          super.visitElement(element);
          List<NonCodeUsageInfo> list = element.getCopyableUserData(MoveClassToInnerProcessor.ourNonCodeUsageKey);
          if (list != null) {
            for (NonCodeUsageInfo info : list) {
              for (int i = 0; i < nonCodeUsages.length; i++) {
                if (nonCodeUsages[i] == info) {
                  nonCodeUsages[i] = info.replaceElement(element);
                  break;
                }
              }
            }
            element.putCopyableUserData(MoveClassToInnerProcessor.ourNonCodeUsageKey, null);
          }
        }
      });
    }
  }

  @Override
  public void removeRedundantImports(PsiFile targetClassFile) {
    if (targetClassFile instanceof PsiJavaFile) {
      JavaCodeStyleManager.getInstance(targetClassFile.getProject()).removeRedundantImports((PsiJavaFile)targetClassFile);
    }
  }
}
