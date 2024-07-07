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

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: May 12, 2003
 * Time: 6:41:19 PM
 * To change this template use Options | File Templates.
 */
public abstract class CreateConstructorFromThisOrSuperFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateConstructorFromThisOrSuperFix.class);

  protected PsiMethodCallExpression myMethodCall;

  public CreateConstructorFromThisOrSuperFix(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  @NonNls
  protected abstract String getSyntheticMethodName ();

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    if (!ref.getText().equals(getSyntheticMethodName())) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    if (method == null || !method.isConstructor()) return false;
    if (CreateMethodFromUsageFix.hasErrorsInArgumentList(myMethodCall)) return false;
    List<PsiClass> targetClasses = getTargetClasses(myMethodCall);
    if (targetClasses.isEmpty()) return false;

    if (CreateFromUsageUtils.shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
      setText(JavaQuickFixBundle.message("create.constructor.text", targetClasses.get(0).getName()));
      return true;
    }

    return false;
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    final PsiFile callSite = myMethodCall.getContainingFile();
    final Project project = myMethodCall.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {
      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod)targetClass.add(constructor);

      final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myMethodCall.getArgumentList(),
                                                  getTargetSubstitutor(myMethodCall));

      final PsiFile psiFile = myMethodCall.getContainingFile();

      templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
      final RangeMarker rangeMarker = psiFile.getViewProvider().getDocument().createRangeMarker(myMethodCall.getTextRange());

      constructor = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(constructor);

      targetClass = constructor.getContainingClass();
      myMethodCall = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), myMethodCall.getClass());
      rangeMarker.dispose();

      Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      if (editor == null) return;
      final TextRange textRange = constructor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
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
                CreateFromUsageUtils.setupMethodBody(constructor);
                CreateFromUsageUtils.setupEditor(constructor, editor);

                LanguageUndoUtil.markPsiFileForUndo(callSite);
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

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
    PsiMethod method = (PsiMethod) methodCall.getMethodExpression().resolve();
    PsiExpressionList argumentList = methodCall.getArgumentList();
    List<PsiClass> classes = getTargetClasses(element);
    return classes.size() > 0 && !CreateFromUsageUtils.shouldCreateConstructor(classes.get(0), argumentList, method);
  }

  @Override
  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }
}
