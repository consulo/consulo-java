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

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
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
  @jakarta.annotation.Nonnull
  private final PsiClass myOldSuperClass;
  private final int myPercent;

  public ChangeSuperClassFix(@jakarta.annotation.Nonnull final PsiClass newSuperClass, final int percent, @jakarta.annotation.Nonnull final PsiClass oldSuperClass) {
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

  @jakarta.annotation.Nonnull
  @Override
  public String getName() {
    return String.format("Make extends '%s' - %s%%", myNewSuperClass.getQualifiedName(), myPercent);
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getFamilyName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  @Override
  public void applyFix(@jakarta.annotation.Nonnull final Project project, @jakarta.annotation.Nonnull final ProblemDescriptor problemDescriptor) {
    changeSuperClass((PsiClass)problemDescriptor.getPsiElement(), myOldSuperClass, myNewSuperClass);
  }

  /**
   * myOldSuperClass and myNewSuperClass can be interfaces or classes in any combination
   * <p/>
   * 1. not checks that myOldSuperClass is really super of aClass
   * 2. not checks that myNewSuperClass not exists in currently existed supers
   */
  private static void changeSuperClass(@jakarta.annotation.Nonnull final PsiClass aClass,
                                       @jakarta.annotation.Nonnull final PsiClass oldSuperClass,
                                       @jakarta.annotation.Nonnull final PsiClass newSuperClass) {
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
