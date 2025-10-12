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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 29, 2002
 * Time: 4:34:37 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ImplementAbstractMethodAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class ImplementAbstractMethodAction extends BaseIntentionAction {
  public ImplementAbstractMethodAction() {
    setText(CodeInsightLocalize.intentionImplementAbstractMethodFamily());
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiMethod method = findMethod(file, offset);

    if (method == null || !method.isValid()) return false;
    setText(getIntentionName(method));

    if (!method.getManager().isInProject(method)) return false;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    if (isAbstract || !method.hasModifierProperty(PsiModifier.PRIVATE) && !method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!isAbstract && !isOnIdentifier(file, offset)) return false;
      MyElementProcessor processor = new MyElementProcessor(method);
      if (containingClass.isEnum()) {
        for (PsiField field : containingClass.getFields()) {
          if (field instanceof PsiEnumConstant enumConstant) {
            final PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
            if (initializingClass == null) {
              processor.myHasMissingImplementations = true;
            }
            else {
              if (!processor.execute(initializingClass)) {
                break;
              }
            }
          }
        }
      }
      ClassInheritorsSearch.search(containingClass, false).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));
      return isAvailable(processor);
    }

    return false;
  }

  @RequiredReadAction
  private static boolean isOnIdentifier(PsiFile file, int offset) {
    final PsiElement psiElement = file.findElementAt(offset);
    return psiElement instanceof PsiIdentifier && psiElement.getParent() instanceof PsiMethod;
  }

  protected LocalizeValue getIntentionName(final PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.ABSTRACT)
      ? CodeInsightLocalize.intentionImplementAbstractMethodText(method.getName())
      : CodeInsightLocalize.intentionOverrideMethodText(method.getName());
  }

  static class MyElementProcessor implements PsiElementProcessor {
    private boolean myHasMissingImplementations;
    private boolean myHasExistingImplementations;
    private final PsiMethod myMethod;

    MyElementProcessor(final PsiMethod method) {
      myMethod = method;
    }

    public boolean hasMissingImplementations() {
      return myHasMissingImplementations;
    }

    public boolean hasExistingImplementations() {
      return myHasExistingImplementations;
    }

    @Override
    public boolean execute(@Nonnull PsiElement element) {
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass.isInterface()) return true;
        final PsiMethod existingImplementation = findExistingImplementation(aClass, myMethod);
        if (existingImplementation != null && !existingImplementation.hasModifierProperty(PsiModifier.ABSTRACT)) {
          myHasExistingImplementations = true;
        }
        else if (existingImplementation == null) {
          myHasMissingImplementations = true;
        }
        if (myHasMissingImplementations && myHasExistingImplementations) return false;
      }
      return true;
    }
  }

  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations();
  }

  @Nullable
  static PsiMethod findExistingImplementation(final PsiClass aClass, PsiMethod method) {
    final PsiMethod[] methods = aClass.findMethodsByName(method.getName(), false);
    for (PsiMethod candidate : methods) {
      final PsiMethod[] superMethods = candidate.findSuperMethods(false);
      for (PsiMethod superMethod : superMethods) {
        if (superMethod.equals(method)) {
          return candidate;
        }
      }
    }
    return null;
  }

  @RequiredReadAction
  private static PsiMethod findMethod(PsiFile file, int offset) {
    PsiMethod method = _findMethod(file, offset);
    if (method == null) {
      method = _findMethod(file, offset - 1);
    }
    return method;
  }

  @RequiredReadAction
  private static PsiMethod _findMethod(PsiFile file, int offset) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiMethod.class);
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;
    if (!project.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    invokeHandler(project, editor, method);
  }

  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new ImplementAbstractMethodHandler(project, editor, method).invoke();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
