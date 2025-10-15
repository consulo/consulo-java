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

package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyMemberType;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.generation.ClassMember;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class GenerateFieldOrPropertyHandler extends GenerateMembersHandlerBase {
    private final String myAttributeName;
    private final PsiType myType;
    private final PropertyMemberType myMemberType;
    private final PsiAnnotation[] myAnnotations;

    public GenerateFieldOrPropertyHandler(
        String attributeName,
        PsiType type,
        PropertyMemberType memberType,
        PsiAnnotation... annotations
    ) {
        super(LocalizeValue.empty());
        myAttributeName = attributeName;
        myType = type;
        myMemberType = memberType;
        myAnnotations = annotations;
    }

    @Override
    @RequiredUIAccess
    protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
        return ClassMember.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    @RequiredWriteAction
    public List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members)
        throws IncorrectOperationException {
        PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
        try {
            String fieldName = getFieldName(aClass);
            PsiField psiField = psiElementFactory.createField(fieldName, myType);
            GenerationInfo[] infos = new GenerateGetterAndSetterHandler().generateMemberPrototypes(aClass, new PsiFieldMember(psiField));
            if (myAnnotations.length > 0) {
                PsiMember targetMember = null;
                if (myMemberType == PropertyMemberType.FIELD) {
                    targetMember = psiField;
                }
                else {
                    for (GenerationInfo info : infos) {
                        PsiMember member = info.getPsiMember();
                        if (!(member instanceof PsiMethod)) {
                            continue;
                        }
                        if (myMemberType == PropertyMemberType.GETTER && PropertyUtil.isSimplePropertyGetter((PsiMethod) member) || myMemberType == PropertyMemberType.SETTER && PropertyUtil
                            .isSimplePropertySetter((PsiMethod) member)) {
                            targetMember = member;
                            break;
                        }
                    }
                    if (targetMember == null) {
                        targetMember = findExistingMember(aClass, myMemberType);
                    }
                }
                PsiModifierList modifierList = targetMember != null ? targetMember.getModifierList() : null;
                if (modifierList != null) {
                    for (PsiAnnotation annotation : myAnnotations) {
                        PsiAnnotation existing = modifierList.findAnnotation(annotation.getQualifiedName());
                        if (existing != null) {
                            existing.replace(annotation);
                        }
                        else {
                            modifierList.addAfter(annotation, null);
                        }
                    }
                }
            }
            return ContainerUtil.concat(Collections.singletonList(new PsiGenerationInfo<>(psiField)), Arrays.asList(infos));
        }
        catch (IncorrectOperationException e) {
            assert false : e;
            return Collections.emptyList();
        }
    }

    @Nullable
    public PsiMember findExistingMember(@Nonnull PsiClass aClass, @Nonnull PropertyMemberType memberType) {
        if (memberType == PropertyMemberType.FIELD) {
            return aClass.findFieldByName(getFieldName(aClass), false);
        }
        else if (memberType == PropertyMemberType.GETTER) {
            try {
                PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
                PsiField field = psiElementFactory.createField(myAttributeName, myType);
                PsiMethod[] templates =
                    GetterSetterPrototypeProvider.generateGetterSetters(field, myMemberType == PropertyMemberType.GETTER);
                for (PsiMethod template : templates) {
                    PsiMethod existingMethod = aClass.findMethodBySignature(template, true);
                    if (existingMethod != null) {
                        return existingMethod;
                    }
                }
            }
            catch (IncorrectOperationException e) {
                assert false : e;
            }
        }
        return null;
    }

    private String getFieldName(PsiClass aClass) {
        return myMemberType == PropertyMemberType.FIELD ? myAttributeName : JavaCodeStyleManager.getInstance(aClass.getProject())
            .propertyNameToVariableName(myAttributeName, VariableKind.FIELD);
    }

    @Override
    protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
    }
}
