/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(EncapsulateFieldsProcessor.class);

    private PsiClass myClass;
    @Nonnull
    private final EncapsulateFieldsDescriptor myDescriptor;
    private final FieldDescriptor[] myFieldDescriptors;

    private Map<String, PsiMethod> myNameToGetter;
    private Map<String, PsiMethod> myNameToSetter;

    public EncapsulateFieldsProcessor(Project project, @Nonnull EncapsulateFieldsDescriptor descriptor) {
        super(project);
        myDescriptor = descriptor;
        myFieldDescriptors = descriptor.getSelectedFields();
        myClass = descriptor.getTargetClass();
    }

    public static void setNewFieldVisibility(PsiField field, EncapsulateFieldsDescriptor descriptor) {
        try {
            if (descriptor.getFieldsVisibility() != null) {
                field.normalizeDeclaration();
                PsiUtil.setModifierProperty(field, descriptor.getFieldsVisibility(), true);
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        FieldDescriptor[] fields = new FieldDescriptor[myFieldDescriptors.length];
        System.arraycopy(myFieldDescriptors, 0, fields, 0, myFieldDescriptors.length);
        return new EncapsulateFieldsViewDescriptor(fields);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.encapsulateFieldsCommandName(DescriptiveNameUtil.getDescriptiveName(myClass));
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();

        checkExistingMethods(conflicts, true);
        checkExistingMethods(conflicts, false);
        Collection<PsiClass> classes = ClassInheritorsSearch.search(myClass).findAll();
        for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
            Set<PsiMethod> setters = new HashSet<>();
            Set<PsiMethod> getters = new HashSet<>();

            for (PsiClass aClass : classes) {
                PsiMethod getterOverrider =
                    myDescriptor.isToEncapsulateGet() ? aClass.findMethodBySignature(fieldDescriptor.getGetterPrototype(), false) : null;
                if (getterOverrider != null) {
                    getters.add(getterOverrider);
                }
                PsiMethod setterOverrider =
                    myDescriptor.isToEncapsulateSet() ? aClass.findMethodBySignature(fieldDescriptor.getSetterPrototype(), false) : null;
                if (setterOverrider != null) {
                    setters.add(setterOverrider);
                }
            }
            if (!getters.isEmpty() || !setters.isEmpty()) {
                PsiField field = fieldDescriptor.getField();
                for (PsiReference reference : ReferencesSearch.search(field)) {
                    PsiElement place = reference.getElement();
                    LOG.assertTrue(place instanceof PsiReferenceExpression);
                    PsiExpression qualifierExpression = ((PsiReferenceExpression) place).getQualifierExpression();
                    PsiClass ancestor;
                    if (qualifierExpression == null) {
                        ancestor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
                    }
                    else {
                        ancestor = PsiUtil.resolveClassInType(qualifierExpression.getType());
                    }

                    boolean isGetter = !PsiUtil.isAccessedForWriting((PsiExpression) place);
                    for (PsiMethod overridden : isGetter ? getters : setters) {
                        if (InheritanceUtil.isInheritorOrSelf(myClass, ancestor, true)) {
                            conflicts.putValue(
                                overridden,
                                LocalizeValue.localizeTODO(
                                    "There is already a " + RefactoringUIUtil.getDescription(overridden, true) +
                                    " which would hide generated " + (isGetter ? "getter" : "setter") + " for " + place.getText()
                                )
                            );
                            break;
                        }
                    }
                }
            }
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @RequiredReadAction
    private void checkExistingMethods(MultiMap<PsiElement, LocalizeValue> conflicts, boolean isGetter) {
        if (isGetter) {
            if (!myDescriptor.isToEncapsulateGet()) {
                return;
            }
        }
        else if (!myDescriptor.isToEncapsulateSet()) {
            return;
        }

        for (FieldDescriptor descriptor : myFieldDescriptors) {
            PsiMethod prototype = isGetter
                ? descriptor.getGetterPrototype()
                : descriptor.getSetterPrototype();

            PsiType prototypeReturnType = prototype.getReturnType();
            PsiMethod existing = myClass.findMethodBySignature(prototype, true);
            if (existing != null) {
                PsiType returnType = existing.getReturnType();
                if (!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
                    String descr = PsiFormatUtil.formatMethod(
                        existing,
                        PsiSubstitutor.EMPTY,
                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE,
                        PsiFormatUtilBase.SHOW_TYPE
                    );
                    LocalizeValue message = isGetter
                        ? RefactoringLocalize.encapsulateFieldsGetterExists(
                        CommonRefactoringUtil.htmlEmphasize(descr),
                        CommonRefactoringUtil.htmlEmphasize(prototype.getName())
                    )
                        : RefactoringLocalize.encapsulateFieldsSetterExists(
                        CommonRefactoringUtil.htmlEmphasize(descr),
                        CommonRefactoringUtil.htmlEmphasize(prototype.getName())
                    );
                    conflicts.putValue(existing, message);
                }
            }
            else {
                PsiClass containingClass = myClass.getContainingClass();
                while (containingClass != null && existing == null) {
                    existing = containingClass.findMethodBySignature(prototype, true);
                    if (existing != null) {
                        for (PsiReference reference : ReferencesSearch.search(existing)) {
                            PsiElement place = reference.getElement();
                            LOG.assertTrue(place instanceof PsiReferenceExpression);
                            PsiExpression qualifierExpression = ((PsiReferenceExpression) place).getQualifierExpression();
                            PsiClass inheritor;
                            if (qualifierExpression == null) {
                                inheritor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
                            }
                            else {
                                inheritor = PsiUtil.resolveClassInType(qualifierExpression.getType());
                            }

                            if (InheritanceUtil.isInheritorOrSelf(inheritor, myClass, true)) {
                                conflicts.putValue(
                                    existing,
                                    LocalizeValue.localizeTODO(
                                        "There is already a " + RefactoringUIUtil.getDescription(existing, true) +
                                            " which would be hidden by generated " + (isGetter ? "getter" : "setter")
                                    )
                                );
                                break;
                            }
                        }
                    }
                    containingClass = containingClass.getContainingClass();
                }
            }
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<EncapsulateFieldUsageInfo> array = new ArrayList<>();
        for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
            for (PsiReference reference : ReferencesSearch.search(fieldDescriptor.getField())) {
                PsiElement element = reference.getElement();
                if (element == null) {
                    continue;
                }

                EncapsulateFieldHelper helper = EncapsulateFieldHelper.forLanguage(element.getLanguage());
                EncapsulateFieldUsageInfo usageInfo = helper.createUsage(myDescriptor, fieldDescriptor, reference);
                if (usageInfo != null) {
                    array.add(usageInfo);
                }
            }
        }
        EncapsulateFieldUsageInfo[] usageInfos = array.toArray(new EncapsulateFieldUsageInfo[array.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == myFieldDescriptors.length);

        for (int idx = 0; idx < elements.length; idx++) {
            PsiElement element = elements[idx];

            LOG.assertTrue(element instanceof PsiField);

            myFieldDescriptors[idx].refreshField((PsiField) element);
        }

        myClass = myFieldDescriptors[0].getField().getContainingClass();
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        updateFieldVisibility();
        generateAccessors();
        processUsagesPerFile(usages);
    }

    private void updateFieldVisibility() {
        if (myDescriptor.getFieldsVisibility() == null) {
            return;
        }

        for (FieldDescriptor descriptor : myFieldDescriptors) {
            setNewFieldVisibility(descriptor.getField(), myDescriptor);
        }
    }

    @RequiredWriteAction
    private void generateAccessors() {
        // generate accessors
        myNameToGetter = new HashMap<>();
        myNameToSetter = new HashMap<>();

        for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
            DocCommentPolicy<PsiDocComment> commentPolicy = new DocCommentPolicy<>(myDescriptor.getJavadocPolicy());

            PsiField field = fieldDescriptor.getField();
            PsiDocComment docComment = field.getDocComment();
            if (myDescriptor.isToEncapsulateGet()) {
                PsiMethod prototype = fieldDescriptor.getGetterPrototype();
                assert prototype != null;
                PsiMethod getter = addOrChangeAccessor(prototype, myNameToGetter);
                if (docComment != null) {
                    PsiDocComment getterJavadoc = (PsiDocComment) getter.addBefore(docComment, getter.getFirstChild());
                    commentPolicy.processNewJavaDoc(getterJavadoc);
                }
            }
            if (myDescriptor.isToEncapsulateSet() && !field.isFinal()) {
                PsiMethod prototype = fieldDescriptor.getSetterPrototype();
                assert prototype != null;
                addOrChangeAccessor(prototype, myNameToSetter);
            }

            if (docComment != null) {
                commentPolicy.processOldJavaDoc(docComment);
            }
        }
    }

    @RequiredReadAction
    private void processUsagesPerFile(UsageInfo[] usages) {
        Map<PsiFile, List<EncapsulateFieldUsageInfo>> usagesInFiles = new HashMap<>();
        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element == null) {
                continue;
            }
            PsiFile file = element.getContainingFile();
            List<EncapsulateFieldUsageInfo> usagesInFile = usagesInFiles.get(file);
            if (usagesInFile == null) {
                usagesInFile = new ArrayList<>();
                usagesInFiles.put(file, usagesInFile);
            }
            usagesInFile.add(((EncapsulateFieldUsageInfo) usage));
        }

        for (List<EncapsulateFieldUsageInfo> usageInfos : usagesInFiles.values()) {
            //this is to avoid elements to become invalid as a result of processUsage
            EncapsulateFieldUsageInfo[] infos = usageInfos.toArray(new EncapsulateFieldUsageInfo[usageInfos.size()]);
            CommonRefactoringUtil.sortDepthFirstRightLeftOrder(infos);

            for (EncapsulateFieldUsageInfo info : infos) {
                EncapsulateFieldHelper helper = EncapsulateFieldHelper.forLanguage(info.getElement().getLanguage());
                helper.processUsage(
                    info,
                    myDescriptor,
                    myNameToSetter.get(info.getFieldDescriptor().getSetterName()),
                    myNameToGetter.get(info.getFieldDescriptor().getGetterName())
                );
            }
        }
    }

    private PsiMethod addOrChangeAccessor(PsiMethod prototype, Map<String, PsiMethod> nameToAncestor) {
        PsiMethod existing = myClass.findMethodBySignature(prototype, false);
        PsiMethod result = existing;
        try {
            if (existing == null) {
                PsiUtil.setModifierProperty(prototype, myDescriptor.getAccessorsVisibility(), true);
                result = (PsiMethod) myClass.add(prototype);
            }
            else {
                //TODO : change visibility
            }
            nameToAncestor.put(prototype.getName(), result);
            return result;
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return null;
    }
}
