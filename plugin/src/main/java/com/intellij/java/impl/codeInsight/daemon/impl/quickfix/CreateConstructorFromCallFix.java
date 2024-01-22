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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import java.util.List;

/**
 * @author mike
 */
public class CreateConstructorFromCallFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateConstructorFromCallFix.class);

  private final PsiConstructorCall myConstructorCall;

  public CreateConstructorFromCallFix(PsiConstructorCall constructorCall) {
    myConstructorCall = constructorCall;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    final Project project = myConstructorCall.getProject();
    JVMElementFactory elementFactory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (elementFactory == null) elementFactory = JavaPsiFacade.getElementFactory(project);

    try {
      PsiMethod constructor = (PsiMethod)targetClass.add(elementFactory.createConstructor());

      final PsiFile file = targetClass.getContainingFile();
      TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myConstructorCall.getArgumentList(),
                                                 getTargetSubstitutor(myConstructorCall));
      final PsiMethod superConstructor = CreateClassFromNewFix.setupSuperCall(targetClass, constructor, templateBuilder);

      constructor = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(constructor);
      Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      if (editor == null) return;
      final TextRange textRange = constructor.getTextRange();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                final int offset = editor.getCaretModel().getOffset();
                PsiMethod constructor = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);
                if (superConstructor == null) {
                  CreateFromUsageUtils.setupMethodBody(constructor);
                } else {
                  OverrideImplementUtil.setupMethodBody(constructor, superConstructor, targetClass);
                }
                CreateFromUsageUtils.setupEditor(constructor, editor);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiFile getTargetFile(PsiElement element) {
    final PsiConstructorCall constructorCall = (PsiConstructorCall)element;

    //Enum constants constructors are file local
    if (constructorCall instanceof PsiEnumConstant) return constructorCall.getContainingFile();

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(constructorCall);
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  @Override
  protected PsiElement getElement() {
    if (!myConstructorCall.isValid() || !myConstructorCall.getManager().isInProject(myConstructorCall)) return null;

    PsiExpressionList argumentList = myConstructorCall.getArgumentList();
    if (argumentList == null) return null;

    if (myConstructorCall instanceof PsiEnumConstant) return myConstructorCall;

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(myConstructorCall);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return myConstructorCall;

    return null;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiConstructorCall constructorCall = (PsiConstructorCall)element;
    PsiMethod method = constructorCall.resolveConstructor();
    PsiExpressionList argumentList = constructorCall.getArgumentList();
    List<PsiClass> targetClasses = getTargetClasses(constructorCall);
    if (targetClasses.isEmpty()) return false;
    PsiClass targetClass = targetClasses.get(0);

    return !CreateFromUsageUtils.shouldCreateConstructor(targetClass, argumentList, method);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiElement element = getElement(myConstructorCall);

    PsiFile targetFile = getTargetFile(myConstructorCall);
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (CreateFromUsageUtils.shouldShowTag(offset, element, myConstructorCall)) {
      setText(JavaQuickFixBundle.message("create.constructor.from.new.text"));
      return true;
    }

    return false;
  }

  private static PsiJavaCodeReferenceElement getReferenceElement(PsiConstructorCall constructorCall) {
    if (constructorCall instanceof PsiNewExpression) {
      return ((PsiNewExpression)constructorCall).getClassOrAnonymousClassReference();
    }
    return null;
  }

  private static PsiElement getElement(PsiElement targetElement) {
    if (targetElement instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)targetElement);
      if (referenceElement == null) return null;
      return referenceElement.getReferenceNameElement();
    }
    else if (targetElement instanceof PsiEnumConstant) {
      return targetElement;
    }

    return null;
  }
}
