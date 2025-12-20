/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.ImportFilter;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.collection.LinkedMultiMap;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.*;

abstract class StaticMembersProcessor<T extends PsiMember & PsiDocCommentOwner> implements Processor<T> {
    private final MultiMap<PsiClass, T> mySuggestions = new LinkedMultiMap<>();

    private final Map<PsiClass, Boolean> myPossibleClasses = new HashMap<>();

    private final PsiElement myPlace;
    private PsiType myExpectedType;

    protected StaticMembersProcessor(PsiElement place) {
        myPlace = place;
        myExpectedType = PsiType.NULL;
    }

    protected abstract boolean isApplicable(T member, PsiElement place);

    @Nonnull
    public List<T> getMembersToImport(boolean applicableOnly) {
        List<T> list = new ArrayList<>();
        List<T> applicableList = new ArrayList<>();
        for (Map.Entry<PsiClass, Collection<T>> methodEntry : mySuggestions.entrySet()) {
            registerMember(methodEntry.getKey(), methodEntry.getValue(), list, applicableList);
        }

        List<T> result = !applicableOnly && applicableList.isEmpty() ? list : applicableList;
        Collections.sort(result, CodeInsightUtil.createSortIdenticalNamedMembersComparator(myPlace));
        return result;
    }

    public PsiType getExpectedType() {
        if (myExpectedType == PsiType.NULL) {
            myExpectedType = getExpectedTypeInternal();
        }
        return myExpectedType;
    }

    private PsiType getExpectedTypeInternal() {
        if (!(myPlace instanceof PsiExpression)) {
            return null;
        }
        ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes((PsiExpression) myPlace, false);
        return types.length > 0 ? types[0].getType() : null;
    }

    @Override
    public boolean process(T member) {
        ProgressManager.checkCanceled();
        if (StaticImportMemberFix.isExcluded(member)) {
            return true;
        }
        PsiClass containingClass = member.getContainingClass();
        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            PsiFile containingFile = myPlace.getContainingFile();
            if (qualifiedName != null && containingFile != null && !ImportFilter.shouldImport(containingFile, qualifiedName)) {
                return true;
            }

            PsiModifierList modifierList = member.getModifierList();
            if (modifierList != null && member instanceof PsiMethod &&
                member.getLanguage().isKindOf(JavaLanguage.INSTANCE)
                && !modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                //methods in interfaces must have explicit static modifier, or they are not static;
                return true;
            }

            if (!PsiUtil.isFromDefaultPackage(member)) {
                mySuggestions.putValue(containingClass, member);
            }
        }
        return processCondition();
    }

    private boolean processCondition() {
        return mySuggestions.size() < 100;
    }

    private void registerMember(PsiClass containingClass, Collection<T> members, List<T> list, List<T> applicableList) {
        Boolean alreadyMentioned = myPossibleClasses.get(containingClass);
        if (alreadyMentioned == Boolean.TRUE) {
            return;
        }
        if (containingClass.getQualifiedName() == null) {
            return;
        }
        if (alreadyMentioned == null) {
            myPossibleClasses.put(containingClass, false);
        }
        for (T member : members) {
            if (!member.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            if (alreadyMentioned == null) {
                list.add(member);
                alreadyMentioned = Boolean.FALSE;
            }

            if (!PsiUtil.isAccessible(myPlace.getProject(), member, myPlace, containingClass)) {
                continue;
            }
            if (isApplicable(member, myPlace)) {
                applicableList.add(member);
                myPossibleClasses.put(containingClass, true);
                break;
            }
        }
    }
}
