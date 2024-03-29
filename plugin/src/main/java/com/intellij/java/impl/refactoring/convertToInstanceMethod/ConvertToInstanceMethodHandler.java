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
package com.intellij.java.impl.refactoring.convertToInstanceMethod;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.logging.Logger;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodHandler.class);
  static final String REFACTORING_NAME = RefactoringBundle.message("convert.to.instance.method.title");

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = dataContext.getData(LangDataKeys.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CONVERT_TO_INSTANCE_METHOD);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeMethodStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) return;
    final PsiMethod method = (PsiMethod)elements[0];
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      String message = RefactoringBundle.message("convertToInstanceMethod.method.is.not.static", method.getName());
      Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CONVERT_TO_INSTANCE_METHOD);
      return;
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    List<PsiParameter> suitableParameters = new ArrayList<PsiParameter>();
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    boolean classesInProjectFound = false;
    for (final PsiParameter parameter : parameters) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
          resolvableClassesFound = true;
          final boolean inProject = method.getManager().isInProject(psiClass);
          if (inProject) {
            classesInProjectFound = true;
            suitableParameters.add(parameter);
          }
        }
      }
    }
    if (suitableParameters.isEmpty()) {
      String message = null;
      if (!classTypesFound) {
        message = RefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type");
      }
      else if (!resolvableClassesFound) {
        message = RefactoringBundle.message("convertToInstanceMethod.all.reference.type.parametres.have.unknown.types");
      }
      else if (!classesInProjectFound) {
        message = RefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.are.not.in.project");
      }
      LOG.assertTrue(message != null);
      Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(message), REFACTORING_NAME, HelpID.CONVERT_TO_INSTANCE_METHOD);
      return;
    }

    new ConvertToInstanceMethodDialog(
      method,
      suitableParameters.toArray(new PsiParameter[suitableParameters.size()])).show();
  }
}
