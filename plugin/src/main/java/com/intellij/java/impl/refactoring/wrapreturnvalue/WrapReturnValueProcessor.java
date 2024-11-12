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

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.psi.TypeParametersVisitor;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.ChangeReturnType;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.ReturnWrappedValue;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.UnwrapCall;
import com.intellij.java.impl.refactoring.wrapreturnvalue.usageInfo.WrapReturnValue;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final String unwrapMethodName;

    public WrapReturnValueProcessor(
        String className,
        String packageName,
        MoveDestination moveDestination, PsiMethod method,
        boolean useExistingClass,
        final boolean createInnerClass, PsiField delegateField
    ) {
        super(method.getProject());
        myMoveDestination = moveDestination;
        this.method = method;
        this.className = className;
        this.packageName = packageName;
        myCreateInnerClass = createInnerClass;
        myDelegateField = delegateField;
        myQualifiedName = StringUtil.getQualifiedName(packageName, className);
        this.myUseExistingClass = useExistingClass;

        final Set<PsiTypeParameter> typeParamSet = new HashSet<>();
        final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        assert returnTypeElement != null;
        returnTypeElement.accept(visitor);
        typeParams = new ArrayList<>(typeParamSet);
        if (useExistingClass) {
            unwrapMethodName = calculateUnwrapMethodName();
        }
        else {
            unwrapMethodName = "getValue";
        }
    }

    private String calculateUnwrapMethodName() {
        final PsiClass existingClass =
            JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
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
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usageInfos) {
        return new WrapReturnValueUsageViewDescriptor(method, usageInfos);
    }

    @Override
    @RequiredReadAction
    public void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        findUsagesForMethod(method, usages);
        for (PsiMethod overridingMethod : OverridingMethodsSearch.search(method)) {
            findUsagesForMethod(overridingMethod, usages);
        }
    }

    @RequiredReadAction
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
            returnTypeBuffer.append(StringUtil.join(
                typeParams,
                typeParameter -> {
                    String paramName = typeParameter.getName();
                    LOG.assertTrue(paramName != null);
                    return paramName;
                },
                ","
            ));
            returnTypeBuffer.append('>');
        }
        return returnTypeBuffer.toString();
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull final Ref<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        final PsiClass existingClass =
            JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
        if (myUseExistingClass) {
            if (existingClass == null) {
                conflicts.putValue(existingClass, JavaRefactoringLocalize.couldNotFindSelectedWrappingClass().get());
            }
            else {
                boolean foundConstructor = false;
                final Set<PsiType> returnTypes = new HashSet<>();
                returnTypes.add(method.getReturnType());
                final PsiCodeBlock methodBody = method.getBody();
                if (methodBody != null) {
                    methodBody.accept(new JavaRecursiveElementWalkingVisitor() {
                        @Override
                        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                            super.visitReturnStatement(statement);
                            if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != method) {
                                return;
                            }
                            PsiExpression returnValue = statement.getReturnValue();
                            if (returnValue != null) {
                                returnTypes.add(returnValue.getType());
                            }
                        }
                    });
                }

                final PsiMethod[] constructors = existingClass.getConstructors();
                constr:
                for (PsiMethod constructor : constructors) {
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
                            @RequiredReadAction
                            public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
                                super.visitAssignmentExpression(expression);
                                if (expression.getLExpression() instanceof PsiReferenceExpression lRefExpr
                                    && lRefExpr.resolve() == myDelegateField
                                    && expression.getRExpression() instanceof PsiReferenceExpression rRefExpr
                                    && rRefExpr.resolve() == parameter) {
                                    found[0] = true;
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
                    conflicts.putValue(
                        existingClass,
                        LocalizeValue.localizeTODO("Existing class does not have appropriate constructor").get()
                    );
                }
            }
            if (unwrapMethodName.length() == 0) {
                conflicts.putValue(
                    existingClass,
                    LocalizeValue.localizeTODO("Existing class does not have getter for selected field").get()
                );
            }
        }
        else {
            if (existingClass != null) {
                conflicts.putValue(existingClass, JavaRefactoringLocalize.thereAlreadyExistsAClassWithTheSelectedName().get());
            }
            if (myMoveDestination != null && !myMoveDestination.isTargetAccessible(
                myProject,
                method.getContainingFile().getVirtualFile()
            )) {
                conflicts.putValue(method, "Created class won't be accessible in the call place");
            }
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @Override
    @RequiredReadAction
    protected void performRefactoring(UsageInfo[] usageInfos) {
        if (!myUseExistingClass && !buildClass()) {
            return;
        }
        super.performRefactoring(usageInfos);
    }

    @RequiredReadAction
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
            }
            else {
                final PsiFile containingFile = method.getContainingFile();

                final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
                final PsiDirectory directory;
                if (myMoveDestination != null) {
                    directory = myMoveDestination.getTargetDirectory(containingDirectory);
                }
                else {
                    final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
                    directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true, true);
                }

                if (directory != null) {
                    final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiFile);
                    final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
                    directory.add(reformattedFile);
                }
                else {
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

    @Nonnull
    @Override
    @RequiredReadAction
    protected String getCommandName() {
        final PsiClass containingClass = method.getContainingClass();
        return JavaRefactoringLocalize.wrappedReturnCommandName(className, containingClass.getName(), '.', method.getName()).get();
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
        public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
        }

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);

            if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != myMethod) {
                return;
            }

            final PsiExpression returnValue = statement.getReturnValue();
            if (myUseExistingClass && returnValue instanceof PsiMethodCallExpression call) {
                if (call.getArgumentList().getExpressions().length == 0) {
                    final PsiReferenceExpression callMethodExpression = call.getMethodExpression();
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
