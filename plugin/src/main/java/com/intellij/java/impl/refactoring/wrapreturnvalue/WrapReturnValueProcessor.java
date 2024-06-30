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
package com.intellij.java.impl.refactoring.wrapreturnvalue;

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import com.intellij.java.impl.refactoring.psi.TypeParametersVisitor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.ChangeReturnType;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.ReturnWrappedValue;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.UnwrapCall;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.WrapReturnValue;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class WrapReturnValueProcessor extends FixableUsagesRefactoringProcessor {

  private static final Logger LOG = Logger.getInstance("com.siyeh.rpp.wrapreturnvalue.WrapReturnValueProcessor");

  private MoveDestination myMoveDestination;
  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final boolean myCreateInnerClass;
  private final PsiField myDelegateField;
  private final String myQualifiedName;
  private final boolean myUseExistingClass;
  private final List<PsiTypeParameter> typeParams;
  @NonNls
  private final String unwrapMethodName;

  public WrapReturnValueProcessor(String className,
                                  String packageName,
                                  MoveDestination moveDestination, PsiMethod method,
                                  boolean useExistingClass,
                                  final boolean createInnerClass, PsiField delegateField) {
    super(method.getProject());
    myMoveDestination = moveDestination;
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    myCreateInnerClass = createInnerClass;
    myDelegateField = delegateField;
    myQualifiedName = StringUtil.getQualifiedName(packageName, className);
    this.myUseExistingClass = useExistingClass;

    final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
    final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    assert returnTypeElement != null;
    returnTypeElement.accept(visitor);
    typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);
    if (useExistingClass) {
      unwrapMethodName = calculateUnwrapMethodName();
    }
    else {
      unwrapMethodName = "getValue";
    }
  }

  private String calculateUnwrapMethodName() {
    final PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (existingClass != null) {
      if (TypeConversionUtil.isPrimitiveWrapper(myQualifiedName)) {
        final PsiPrimitiveType unboxedType =
          PsiPrimitiveType.getUnboxedType(JavaPsiFacade.getInstance(myProject).getElementFactory().createType(existingClass));
        assert unboxedType != null;
        return unboxedType.getCanonicalText() + "Value()";
      }

      final PsiMethod getter = PropertyUtil.findGetterForField(myDelegateField);
      return getter != null ? getter.getName() : "";
    }
    return "";
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new WrapReturnValueUsageViewDescriptor(method, usageInfos);
  }

  public void findUsages(@Nonnull List<FixableUsageInfo> usages) {
    findUsagesForMethod(method, usages);
    for (PsiMethod overridingMethod : OverridingMethodsSearch.search(method)) {
      findUsagesForMethod(overridingMethod, usages);
    }
  }

  private void findUsagesForMethod(PsiMethod psiMethod, List<FixableUsageInfo> usages) {
    for (PsiReference reference : ReferencesSearch.search(psiMethod, psiMethod.getUseScope())) {
      final PsiElement referenceElement = reference.getElement();
      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiCallExpression) {
        usages.add(new UnwrapCall((PsiCallExpression)parent, unwrapMethodName));
      }
    }
    final String returnType = calculateReturnTypeString();
    usages.add(new ChangeReturnType(psiMethod, returnType));
    psiMethod.accept(new ReturnSearchVisitor(usages, returnType, psiMethod));
  }

  private String calculateReturnTypeString() {
    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    final StringBuilder returnTypeBuffer = new StringBuilder(qualifiedName);
    if (!typeParams.isEmpty()) {
      returnTypeBuffer.append('<');
      returnTypeBuffer.append(StringUtil.join(typeParams, new Function<PsiTypeParameter, String>() {
        public String apply(final PsiTypeParameter typeParameter) {
          final String paramName = typeParameter.getName();
          LOG.assertTrue(paramName != null);
          return paramName;
        }
      }, ","));
      returnTypeBuffer.append('>');
    }
    return returnTypeBuffer.toString();
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("could.not.find.selected.wrapping.class"));
      }
      else {
        boolean foundConstructor = false;
        final Set<PsiType> returnTypes = new HashSet<PsiType>();
        returnTypes.add(method.getReturnType());
        final PsiCodeBlock methodBody = method.getBody();
        if (methodBody != null) {
          methodBody.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(final PsiReturnStatement statement) {
              super.visitReturnStatement(statement);
              if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != method) return;
              final PsiExpression returnValue = statement.getReturnValue();
              if (returnValue != null) {
                returnTypes.add(returnValue.getType());
              }
            }
          });
        }

        final PsiMethod[] constructors = existingClass.getConstructors();
        constr: for (PsiMethod constructor : constructors) {
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiParameter parameter = parameters[0];
            final PsiType parameterType = parameter.getType();
            for (PsiType returnType : returnTypes) {
              if (!TypeConversionUtil.isAssignable(parameterType, returnType)) {
                continue constr;
              }
            }
            final PsiCodeBlock body = constructor.getBody();
            LOG.assertTrue(body != null);
            final boolean[] found = new boolean[1];
            body.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                final PsiExpression lExpression = expression.getLExpression();
                if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)lExpression).resolve() == myDelegateField) {
                  final PsiExpression rExpression = expression.getRExpression();
                  if (rExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)rExpression).resolve() == parameter) {
                    found[0] = true;
                  }
                }
              }
            });
            if (found[0]) {
              foundConstructor = true;
              break;
            }
          }
        }
        if (!foundConstructor) {
          conflicts.putValue(existingClass, "Existing class does not have appropriate constructor");
        }
      }
      if (unwrapMethodName.length() == 0) {
        conflicts.putValue(existingClass,
                      "Existing class does not have getter for selected field");
      }
    }
    else {
      if (existingClass != null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("there.already.exists.a.class.with.the.selected.name"));
      }
      if (myMoveDestination != null && !myMoveDestination.isTargetAccessible(myProject, method.getContainingFile().getVirtualFile())) {
        conflicts.putValue(method, "Created class won't be accessible in the call place");
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    if (!myUseExistingClass && !buildClass()) return;
    super.performRefactoring(usageInfos);
  }

  private boolean buildClass() {
    final PsiManager manager = method.getManager();
    final Project project = method.getProject();
    final ReturnValueBeanBuilder beanClassBuilder = new ReturnValueBeanBuilder();
    beanClassBuilder.setCodeStyleSettings(project);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    beanClassBuilder.setStatic(myCreateInnerClass && method.hasModifierProperty(PsiModifier.STATIC));
    final PsiType returnType = method.getReturnType();
    beanClassBuilder.setValueType(returnType);

    final String classString;
    try {
      classString = beanClassBuilder.buildBeanClass();
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }

    try {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final PsiJavaFile psiFile = (PsiJavaFile)factory.createFileFromText(className + ".java", JavaFileType.INSTANCE, classString);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
      if (myCreateInnerClass) {
        final PsiClass containingClass = method.getContainingClass();
        final PsiElement innerClass = containingClass.add(psiFile.getClasses()[0]);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(innerClass);
      } else {
        final PsiFile containingFile = method.getContainingFile();

        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        final PsiDirectory directory;
        if (myMoveDestination != null) {
          directory = myMoveDestination.getTargetDirectory(containingDirectory);
        } else {
          final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
          directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true, true);
        }

        if (directory != null) {
          final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiFile);
          final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
          directory.add(reformattedFile);
        } else {
          return false;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }

  protected String getCommandName() {
    final PsiClass containingClass = method.getContainingClass();
    return RefactorJBundle.message("wrapped.return.command.name", className, containingClass.getName(), '.', method.getName());
  }


  private class ReturnSearchVisitor extends JavaRecursiveElementWalkingVisitor {
    private final List<FixableUsageInfo> usages;
    private final String type;
    private final PsiMethod myMethod;

    ReturnSearchVisitor(List<FixableUsageInfo> usages, String type, final PsiMethod psiMethod) {
      super();
      this.usages = usages;
      this.type = type;
      myMethod = psiMethod;
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
    }

    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);

      if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != myMethod) return;

      final PsiExpression returnValue = statement.getReturnValue();
      if (myUseExistingClass && returnValue instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)returnValue;
        if (callExpression.getArgumentList().getExpressions().length == 0) {
          final PsiReferenceExpression callMethodExpression = callExpression.getMethodExpression();
          final String methodName = callMethodExpression.getReferenceName();
          if (Comparing.strEqual(unwrapMethodName, methodName)) {
            final PsiExpression qualifier = callMethodExpression.getQualifierExpression();
            if (qualifier != null) {
              final PsiType qualifierType = qualifier.getType();
              if (qualifierType != null && qualifierType.getCanonicalText().equals(myQualifiedName)) {
                usages.add(new ReturnWrappedValue(statement));
                return;
              }
            }
          }
        }
      }
      usages.add(new WrapReturnValue(statement, type));
    }
  }
}
