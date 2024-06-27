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
package com.intellij.java.impl.codeInspection.inheritance;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.annotations.TestOnly;

import jakarta.annotation.Nonnull;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChangeSuperClassFix implements LocalQuickFix {
  @Nonnull
  private final PsiClass myNewSuperClass;
  @Nonnull
  private final PsiClass myOldSuperClass;
  private final int myPercent;

  public ChangeSuperClassFix(@Nonnull final PsiClass newSuperClass, final int percent, @Nonnull final PsiClass oldSuperClass) {
    myNewSuperClass = newSuperClass;
    myOldSuperClass = oldSuperClass;
    myPercent = percent;
  }

  @Nonnull
  @TestOnly
  public PsiClass getNewSuperClass() {
    return myNewSuperClass;
  }

  @TestOnly
  public int getPercent() {
    return myPercent;
  }

  @Nonnull
  @Override
  public String getName() {
    return String.format("Make extends '%s' - %s%%", myNewSuperClass.getQualifiedName(), myPercent);
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return InspectionLocalize.groupNamesInheritanceIssues().get();
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor problemDescriptor) {
    changeSuperClass((PsiClass)problemDescriptor.getPsiElement(), myOldSuperClass, myNewSuperClass);
  }

  /**
   * myOldSuperClass and myNewSuperClass can be interfaces or classes in any combination
   * <p/>
   * 1. not checks that myOldSuperClass is really super of aClass
   * 2. not checks that myNewSuperClass not exists in currently existed supers
   */
  private static void changeSuperClass(@Nonnull final PsiClass aClass,
                                       @Nonnull final PsiClass oldSuperClass,
                                       @Nonnull final PsiClass newSuperClass) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(aClass)) return;

    new WriteCommandAction.Simple(newSuperClass.getProject(), aClass.getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        if (aClass instanceof PsiAnonymousClass) {
          ((PsiAnonymousClass)aClass).getBaseClassReference().replace(factory.createClassReferenceElement(newSuperClass));
        }
        else if (oldSuperClass.isInterface()) {
          final PsiReferenceList interfaceList = aClass.getImplementsList();
          if (interfaceList != null) {
            for (final PsiJavaCodeReferenceElement interfaceRef : interfaceList.getReferenceElements()) {
              final PsiElement aInterface = interfaceRef.resolve();
              if (aInterface != null && aInterface.isEquivalentTo(oldSuperClass)) {
                interfaceRef.delete();
              }
            }
          }

          final PsiReferenceList extendsList = aClass.getExtendsList();
          if (extendsList != null) {
            final PsiJavaCodeReferenceElement newClassReference = factory.createClassReferenceElement(newSuperClass);
            if (extendsList.getReferenceElements().length == 0) {
              extendsList.add(newClassReference);
            }
          }
        }
        else {
          final PsiReferenceList extendsList = aClass.getExtendsList();
          if (extendsList != null && extendsList.getReferenceElements().length == 1) {
            extendsList.getReferenceElements()[0].delete();
            PsiElement ref = extendsList.add(factory.createClassReferenceElement(newSuperClass));
            JavaCodeStyleManager.getInstance(aClass.getProject()).shortenClassReferences(ref);
          }
        }
      }
    }.execute();
  }

  public static class LowPriority extends ChangeSuperClassFix implements LowPriorityAction {
    public LowPriority(@Nonnull final PsiClass newSuperClass, final int percent, @Nonnull final PsiClass oldSuperClass) {
      super(newSuperClass, percent, oldSuperClass);
    }
  }
}
