/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class InlineToAnonymousClassHandler extends JavaInlineActionHandler {
    static final ElementPattern OUR_CATCH_CLAUSE_PATTERN = PlatformPatterns.psiElement(PsiTypeElement.class).withParent(
        PlatformPatterns.psiElement(PsiParameter.class).withParent(PlatformPatterns.psiElement(PsiCatchSection.class))
    );
    static final ElementPattern OUR_THROWS_CLAUSE_PATTERN = PlatformPatterns.psiElement().withParent(
        PlatformPatterns.psiElement(PsiReferenceList.class).withFirstChild(PlatformPatterns.psiElement().withText(PsiKeyword.THROWS))
    );

    @Override
    public boolean isEnabledOnElement(PsiElement element) {
        return element instanceof PsiMethod || element instanceof PsiClass;
    }

    @Override
    @RequiredReadAction
    public boolean canInlineElement(PsiElement element) {
        if (element.getLanguage() != JavaLanguage.INSTANCE) {
            return false;
        }
        if (element instanceof PsiMethod method && method.isConstructor() && !InlineMethodHandler.isChainingConstructor(method)) {
            PsiClass containingClass = method.getContainingClass();
            return containingClass != null && findClassInheritors(containingClass);
        }
        return element instanceof PsiClass psiClass && !(element instanceof PsiAnonymousClass) && findClassInheritors(psiClass);
    }

    private static boolean findClassInheritors(PsiClass element) {
        Collection<PsiElement> inheritors = new ArrayList<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> element.getApplication().runReadAction(() -> {
                PsiClass inheritor = ClassInheritorsSearch.search(element).findFirst();
                if (inheritor != null) {
                    inheritors.add(inheritor);
                }
                else {
                    PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(element).findFirst();
                    if (functionalExpression != null) {
                        inheritors.add(functionalExpression);
                    }
                }
            }),
            JavaRefactoringLocalize.inlineAnonymousConflictProgress(element.getQualifiedName()),
            true,
            element.getProject()
        )) {
            return false;
        }
        return inheritors.isEmpty();
    }

    @Override
    @RequiredReadAction
    public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
        if (canInlineElement(element)) {
            PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
            if (!InlineMethodHandler.isThisReference(reference)) {
                if (element instanceof PsiMethod method && reference != null) {
                    PsiElement referenceElement = reference.getElement();
                    return referenceElement != null && !PsiTreeUtil.isAncestor(method.getContainingClass(), referenceElement, false);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void inlineElement(Project project, Editor editor, PsiElement psiElement) {
        PsiClass psiClass = psiElement instanceof PsiMethod method ? method.getContainingClass() : (PsiClass) psiElement;
        PsiCall callToInline = findCallToInline(editor);

        PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(psiClass);
        LocalizeValue title = RefactoringLocalize.inlineToAnonymousRefactoring();
        if (superType == null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                JavaLocalize.classNotFoundErrorMessage(CommonClassNames.JAVA_LANG_OBJECT),
                title,
                null
            );
            return;
        }

        SimpleReference<LocalizeValue> errorMessage = new SimpleReference<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> project.getApplication().runReadAction(() -> errorMessage.set(getCannotInlineMessage(psiClass))),
            JavaRefactoringLocalize.inlineConflictsProgress(),
            true,
            project
        )) {
            return;
        }
        if (errorMessage.get().isNotEmpty()) {
            CommonRefactoringUtil.showErrorHint(project, editor, errorMessage.get(), title, null);
            return;
        }

        new InlineToAnonymousClassDialog(project, psiClass, callToInline, canBeInvokedOnReference(callToInline, superType)).show();
    }

    public static boolean canBeInvokedOnReference(PsiCall callToInline, PsiType superType) {
        if (callToInline == null) {
            return false;
        }
        PsiElement parent = callToInline.getParent();
        if (parent instanceof PsiExpressionStatement || parent instanceof PsiSynchronizedStatement || parent instanceof PsiReferenceExpression) {
            return true;
        }
        else if (parent instanceof PsiExpressionList) {
            PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(parent, PsiMethodCallExpression.class);
            if (methodCallExpression != null) {
                int paramIdx = ArrayUtil.find(methodCallExpression.getArgumentList().getExpressions(), callToInline);
                if (paramIdx != -1) {
                    JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
                    PsiElement resolvedMethod = resolveResult.getElement();
                    if (resolvedMethod instanceof PsiMethod method) {
                        PsiType paramType;
                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        if (paramIdx >= parameters.length) {
                            PsiParameter varargParameter = parameters[parameters.length - 1];
                            paramType = varargParameter.getType();
                        }
                        else {
                            paramType = parameters[paramIdx].getType();
                        }
                        if (paramType instanceof PsiEllipsisType ellipsisType) {
                            paramType = ellipsisType.getComponentType();
                        }
                        paramType = resolveResult.getSubstitutor().substitute(paramType);

                        PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) callToInline).getClassOrAnonymousClassReference();
                        if (classReference != null) {
                            superType = classReference.advancedResolve(false).getSubstitutor().substitute(superType);
                            if (TypeConversionUtil.isAssignable(paramType, superType)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    public static PsiCall findCallToInline(Editor editor) {
        PsiCall callToInline = null;
        PsiReference reference = editor != null ? TargetElementUtil.findReference(editor) : null;
        if (reference != null && reference.getElement() instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            callToInline = RefactoringUtil.getEnclosingConstructorCall(javaCodeRef);
        }
        return callToInline;
    }

    @Nonnull
    @RequiredReadAction
    public static LocalizeValue getCannotInlineMessage(PsiClass psiClass) {
        if (psiClass instanceof PsiTypeParameter) {
            return JavaRefactoringLocalize.typeParametersCannotBeInlined();
        }
        if (psiClass.isAnnotationType()) {
            return JavaRefactoringLocalize.annotationTypesCannotBeInlined();
        }
        if (psiClass.isInterface()) {
            return JavaRefactoringLocalize.interfacesCannotBeInlined();
        }
        if (psiClass.isEnum()) {
            return JavaRefactoringLocalize.enumsCannotBeInlined();
        }
        if (psiClass.isAbstract()) {
            return RefactoringLocalize.inlineToAnonymousNoAbstract();
        }
        if (!psiClass.getManager().isInProject(psiClass)) {
            return JavaRefactoringLocalize.libraryClassesCannotBeInlined();
        }

        PsiClassType[] classTypes = psiClass.getExtendsListTypes();
        for (PsiClassType classType : classTypes) {
            PsiClass superClass = classType.resolve();
            if (superClass == null) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItsSuperclassCannotBeResolved();
            }
        }

        PsiClassType[] interfaces = psiClass.getImplementsListTypes();
        if (interfaces.length > 1) {
            return RefactoringLocalize.inlineToAnonymousNoMultipleInterfaces();
        }
        if (interfaces.length == 1) {
            if (interfaces[0].resolve() == null) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseAnInterfaceImplementedByItCannotBeResolved();
            }
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
                PsiClassType interfaceType = interfaces[0];
                if (!isRedundantImplements(superClass, interfaceType)) {
                    return RefactoringLocalize.inlineToAnonymousNoSuperclassAndInterface();
                }
            }
        }

        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            if (method.isConstructor()) {
                if (PsiUtil.findReturnStatements(method).length > 0) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseItsConstructorContainsReturnStatements();
                }
            }
            else if (method.findSuperMethods().length == 0) {
                if (!ReferencesSearch.search(method).forEach(new AllowedUsagesProcessor(psiClass))) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseThereAreUsagesOfItsMethodsNotInheritedFromItsSuperclassOrInterface();
                }
            }
            if (method.isStatic()) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasStaticMethods();
            }
        }

        PsiClass[] innerClasses = psiClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
            if (innerClass.isStatic()) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasStaticInnerClasses();
            }
            if (!ReferencesSearch.search(innerClass).forEach(new AllowedUsagesProcessor(psiClass))) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasUsagesOfItsInnerClasses();
            }
        }

        for (PsiField field : psiClass.getFields()) {
            if (field.isStatic()) {
                if (!field.isFinal()) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasStaticNonFinalFields();
                }
                Object initValue = null;
                PsiExpression initializer = field.getInitializer();
                if (initializer != null) {
                    initValue = JavaPsiFacade.getInstance(psiClass.getProject())
                        .getConstantEvaluationHelper()
                        .computeConstantExpression(initializer);
                }
                if (initValue == null) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasStaticFieldsWithNonConstantInitializers();
                }
            }
            if (!ReferencesSearch.search(field).forEach(new AllowedUsagesProcessor(psiClass))) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasUsagesOfFieldsNotInheritedFromItsSuperclass();
            }
        }

        PsiClassInitializer[] initializers = psiClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
            if (initializer.isStatic()) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasStaticInitializers();
            }
        }

        return getCannotInlineDueToUsagesMessage(psiClass);
    }

    static boolean isRedundantImplements(PsiClass superClass, PsiClassType interfaceType) {
        boolean redundantImplements = false;
        PsiClassType[] superClassInterfaces = superClass.getImplementsListTypes();
        for (PsiClassType superClassInterface : superClassInterfaces) {
            if (superClassInterface.equals(interfaceType)) {
                redundantImplements = true;
                break;
            }
        }
        return redundantImplements;
    }

    @Nonnull
    @RequiredReadAction
    private static LocalizeValue getCannotInlineDueToUsagesMessage(PsiClass aClass) {
        boolean hasUsages = false;
        for (PsiReference reference : ReferencesSearch.search(aClass)) {
            PsiElement element = reference.getElement();
            if (element == null) {
                continue;
            }
            if (!PsiTreeUtil.isAncestor(aClass, element, false)) {
                hasUsages = true;
            }
            PsiElement parentElement = element.getParent();
            if (parentElement != null) {
                if (parentElement.getParent() instanceof PsiClassObjectAccessExpression) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseItHasUsagesOfItsClassLiteral();
                }
                if (OUR_CATCH_CLAUSE_PATTERN.accepts(parentElement)) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseItIsUsedInACatchClause();
                }
            }
            if (OUR_THROWS_CLAUSE_PATTERN.accepts(element)) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItIsUsedInAThrowsClause();
            }
            if (parentElement instanceof PsiThisExpression) {
                return JavaRefactoringLocalize.classCannotBeInlinedBecauseItIsUsedAsAThisQualifier();
            }
            if (parentElement instanceof PsiNewExpression newExpression) {
                PsiMethod[] constructors = aClass.getConstructors();
                if (constructors.length == 0) {
                    PsiExpressionList newArgumentList = newExpression.getArgumentList();
                    if (newArgumentList != null && newArgumentList.getExpressions().length > 0) {
                        return JavaRefactoringLocalize.classCannotBeInlinedBecauseACallToItsConstructorIsUnresolved();
                    }
                }
                else if (!newExpression.resolveMethodGenerics().isValidResult()) {
                    return JavaRefactoringLocalize.classCannotBeInlinedBecauseACallToItsConstructorIsUnresolved();
                }
            }
        }
        if (!hasUsages) {
            return RefactoringLocalize.classIsNeverUsed();
        }
        return LocalizeValue.empty();
    }

    private static class AllowedUsagesProcessor implements Predicate<PsiReference> {
        private final PsiElement myPsiElement;

        public AllowedUsagesProcessor(PsiElement psiElement) {
            myPsiElement = psiElement;
        }

        @Override
        @RequiredReadAction
        public boolean test(PsiReference psiReference) {
            if (PsiTreeUtil.isAncestor(myPsiElement, psiReference.getElement(), false)) {
                return true;
            }
            PsiElement element = psiReference.getElement();
            if (element instanceof PsiReferenceExpression referenceExpression) {
                PsiExpression qualifier = referenceExpression.getQualifierExpression();
                while (qualifier instanceof PsiParenthesizedExpression parenthesizedExpression) {
                    qualifier = parenthesizedExpression.getExpression();
                }
                if (qualifier instanceof PsiNewExpression newExpr) {
                    PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
                    if (classRef != null && myPsiElement.equals(classRef.resolve())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}