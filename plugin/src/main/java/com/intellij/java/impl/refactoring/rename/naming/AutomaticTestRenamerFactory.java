/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.usage.UsageInfo;

import java.util.Collection;

/**
 * @author yole
 */
@ExtensionImpl
public class AutomaticTestRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass) element).getQualifiedName();
      if (qualifiedName != null) {
        return !qualifiedName.endsWith("Test") && !qualifiedName.endsWith("TestCase");
      }
    }
    return false;
  }

  public String getOptionName() {
    return RefactoringLocalize.renameTests().get();
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameTests();
  }

  public void setEnabled(final boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameTests(enabled);
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new TestsRenamer((PsiClass) element, newName);
  }

  private static class TestsRenamer extends AutomaticRenamer {
    public TestsRenamer(PsiClass aClass, String newClassName) {

      appendTestClass(aClass, "Test");
      appendTestClass(aClass, "TestCase");

      suggestAllNames(aClass.getName(), newClassName);
    }

    private void appendTestClass(PsiClass aClass, String testSuffix) {
      final Project project = aClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass psiClassTest = facade.findClass(aClass.getQualifiedName() + testSuffix, GlobalSearchScope.projectScope(project));
      if (psiClassTest != null) {
        myElements.add(psiClassTest);
      }
    }

    public String getDialogTitle() {
      return RefactoringLocalize.renameTestsTitle().get();
    }

    public String getDialogDescription() {
      return RefactoringLocalize.renameTestsWithTheFollowingNamesTo().get();
    }

    public String entityName() {
      return RefactoringLocalize.entityNameTest().get();
    }
  }
}
