/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ig.classmetrics;

import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import org.jetbrains.annotations.NonNls;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import com.siyeh.ig.psiutils.ClassUtils;
import com.intellij.java.impl.ig.psiutils.LibraryUtil;

class CouplingVisitor extends JavaRecursiveElementVisitor {
  private boolean m_inClass = false;
  private final PsiClass m_class;
  private final boolean m_includeJavaClasses;
  private final boolean m_includeLibraryClasses;
  private final Set<String> m_dependencies = new HashSet<String>(10);

  CouplingVisitor(PsiClass aClass, boolean includeJavaClasses,
                  boolean includeLibraryClasses) {
    super();
    m_class = aClass;
    m_includeJavaClasses = includeJavaClasses;
    m_includeLibraryClasses = includeLibraryClasses;
  }

  @Override
  public void visitField(@Nonnull PsiField field) {
    super.visitField(field);
    PsiType type = field.getType();
    addDependency(type);
  }

  @Override
  public void visitLocalVariable(@Nonnull PsiLocalVariable var) {
    super.visitLocalVariable(var);
    PsiType type = var.getType();
    addDependency(type);
  }

  @Override
  public void visitMethod(@Nonnull PsiMethod method) {
    super.visitMethod(method);
    PsiType returnType = method.getReturnType();
    addDependency(returnType);
    addDependenciesForParameters(method);
    addDependenciesForThrowsList(method);
  }

  private void addDependenciesForThrowsList(PsiMethod method) {
    PsiReferenceList throwsList = method.getThrowsList();
    PsiClassType[] throwsTypes = throwsList.getReferencedTypes();
    for (PsiClassType throwsType : throwsTypes) {
      addDependency(throwsType);
    }
  }

  private void addDependenciesForParameters(PsiMethod method) {
    PsiParameterList parameterList = method.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      PsiType parameterType = parameter.getType();
      addDependency(parameterType);
    }
  }

  @Override
  public void visitNewExpression(@Nonnull PsiNewExpression exp) {
    super.visitNewExpression(exp);
    PsiType classType = exp.getType();
    addDependency(classType);
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression exp) {
    super.visitClassObjectAccessExpression(exp);
    PsiTypeElement operand = exp.getOperand();
    PsiType classType = operand.getType();
    addDependency(classType);
  }

  @Override
  public void visitClass(@Nonnull PsiClass aClass) {
    boolean wasInClass = m_inClass;
    if (!m_inClass) {

      m_inClass = true;
      super.visitClass(aClass);
    }
    m_inClass = wasInClass;
    PsiType[] superTypes = aClass.getSuperTypes();
    for (PsiType superType : superTypes) {
      addDependency(superType);
    }
  }

  @Override
  public void visitTryStatement(@Nonnull PsiTryStatement statement) {
    super.visitTryStatement(statement);
    PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
    for (PsiParameter catchBlockParameter : catchBlockParameters) {
      PsiType catchType = catchBlockParameter.getType();
      addDependency(catchType);
    }
  }

  @Override
  public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression exp) {
    super.visitInstanceOfExpression(exp);
    PsiTypeElement checkType = exp.getCheckType();
    if (checkType == null) {
      return;
    }
    PsiType classType = checkType.getType();
    addDependency(classType);
  }

  @Override
  public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression exp) {
    super.visitTypeCastExpression(exp);
    PsiTypeElement castType = exp.getCastType();
    if (castType == null) {
      return;
    }
    PsiType classType = castType.getType();
    addDependency(classType);
  }

  private void addDependency(PsiType type) {
    if (type == null) {
      return;
    }
    PsiType baseType = type.getDeepComponentType();

    if (ClassUtils.isPrimitive(type)) {
      return;
    }
    String qualifiedName = m_class.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    if (baseType.equalsToText(qualifiedName)) {
      return;
    }
    String baseTypeName = baseType.getCanonicalText();
    @NonNls String javaPrefix = "java.";
    @NonNls String javaxPrefix = "javax.";
    if (!m_includeJavaClasses &&
        (baseTypeName.startsWith(javaPrefix) ||
         baseTypeName.startsWith(javaxPrefix))) {
      return;
    }
    if (!m_includeLibraryClasses) {
      Project project = m_class.getProject();
      GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(baseTypeName, searchScope);
      if (aClass == null) {
        return;
      }
      if (LibraryUtil.classIsInLibrary(aClass)) {
        return;
      }
    }
    if (StringUtil.startsWithConcatenation(baseTypeName, qualifiedName, ".")) {
      return;
    }
    m_dependencies.add(baseTypeName);
  }

  public int getNumDependencies() {
    return m_dependencies.size();
  }
}
