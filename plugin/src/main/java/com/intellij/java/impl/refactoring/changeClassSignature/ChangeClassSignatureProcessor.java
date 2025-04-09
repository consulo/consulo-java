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
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
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

    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 1);
        LOG.assertTrue(elements[0] instanceof PsiClass);
        myClass = (PsiClass)elements[0];
    }

    protected String getCommandName() {
        return ChangeClassSignatureDialog.REFACTORING_NAME;
    }

    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new ChangeClassSigntaureViewDescriptor(myClass);
    }

    @Override
    protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
        final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

        final PsiTypeParameter[] parameters = myClass.getTypeParameters();
        final Map<String, TypeParameterInfo> infos = new HashMap<String, TypeParameterInfo>();
        for (TypeParameterInfo info : myNewSignature) {
            final String newName = info.isForExistingParameter() ? parameters[info.getOldParameterIndex()].getName() : info.getNewName();
            TypeParameterInfo existing = infos.get(newName);
            if (existing != null) {
                conflicts.putValue(
                    myClass,
                    RefactoringUIUtil.getDescription(myClass, false) + " already contains type parameter " + newName
                );
            }
            infos.put(newName, info);
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @Nonnull
    protected UsageInfo[] findUsages() {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
        List<UsageInfo> result = new ArrayList<UsageInfo>();

        boolean hadTypeParameters = myClass.hasTypeParameters();
        for (final PsiReference reference : ReferencesSearch.search(myClass, projectScope, false)) {
            if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
                PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)reference.getElement();
                PsiElement parent = referenceElement.getParent();
                if (parent instanceof PsiTypeElement && parent.getParent() instanceof PsiInstanceOfExpression) {
                    continue;
                }
                if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression || parent instanceof PsiAnonymousClass ||
                    parent instanceof PsiReferenceList) {
                    if (!hadTypeParameters || referenceElement.getTypeParameters().length > 0) {
                        result.add(new UsageInfo(referenceElement));
                    }
                }
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    protected void performRefactoring(UsageInfo[] usages) {
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

    private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
        final PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
        final boolean[] toRemoveParms = detectRemovedParameters(typeParameters);

        for (final UsageInfo usage : usages) {
            LOG.assertTrue(usage.getElement() instanceof PsiJavaCodeReferenceElement);
            processUsage(usage, typeParameters, toRemoveParms);
        }
        final Map<PsiTypeElement, PsiClass> supersMap = new HashMap<PsiTypeElement, PsiClass>();
        myClass.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitTypeElement(PsiTypeElement typeElement) {
                super.visitTypeElement(typeElement);
                final PsiType type = typeElement.getType();
                final PsiClass psiClass = PsiUtil.resolveClassInType(type);
                if (psiClass instanceof PsiTypeParameter) {
                    final int i = ArrayUtil.find(typeParameters, psiClass);
                    if (i >= 0 && i < toRemoveParms.length && toRemoveParms[i]) {
                        supersMap.put(typeElement, psiClass.getSuperClass());
                    }
                }
            }
        });
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        for (Map.Entry<PsiTypeElement, PsiClass> classEntry : supersMap.entrySet()) {
            classEntry.getKey().replace(elementFactory.createTypeElement(elementFactory.createType(classEntry.getValue())));
        }
        changeClassSignature(typeParameters, toRemoveParms);
    }

    private void changeClassSignature(final PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParms)
        throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        List<PsiTypeParameter> newTypeParameters = new ArrayList<PsiTypeParameter>();
        for (final TypeParameterInfo info : myNewSignature) {
            int oldIndex = info.getOldParameterIndex();
            if (oldIndex >= 0) {
                newTypeParameters.add(originalTypeParameters[oldIndex]);
            }
            else {
                newTypeParameters.add(factory.createTypeParameterFromText(info.getNewName(), null));
            }
        }
        ChangeSignatureUtil.synchronizeList(myClass.getTypeParameterList(), newTypeParameters, TypeParameterList.INSTANCE, toRemoveParms);
    }

    private boolean[] detectRemovedParameters(final PsiTypeParameter[] originaltypeParameters) {
        final boolean[] toRemoveParms = new boolean[originaltypeParameters.length];
        Arrays.fill(toRemoveParms, true);
        for (final TypeParameterInfo info : myNewSignature) {
            int oldParameterIndex = info.getOldParameterIndex();
            if (oldParameterIndex >= 0) {
                toRemoveParms[oldParameterIndex] = false;
            }
        }
        return toRemoveParms;
    }

    private void processUsage(final UsageInfo usage, final PsiTypeParameter[] originalTypeParameters, final boolean[] toRemoveParms)
        throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
        PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

        PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
        PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
        if (oldValues.length != originalTypeParameters.length) {
            return;
        }
        List<PsiTypeElement> newValues = new ArrayList<PsiTypeElement>();
        for (final TypeParameterInfo info : myNewSignature) {
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
        ChangeSignatureUtil.synchronizeList(referenceParameterList, newValues, ReferenceParameterList.INSTANCE, toRemoveParms);
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

    private static class ReferenceParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceParameterList, PsiTypeElement> {
        private static final ReferenceParameterList INSTANCE = new ReferenceParameterList();

        public List<PsiTypeElement> getChildren(PsiReferenceParameterList list) {
            return Arrays.asList(list.getTypeParameterElements());
        }
    }

    private static class TypeParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiTypeParameterList, PsiTypeParameter> {
        private static final TypeParameterList INSTANCE = new TypeParameterList();

        public List<PsiTypeParameter> getChildren(PsiTypeParameterList psiTypeParameterList) {
            return Arrays.asList(psiTypeParameterList.getTypeParameters());
        }
    }

}
