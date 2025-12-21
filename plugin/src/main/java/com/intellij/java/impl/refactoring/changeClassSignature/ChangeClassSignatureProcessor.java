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
package com.intellij.java.impl.refactoring.changeClassSignature;

import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author dsl
 */
public class ChangeClassSignatureProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(ChangeClassSignatureProcessor.class);
    private PsiClass myClass;
    private final TypeParameterInfo[] myNewSignature;

    public ChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature) {
        super(project);
        myClass = aClass;
        myNewSignature = newSignature;
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 1);
        LOG.assertTrue(elements[0] instanceof PsiClass);
        myClass = (PsiClass)elements[0];
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return ChangeClassSignatureDialog.REFACTORING_NAME;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new ChangeClassSigntaureViewDescriptor(myClass);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();

        PsiTypeParameter[] parameters = myClass.getTypeParameters();
        Map<String, TypeParameterInfo> infos = new HashMap<>();
        for (TypeParameterInfo info : myNewSignature) {
            String newName = info.isForExistingParameter() ? parameters[info.getOldParameterIndex()].getName() : info.getNewName();
            TypeParameterInfo existing = infos.get(newName);
            if (existing != null) {
                conflicts.putValue(
                    myClass,
                    LocalizeValue.localizeTODO(
                        RefactoringUIUtil.getDescription(myClass, false) + " already contains type parameter " + newName
                    )
                );
            }
            infos.put(newName, info);
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
        List<UsageInfo> result = new ArrayList<>();

        boolean hadTypeParameters = myClass.hasTypeParameters();
        for (PsiReference reference : ReferencesSearch.search(myClass, projectScope, false)) {
            if (reference.getElement() instanceof PsiJavaCodeReferenceElement referenceElement) {
                PsiElement parent = referenceElement.getParent();
                if (parent instanceof PsiTypeElement typeElem && typeElem.getParent() instanceof PsiInstanceOfExpression) {
                    continue;
                }
                if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression
                    || parent instanceof PsiAnonymousClass || parent instanceof PsiReferenceList) {
                    if (!hadTypeParameters || referenceElement.getTypeParameters().length > 0) {
                        result.add(new UsageInfo(referenceElement));
                    }
                }
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
        try {
            doRefactoring(usages);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        finally {
            a.finish();
        }
    }

    @RequiredWriteAction
    private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
        PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
        boolean[] toRemoveParams = detectRemovedParameters(typeParameters);

        for (UsageInfo usage : usages) {
            LOG.assertTrue(usage.getElement() instanceof PsiJavaCodeReferenceElement);
            processUsage(usage, typeParameters, toRemoveParams);
        }
        Map<PsiTypeElement, PsiClass> supersMap = new HashMap<>();
        myClass.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitTypeElement(@Nonnull PsiTypeElement typeElement) {
                super.visitTypeElement(typeElement);
                if (PsiUtil.resolveClassInType(typeElement.getType()) instanceof PsiTypeParameter typeParam) {
                    int i = ArrayUtil.find(typeParameters, typeParam);
                    if (i >= 0 && i < toRemoveParams.length && toRemoveParams[i]) {
                        supersMap.put(typeElement, typeParam.getSuperClass());
                    }
                }
            }
        });
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        for (Map.Entry<PsiTypeElement, PsiClass> classEntry : supersMap.entrySet()) {
            classEntry.getKey().replace(elementFactory.createTypeElement(elementFactory.createType(classEntry.getValue())));
        }
        changeClassSignature(typeParameters, toRemoveParams);
    }

    @RequiredWriteAction
    private void changeClassSignature(PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParams)
        throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        List<PsiTypeParameter> newTypeParameters = new ArrayList<>();
        for (TypeParameterInfo info : myNewSignature) {
            int oldIndex = info.getOldParameterIndex();
            if (oldIndex >= 0) {
                newTypeParameters.add(originalTypeParameters[oldIndex]);
            }
            else {
                newTypeParameters.add(factory.createTypeParameterFromText(info.getNewName(), null));
            }
        }
        ChangeSignatureUtil.synchronizeList(myClass.getTypeParameterList(), newTypeParameters, TypeParameterList.INSTANCE, toRemoveParams);
    }

    private boolean[] detectRemovedParameters(PsiTypeParameter[] originalTypeParams) {
        boolean[] toRemoveParams = new boolean[originalTypeParams.length];
        Arrays.fill(toRemoveParams, true);
        for (TypeParameterInfo info : myNewSignature) {
            int oldParameterIndex = info.getOldParameterIndex();
            if (oldParameterIndex >= 0) {
                toRemoveParams[oldParameterIndex] = false;
            }
        }
        return toRemoveParams;
    }

    @RequiredWriteAction
    private void processUsage(UsageInfo usage, PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParams)
        throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
        PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

        PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
        PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
        if (oldValues.length != originalTypeParameters.length) {
            return;
        }
        List<PsiTypeElement> newValues = new ArrayList<>();
        for (TypeParameterInfo info : myNewSignature) {
            int oldIndex = info.getOldParameterIndex();
            if (oldIndex >= 0) {
                newValues.add(oldValues[oldIndex]);
            }
            else {
                PsiType type = info.getDefaultValue().getType(myClass.getLBrace(), PsiManager.getInstance(myProject));

                PsiTypeElement newValue = factory.createTypeElement(usageSubstitutor.substitute(type));
                newValues.add(newValue);
            }
        }
        ChangeSignatureUtil.synchronizeList(referenceParameterList, newValues, ReferenceParameterList.INSTANCE, toRemoveParams);
    }

    private PsiSubstitutor determineUsageSubstitutor(PsiJavaCodeReferenceElement referenceElement) {
        PsiType[] typeArguments = referenceElement.getTypeParameters();
        PsiSubstitutor usageSubstitutor = PsiSubstitutor.EMPTY;
        PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
        if (typeParameters.length == typeArguments.length) {
            for (int i = 0; i < typeParameters.length; i++) {
                usageSubstitutor = usageSubstitutor.put(typeParameters[i], typeArguments[i]);
            }
        }
        return usageSubstitutor;
    }

    private static class ReferenceParameterList
        implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceParameterList, PsiTypeElement> {
        private static final ReferenceParameterList INSTANCE = new ReferenceParameterList();

        @Override
        public List<PsiTypeElement> getChildren(PsiReferenceParameterList list) {
            return Arrays.asList(list.getTypeParameterElements());
        }
    }

    private static class TypeParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiTypeParameterList, PsiTypeParameter> {
        private static final TypeParameterList INSTANCE = new TypeParameterList();

        @Override
        public List<PsiTypeParameter> getChildren(PsiTypeParameterList psiTypeParameterList) {
            return Arrays.asList(psiTypeParameterList.getTypeParameters());
        }
    }
}
