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
package com.intellij.java.analysis.refactoring;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.dataContext.DataContext;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class JavaRefactoringActionHandlerFactory {
  public static JavaRefactoringActionHandlerFactory getInstance() {
    return ServiceManager.getService(JavaRefactoringActionHandlerFactory.class);
  }

  /**
   * Creates handler for Anonymous To Inner refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * is not implemented.
   * @return
   */
  public abstract RefactoringActionHandler createAnonymousToInnerHandler();

  /**
   * Creates handler for Pull Members Up refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either a {@link PsiClass}, {@link PsiField} or {@link PsiMethod}.
   * In latter two cases, <code>elements[0]</code> is a member that will be preselected.
   */
  public abstract RefactoringActionHandler createPullUpHandler();

  /**
   * Creates handler for Push Members Down refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either a {@link PsiClass}, {@link PsiField} or {@link PsiMethod}.
   * In latter two cases, <code>elements[0]</code> is a member that will be preselected.
   */
  public abstract RefactoringActionHandler createPushDownHandler();

  /**
   * Creates handler for Use Interface Where Possible refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiClass</code>.
   * @return
   */
  public abstract RefactoringActionHandler createTurnRefsToSuperHandler();

  /**
   * Creates handler for Replace Temp With Query refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * is not implemented.
   */
  public abstract RefactoringActionHandler createTempWithQueryHandler();

  /**
   * Creates handler for Introduce Parameter refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either 1 <code>PsiExpression</code>, that will be an initializer for introduced parameter,
   * or 1 <code>PsiLocalVariable</code>, that will be replaced with introduced parameter.
   */
  public abstract RefactoringActionHandler createIntroduceParameterHandler();

  /**
   * Creates handler for Make Method Static refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiMethod</code>.
   */
  public abstract RefactoringActionHandler createMakeMethodStaticHandler();

  /**
   * Creates handler for Convert To Instance Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiMethod</code>.
   */
  public abstract RefactoringActionHandler createConvertToInstanceMethodHandler();

  /**
   * Creates handler for Replace Constructor With Factory Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either a <code>PsiMethod</code> that is a constructor, or a <code>PsiClass</code>
   * with implicit default constructor.
   */
  public abstract RefactoringActionHandler createReplaceConstructorWithFactoryHandler();


  /**
   * Creates handler for Replace Constructor With Factory Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either a <code>PsiClass</code> or any number of <code>PsiField</code>s.
   */
  public abstract RefactoringActionHandler createEncapsulateFieldsHandler();

  /**
   * Creates handler for Replace Method Code Duplicates refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts one <code>PsiMethod</code>.
   */
  public abstract RefactoringActionHandler createMethodDuplicatesHandler();

  /**
   * Creates handler for Change Method/Class Signature refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either 1 <code>PsiMethod</code> or 1 <code>PsiClass</code>
   */
  public abstract RefactoringActionHandler createChangeSignatureHandler();

  /**
   * Creates handler for Extract Superclass refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiClass</code>.
   */
  public abstract RefactoringActionHandler createExtractSuperclassHandler();

  /**
   * Creates handler for Generify (aka Type Cook) refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts any number of arbitrary <code>PsiElement</code>s. All code inside these elements will be generified.
   */
  public abstract RefactoringActionHandler createTypeCookHandler();

  /**
   * Creates handler for Inline refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 inlinable <code>PsiElement</code> (method, local variable or constant).
   */
  public abstract RefactoringActionHandler createInlineHandler();

  /**
   * Creates handler for Extract Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * is not implemented.
   */
  public abstract RefactoringActionHandler createExtractMethodHandler();

  public abstract RefactoringActionHandler createInheritanceToDelegationHandler();

  /**
   * Creates handler for Extract Interface refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiClass</code>.
   */
  public abstract RefactoringActionHandler createExtractInterfaceHandler();

  /**
   * Creates handler for Introduce Field refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either 1 <code>PsiExpression</code>, that will be an initializer for introduced field,
   * or 1 <code>PsiLocalVariable</code>, that will be replaced with introduced field.
   */
  public abstract RefactoringActionHandler createIntroduceFieldHandler();

  /**
   * Creates handler for Introduce Variable refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiExpression</code>, that will be an initializer for introduced variable.
   */
  public abstract RefactoringActionHandler createIntroduceVariableHandler();

  /**
   * Creates handler for Introduce Constant refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts either 1 <code>PsiExpression</code>, that will be an initializer for introduced constant,
   * or 1 <code>PsiLocalVariable</code>, that will be replaced with introduced constant.
   */
  public abstract IntroduceConstantHandler createIntroduceConstantHandler();

  /**
   * Creates handler for Invert Boolean refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts 1 <code>PsiMethod</code>, that will be inverted
   */
  public abstract RefactoringActionHandler createInvertBooleanHandler();
}
