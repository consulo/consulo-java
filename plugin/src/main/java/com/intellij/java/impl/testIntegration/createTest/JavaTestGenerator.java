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
package com.intellij.java.impl.testIntegration.createTest;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.testIntegration.TestIntegrationUtils;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.language.Language;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

@ExtensionImpl
public class JavaTestGenerator implements TestGenerator {
  public JavaTestGenerator() {
  }

  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
      () -> project.getApplication().runWriteAction(new Supplier<PsiElement>() {
        public PsiElement get() {
          try {
            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            PsiClass targetClass = JavaDirectoryService.getInstance().createClass(d.getTargetDirectory(), d.getClassName());
            addSuperClass(targetClass, project, d.getSuperClassName());

            Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
            addTestMethods(
              editor,
              targetClass,
              d.getSelectedTestFrameworkDescriptor(),
              d.getSelectedMethods(),
              d.shouldGeneratedBefore(),
              d.shouldGeneratedAfter()
            );
            return targetClass;
          }
          catch (IncorrectOperationException e) {
            showErrorLater(project, d.getClassName());
            return null;
          }
        }
      })
    );
  }

  private static void addSuperClass(PsiClass targetClass, Project project, String superClassName) throws IncorrectOperationException {
    if (superClassName == null) return;

    PsiElementFactory ef = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;

    PsiClass superClass = findClass(project, superClassName);
    if (superClass != null) {
      superClassRef = ef.createClassReferenceElement(superClass);
    }
    else {
      superClassRef = ef.createFQClassNameReferenceElement(superClassName, GlobalSearchScope.allScope(project));
    }
    targetClass.getExtendsList().add(superClassRef);
  }

  @Nullable
  private static PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private static void addTestMethods(Editor editor,
                                     PsiClass targetClass,
                                     TestFramework descriptor,
                                     Collection<MemberInfo> methods,
                                     boolean generateBefore,
                                     boolean generateAfter) throws IncorrectOperationException {
    if (generateBefore) {
      generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, editor, null);
    }
    if (generateAfter) {
      generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, editor, null);
    }
    for (MemberInfo m : methods) {
      generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, editor, m.getMember().getName());
    }
  }

  private static void showErrorLater(Project project, String targetClassName) {
    project.getApplication().invokeLater(() -> Messages.showErrorDialog(
      project,
      CodeInsightLocalize.intentionErrorCannotCreateClassMessage(targetClassName).get(),
      CodeInsightLocalize.intentionErrorCannotCreateClassTitle().get()
    ));
  }

  private static void generateMethod(
    TestIntegrationUtils.MethodKind methodKind,
    TestFramework descriptor,
    PsiClass targetClass,
    Editor editor,
    @Nullable String name
  ) {
    PsiMethod method = (PsiMethod)targetClass.add(TestIntegrationUtils.createDummyMethod(targetClass));
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, method, name, true);
  }

  @Override
  public String toString() {
    return CodeInsightLocalize.intentionCreateTestDialogJava().get();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}