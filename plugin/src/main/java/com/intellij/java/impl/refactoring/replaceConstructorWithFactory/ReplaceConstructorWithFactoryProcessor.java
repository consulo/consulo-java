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
package com.intellij.java.impl.refactoring.replaceConstructorWithFactory;

import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(ReplaceConstructorWithFactoryProcessor.class);
    private final PsiMethod myConstructor;
    private final String myFactoryName;
    private final PsiElementFactory myFactory;
    private final PsiClass myOriginalClass;
    private final PsiClass myTargetClass;
    private final PsiManager myManager;
    private final boolean myIsInner;

    public ReplaceConstructorWithFactoryProcessor(
        Project project,
        PsiMethod originalConstructor,
        PsiClass originalClass,
        PsiClass targetClass,
        @NonNls String factoryName
    ) {
        super(project);
        myOriginalClass = originalClass;
        myConstructor = originalConstructor;
        myTargetClass = targetClass;
        myFactoryName = factoryName;
        myManager = PsiManager.getInstance(project);
        myFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();

        myIsInner = isInner(myOriginalClass);
    }

    private boolean isInner(PsiClass originalClass) {
        final boolean result = PsiUtil.isInnerClass(originalClass);
        if (result) {
            LOG.assertTrue(PsiTreeUtil.isAncestor(myTargetClass, originalClass, false));
        }
        return result;
    }

    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        if (myConstructor != null) {
            return new ReplaceConstructorWithFactoryViewDescriptor(myConstructor);
        }
        else {
            return new ReplaceConstructorWithFactoryViewDescriptor(myOriginalClass);
        }
    }

    private List<PsiElement> myNonNewConstructorUsages;

    @Nonnull
    protected UsageInfo[] findUsages() {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);

        ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
        myNonNewConstructorUsages = new ArrayList<PsiElement>();

        for (PsiReference reference : ReferencesSearch.search(
            myConstructor == null ? myOriginalClass : myConstructor,
            projectScope,
            false
        )) {
            PsiElement element = reference.getElement();

            if (element.getParent() instanceof PsiNewExpression) {
                usages.add(new UsageInfo(element.getParent()));
            }
            else if ("super".equals(element.getText()) || "this".equals(element.getText())) {
                myNonNewConstructorUsages.add(element);
            }
            else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
                myNonNewConstructorUsages.add(element);
            }
            else if (element instanceof PsiClass) {
                myNonNewConstructorUsages.add(element);
            }
        }

        //if (myConstructor != null && myConstructor.getParameterList().getParametersCount() == 0) {
        //    RefactoringUtil.visitImplicitConstructorUsages(getConstructorContainingClass(), new RefactoringUtil.ImplicitConstructorUsageVisitor() {
        //        @Override public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
        //            myNonNewConstructorUsages.add(constructor);
        //        }
        //
        //        @Override public void visitClassWithoutConstructors(PsiClass aClass) {
        //            myNonNewConstructorUsages.add(aClass);
        //        }
        //    });
        //}

        return usages.toArray(new UsageInfo[usages.size()]);
    }

    protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
        UsageInfo[] usages = refUsages.get();

        MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
        final PsiResolveHelper helper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
        final PsiClass constructorContainingClass = getConstructorContainingClass();
        if (!helper.isAccessible(constructorContainingClass, myTargetClass, null)) {
            LocalizeValue message = RefactoringLocalize.class0IsNotAccessibleFromTarget1(
                RefactoringUIUtil.getDescription(constructorContainingClass, true),
                RefactoringUIUtil.getDescription(myTargetClass, true)
            );
            conflicts.putValue(constructorContainingClass, message.get());
        }

        HashSet<PsiElement> reportedContainers = new HashSet<PsiElement>();
        final String targetClassDescription = RefactoringUIUtil.getDescription(myTargetClass, true);
        for (UsageInfo usage : usages) {
            final PsiElement container = ConflictsUtil.getContainer(usage.getElement());
            if (!reportedContainers.contains(container)) {
                reportedContainers.add(container);
                if (!helper.isAccessible(myTargetClass, usage.getElement(), null)) {
                    LocalizeValue message = RefactoringLocalize.target0IsNotAccessibleFrom1(
                        targetClassDescription,
                        RefactoringUIUtil.getDescription(container, true)
                    );
                    conflicts.putValue(myTargetClass, message.get());
                }
            }
        }

        if (myIsInner) {
            for (UsageInfo usage : usages) {
                final PsiField field = PsiTreeUtil.getParentOfType(usage.getElement(), PsiField.class);
                if (field != null) {
                    final PsiClass containingClass = field.getContainingClass();

                    if (PsiTreeUtil.isAncestor(containingClass, myTargetClass, true)) {
                        LocalizeValue message = RefactoringLocalize.constructorBeingRefactoredIsUsedInInitializerOf0(
                            RefactoringUIUtil.getDescription(field, true),
                            RefactoringUIUtil.getDescription(constructorContainingClass, false)
                        );
                        conflicts.putValue(field, message.get());
                    }
                }
            }
        }


        return showConflicts(conflicts, usages);
    }

    private PsiClass getConstructorContainingClass() {
        if (myConstructor != null) {
            return myConstructor.getContainingClass();
        }
        else {
            return myOriginalClass;
        }
    }

    protected void performRefactoring(UsageInfo[] usages) {

        try {
            PsiReferenceExpression classReferenceExpression =
                myFactory.createReferenceExpression(myTargetClass);
            PsiReferenceExpression qualifiedMethodReference =
                (PsiReferenceExpression)myFactory.createExpressionFromText("A." + myFactoryName, null);
            PsiMethod factoryMethod = (PsiMethod)myTargetClass.add(createFactoryMethod());
            if (myConstructor != null) {
                PsiUtil.setModifierProperty(myConstructor, PsiModifier.PRIVATE, true);
                VisibilityUtil.escalateVisibility(myConstructor, factoryMethod);
                for (PsiElement place : myNonNewConstructorUsages) {
                    VisibilityUtil.escalateVisibility(myConstructor, place);
                }
            }

            if (myConstructor == null) {
                PsiMethod constructor = myFactory.createConstructor();
                PsiUtil.setModifierProperty(constructor, PsiModifier.PRIVATE, true);
                constructor = (PsiMethod)getConstructorContainingClass().add(constructor);
                VisibilityUtil.escalateVisibility(constructor, myTargetClass);
            }

            for (UsageInfo usage : usages) {
                PsiNewExpression newExpression = (PsiNewExpression)usage.getElement();
                if (newExpression == null) {
                    continue;
                }

                VisibilityUtil.escalateVisibility(factoryMethod, newExpression);
                PsiMethodCallExpression factoryCall =
                    (PsiMethodCallExpression)myFactory.createExpressionFromText(myFactoryName + "()", newExpression);
                factoryCall.getArgumentList().replace(newExpression.getArgumentList());

                boolean replaceMethodQualifier = false;
                PsiExpression newQualifier = newExpression.getQualifier();

                PsiElement resolvedFactoryMethod = factoryCall.getMethodExpression().resolve();
                if (resolvedFactoryMethod != factoryMethod || newQualifier != null) {
                    factoryCall.getMethodExpression().replace(qualifiedMethodReference);
                    replaceMethodQualifier = true;
                }

                if (replaceMethodQualifier) {
                    if (newQualifier == null) {
                        factoryCall.getMethodExpression().getQualifierExpression().replace(classReferenceExpression);
                    }
                    else {
                        factoryCall.getMethodExpression().getQualifierExpression().replace(newQualifier);
                    }
                }

                newExpression.replace(factoryCall);
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    private PsiMethod createFactoryMethod() throws IncorrectOperationException {
        final PsiClass containingClass = getConstructorContainingClass();
        PsiClassType type = myFactory.createType(containingClass, PsiSubstitutor.EMPTY);
        final PsiMethod factoryMethod = myFactory.createMethod(myFactoryName, type);
        if (myConstructor != null) {
            factoryMethod.getParameterList().replace(myConstructor.getParameterList());
            factoryMethod.getThrowsList().replace(myConstructor.getThrowsList());
        }

        Collection<String> names = new HashSet<String>();
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(myConstructor != null ? myConstructor : containingClass)) {
            if (!names.contains(typeParameter.getName())) { //Otherwise type parameter is hidden in the constructor
                names.add(typeParameter.getName());
                factoryMethod.getTypeParameterList().addAfter(typeParameter, null);
            }
        }

        PsiReturnStatement returnStatement =
            (PsiReturnStatement)myFactory.createStatementFromText("return new A();", null);
        PsiNewExpression newExpression = (PsiNewExpression)returnStatement.getReturnValue();
        PsiJavaCodeReferenceElement classRef = myFactory.createReferenceElementByType(type);
        newExpression.getClassReference().replace(classRef);
        final PsiExpressionList argumentList = newExpression.getArgumentList();

        PsiParameter[] params = factoryMethod.getParameterList().getParameters();

        for (PsiParameter parameter : params) {
            PsiExpression paramRef = myFactory.createExpressionFromText(parameter.getName(), null);
            argumentList.add(paramRef);
        }
        factoryMethod.getBody().add(returnStatement);

        PsiUtil.setModifierProperty(factoryMethod, getDefaultFactoryVisibility(), true);

        if (!myIsInner) {
            PsiUtil.setModifierProperty(factoryMethod, PsiModifier.STATIC, true);
        }

        return (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(factoryMethod);
    }

    @PsiModifier.ModifierConstant
    private String getDefaultFactoryVisibility() {
        final PsiModifierList modifierList;
        if (myConstructor != null) {
            modifierList = myConstructor.getModifierList();
        }
        else {
            modifierList = myOriginalClass.getModifierList();
        }
        return VisibilityUtil.getVisibilityModifier(modifierList);
    }


    protected String getCommandName() {
        if (myConstructor != null) {
            return RefactoringLocalize.replaceConstructor0WithAFactoryMethod(DescriptiveNameUtil.getDescriptiveName(myConstructor)).get();
        }
        else {
            return RefactoringLocalize.replaceDefaultConstructorOf0WithAFactoryMethod(
                DescriptiveNameUtil.getDescriptiveName(myOriginalClass)
            ).get();
        }
    }

    public PsiClass getOriginalClass() {
        return getConstructorContainingClass();
    }

    public PsiClass getTargetClass() {
        return myTargetClass;
    }

    public PsiMethod getConstructor() {
        return myConstructor;
    }

    public String getFactoryName() {
        return myFactoryName;
    }
}
