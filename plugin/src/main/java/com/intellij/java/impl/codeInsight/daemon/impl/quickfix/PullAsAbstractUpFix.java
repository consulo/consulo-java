/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.intention.impl.RunRefactoringAction;
import com.intellij.java.impl.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.java.impl.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;

public class PullAsAbstractUpFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(PullAsAbstractUpFix.class);
  private final String myName;

  public PullAsAbstractUpFix(PsiMethod psiMethod, final String name) {
    super(psiMethod);
    myName = name;
  }

  @Override
  @Nonnull
  public String getText() {
    return myName;
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return "Pull up";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    return startElement instanceof PsiMethod && startElement.isValid() && ((PsiMethod) startElement).getContainingClass() != null;
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiMethod method = (PsiMethod) startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) return;

    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass != null);

    PsiManager manager = containingClass.getManager();
    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass) containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && manager.isInProject(baseClass)) {
        pullUp(method, containingClass, baseClass);
      }
    } else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<PsiClass>();
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getImplementsListTypes());

      if (classesToPullUp.size() == 0) {
        //check visibility
        new ExtractInterfaceHandler().invoke(project, new PsiElement[]{containingClass}, null);
      } else if (classesToPullUp.size() == 1) {
        pullUp(method, containingClass, classesToPullUp.iterator().next());
      } else if (editor != null) {
        JBPopup popup = PopupNavigationUtil.getPsiElementPopup(classesToPullUp.toArray(new PsiClass[classesToPullUp.size()]), new PsiClassListCellRenderer(),
            "Choose super class",
            new PsiElementProcessor<PsiClass>() {
              @Override
              public boolean execute(@Nonnull PsiClass aClass) {
                pullUp(method, containingClass, aClass);
                return false;
              }
            }, classesToPullUp.iterator().next());

        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
      }
    }
  }


  private static void collectClassesToPullUp(PsiManager manager, LinkedHashSet<PsiClass> classesToPullUp, PsiClassType[] extendsListTypes) {
    for (PsiClassType extendsListType : extendsListTypes) {
      PsiClass resolve = extendsListType.resolve();
      if (resolve != null && manager.isInProject(resolve)) {
        classesToPullUp.add(resolve);
      }
    }
  }

  private static void pullUp(PsiMethod method, PsiClass containingClass, PsiClass baseClass) {
    if (!FileModificationService.getInstance().prepareFileForWrite(baseClass.getContainingFile())) return;
    final MemberInfo memberInfo = new MemberInfo(method);
    memberInfo.setChecked(true);
    memberInfo.setToAbstract(true);
    new PullUpProcessor(containingClass, baseClass, new MemberInfo[]{memberInfo}, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerQuickFix(@Nonnull PsiMethod methodWithOverrides, @Nonnull QuickFixActionRegistrar registrar) {
    PsiClass containingClass = methodWithOverrides.getContainingClass();
    if (containingClass == null) return;
    final PsiManager manager = containingClass.getManager();

    boolean canBePulledUp = true;
    String name = "Pull method \'" + methodWithOverrides.getName() + "\' up";
    if (containingClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass) containingClass).getBaseClassType();
      final PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null) return;
      if (!manager.isInProject(baseClass)) return;
      if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        name = "Pull method \'" + methodWithOverrides.getName() + "\' up and make it abstract";
      }
    } else {
      final LinkedHashSet<PsiClass> classesToPullUp = new LinkedHashSet<PsiClass>();
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getExtendsListTypes());
      collectClassesToPullUp(manager, classesToPullUp, containingClass.getImplementsListTypes());
      if (classesToPullUp.size() == 0) {
        name = "Extract method \'" + methodWithOverrides.getName() + "\' to new interface";
        canBePulledUp = false;
      } else if (classesToPullUp.size() == 1) {
        final PsiClass baseClass = classesToPullUp.iterator().next();
        name = "Pull method \'" + methodWithOverrides.getName() + "\' to \'" + baseClass.getName() + "\'";
        if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          name += " and make it abstract";
        }
      }
      registrar.register(new RunRefactoringAction(new ExtractInterfaceHandler(), "Extract interface"));
      registrar.register(new RunRefactoringAction(new ExtractSuperclassHandler(), "Extract superclass"));
    }


    if (canBePulledUp) {
      registrar.register(new RunRefactoringAction(new JavaPullUpHandler(), "Pull members up"));
    }
    registrar.register(new PullAsAbstractUpFix(methodWithOverrides, name));
  }
}
