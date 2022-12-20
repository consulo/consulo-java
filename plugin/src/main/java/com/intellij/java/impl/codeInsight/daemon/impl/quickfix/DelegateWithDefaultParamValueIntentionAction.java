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

import com.intellij.java.impl.codeInsight.template.impl.TextExpression;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.component.util.Iconable;
import consulo.document.RangeMarker;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;

/**
 * User: anna
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.DelegateWithDefaultParamValueIntentionAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class DelegateWithDefaultParamValueIntentionAction extends PsiElementBaseIntentionAction implements Iconable, LowPriorityAction {
  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      if (!parameter.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declarationScope;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && !containingClass.isInterface()) {
          return containingClass.findMethodBySignature(generateMethodPrototype(method, parameter), false) == null;
        }
      }
    }
    return false;
  }

  @Override
  public Image getIcon(int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }

  private static PsiMethod generateMethodPrototype(PsiMethod method, PsiParameter... params) {
    final PsiMethod prototype = (PsiMethod)method.copy();
    final PsiCodeBlock body = prototype.getBody();
    final PsiCodeBlock emptyBody = JavaPsiFacade.getElementFactory(method.getProject()).createMethodFromText("void foo(){}", prototype).getBody();
    assert emptyBody != null;
    if (body != null) {
      body.replace(emptyBody);
    } else {
      prototype.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);
      prototype.addBefore(emptyBody, null);
    }
    for (int i = params.length - 1; i >= 0; i--) {
      PsiParameter param = params[i];
      final int parameterIndex = method.getParameterList().getParameterIndex(param);
      prototype.getParameterList().getParameters()[parameterIndex].delete();
    }
    return prototype;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiParameter[] parameters = getParams(element);
    if (parameters == null || parameters.length == 0) return;
    final PsiMethod method = (PsiMethod)parameters[0].getDeclarationScope();
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiMethod existingMethod = method.getContainingClass().findMethodBySignature(methodPrototype, false);
    if (existingMethod != null) {
      editor.getCaretModel().moveToOffset(existingMethod.getTextOffset());
      HintManager.getInstance().showErrorHint(editor, "Method with the chosen signature already exist");
      return;
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        
        final PsiMethod prototype = (PsiMethod)method.getContainingClass().addBefore(methodPrototype, method);
        RefactoringUtil.fixJavadocsForParams(prototype, new HashSet<PsiParameter>(Arrays.asList(prototype.getParameterList().getParameters())));
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(prototype);

        PsiCodeBlock body = prototype.getBody();
        final String callArgs =
          "(" + StringUtil.join(method.getParameterList().getParameters(), psiParameter -> {
            if (ArrayUtil.find(parameters, psiParameter) > -1) return "IntelliJIDEARulezzz";
            return psiParameter.getName();
          }, ",") + ");";
        final String methodCall;
        if (method.getReturnType() == null) {
          methodCall = "this";
        } else if (!PsiType.VOID.equals(method.getReturnType())) {
          methodCall = "return " + method.getName();
        } else {
          methodCall = method.getName();
        }
        body.add(JavaPsiFacade.getElementFactory(project).createStatementFromText(methodCall + callArgs, method));
        body = (PsiCodeBlock)CodeStyleManager.getInstance(project).reformat(body);
        final PsiStatement stmt = body.getStatements()[0];
        PsiExpression expr = null;
        if (stmt instanceof PsiReturnStatement) {
          expr = ((PsiReturnStatement)stmt).getReturnValue();
        } else if (stmt instanceof PsiExpressionStatement) {
          expr = ((PsiExpressionStatement)stmt).getExpression();
        }
        if (expr instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExp = (PsiMethodCallExpression)expr;
          RangeMarker rangeMarker = editor.getDocument().createRangeMarker(prototype.getTextRange());
          for (PsiParameter parameter : parameters) {
            final PsiExpression exprToBeDefault =
              methodCallExp.getArgumentList().getExpressions()[method.getParameterList().getParameterIndex(parameter)];
            builder.replaceElement(exprToBeDefault, new TextExpression(""));
          }
          Template template = builder.buildTemplate();
          editor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
    
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
          editor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    
          rangeMarker.dispose();
    
          CreateFromUsageBaseFix.startTemplate(editor, template, project);
        }
      }
    };
    if (startInWriteAction()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  @Nullable
  protected PsiParameter[] getParams(PsiElement element) {
    return new PsiParameter[]{PsiTreeUtil.getParentOfType(element, PsiParameter.class)};
  }

  @Nonnull
  @Override
  public String getText() {
    return "Generate delegated method with default parameter value";
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }
}
