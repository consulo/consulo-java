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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Mike
 */
public class CreateFieldFromUsageFix extends CreateVarFromUsageFix {

  public CreateFieldFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
    setText(JavaQuickFixLocalize.createFieldFromUsageFamily());
  }

  @Override
  protected LocalizeValue getText(String varName) {
    return JavaQuickFixLocalize.createFieldFromUsageText(varName);
  }

  protected boolean createConstantField() {
    return false;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return psiClass.getManager().isInProject(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType();
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    final Project project = myReferenceExpression.getProject();
    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (factory == null) factory = JavaPsiFacade.getElementFactory(project);

    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? myReferenceExpression : enclosingContext, PsiMethod.class,
                                                     PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);

    String fieldName = myReferenceExpression.getReferenceName();
    assert fieldName != null;

    PsiField field = factory.createField(fieldName, PsiType.INT);
    if (createConstantField()) {
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    }

    if (createConstantField()) {
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    } else {
      if (!targetClass.isInterface() && shouldCreateStaticMember(myReferenceExpression, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      }
      if (shouldCreateFinalMember(myReferenceExpression, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      }
    }

    field = CreateFieldFromUsageHelper.insertField(targetClass, field, myReferenceExpression);

    setupVisibility(parentClass, targetClass, field.getModifierList());

    createFieldFromUsageTemplate(targetClass, project, expectedTypes, field, createConstantField(), myReferenceExpression);
  }

  public static void createFieldFromUsageTemplate(final PsiClass targetClass,
                                                  final Project project,
                                                  final ExpectedTypeInfo[] expectedTypes,
                                                  final PsiField field,
                                                  final boolean createConstantField,
                                                  final PsiElement context) {
    final PsiFile targetFile = targetClass.getContainingFile();
    final Editor newEditor = positionCursor(project, targetFile, field);
    if (newEditor == null) return;
    Template template =
      CreateFieldFromUsageHelper.setupTemplate(field, expectedTypes, targetClass, newEditor, context, createConstantField);

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiField psiField = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiField.class, false);
        if (psiField != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CodeStyleManager.getInstance(project).reformat(psiField);
            }
          });
          newEditor.getCaretModel().moveToOffset(psiField.getTextRange().getEndOffset() - 1);
        }
      }
    });
  }

  private static boolean shouldCreateFinalMember(@Nonnull PsiReferenceExpression ref, @Nonnull PsiClass targetClass) {
    if (!PsiTreeUtil.isAncestor(targetClass, ref, true)) {
      return false;
    }
    final PsiElement element = PsiTreeUtil.getParentOfType(ref, PsiClassInitializer.class, PsiMethod.class);
    if (element instanceof PsiClassInitializer) {
      return true;
    }

    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return true;
    }

    return false;
  }
}
