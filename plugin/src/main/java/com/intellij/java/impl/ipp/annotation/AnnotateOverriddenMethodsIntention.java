/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.annotation;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.ClassUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Collection;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AnnotateOverriddenMethodsIntention", fileExtensions = "java", categories = {"Java", "Annotations"})
public class AnnotateOverriddenMethodsIntention extends MutablyNamedIntention {
    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.annotateOverriddenMethodsIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new AnnotateOverriddenMethodsPredicate();
    }

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiAnnotation annotation = (PsiAnnotation) element;
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
            return LocalizeValue.empty();
        }
        String annotationName = ClassUtil.extractClassName(qualifiedName);
        if (element.getParent().getParent() instanceof PsiMethod) {
            return IntentionPowerPackLocalize.annotateOverriddenMethodsIntentionMethodName(annotationName);
        }
        else {
            return IntentionPowerPackLocalize.annotateOverriddenMethodsIntentionParametersName(annotationName);
        }
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiAnnotation annotation = (PsiAnnotation) element;
        String annotationName = annotation.getQualifiedName();
        if (annotationName == null) {
            return;
        }
        PsiElement parent = annotation.getParent();
        PsiElement grandParent = parent.getParent();
        PsiMethod method;
        int parameterIndex;
        if (!(grandParent instanceof PsiMethod)) {
            if (!(grandParent instanceof PsiParameter)) {
                return;
            }
            PsiParameter parameter = (PsiParameter) grandParent;
            PsiElement greatGrandParent = grandParent.getParent();
            if (!(greatGrandParent instanceof PsiParameterList)) {
                return;
            }
            PsiParameterList parameterList =
                (PsiParameterList) greatGrandParent;
            parameterIndex = parameterList.getParameterIndex(parameter);
            PsiElement greatGreatGrandParent =
                greatGrandParent.getParent();
            if (!(greatGreatGrandParent instanceof PsiMethod)) {
                return;
            }
            method = (PsiMethod) greatGreatGrandParent;
        }
        else {
            parameterIndex = -1;
            method = (PsiMethod) grandParent;
        }
        Project project = element.getProject();
        Collection<PsiMethod> overridingMethods =
            OverridingMethodsSearch.search(method,
                GlobalSearchScope.allScope(project), true).findAll();
        PsiNameValuePair[] attributes =
            annotation.getParameterList().getAttributes();
        for (PsiMethod overridingMethod : overridingMethods) {
            if (parameterIndex == -1) {
                annotate(overridingMethod, annotationName, attributes, element);
            }
            else {
                PsiParameterList parameterList =
                    overridingMethod.getParameterList();
                PsiParameter[] parameters = parameterList.getParameters();
                PsiParameter parameter = parameters[parameterIndex];
                annotate(parameter, annotationName, attributes, element);
            }
        }
    }

    private static void annotate(PsiModifierListOwner modifierListOwner,
                                 String annotationName,
                                 PsiNameValuePair[] attributes,
                                 PsiElement context) {
        Project project = context.getProject();
        ExternalAnnotationsManager annotationsManager =
            ExternalAnnotationsManager.getInstance(project);
        PsiModifierList modifierList =
            modifierListOwner.getModifierList();
        if (modifierList == null) {
            return;
        }
        if (modifierList.findAnnotation(annotationName) != null) {
            return;
        }
        ExternalAnnotationsManager.AnnotationPlace
            annotationAnnotationPlace =
            annotationsManager.chooseAnnotationsPlace(modifierListOwner);
        if (annotationAnnotationPlace ==
            ExternalAnnotationsManager.AnnotationPlace.NOWHERE) {
            return;
        }
        PsiFile fromFile = context.getContainingFile();
        if (annotationAnnotationPlace ==
            ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
            annotationsManager.annotateExternally(modifierListOwner,
                annotationName, fromFile, attributes);
        }
        else {
            PsiFile containingFile =
                modifierListOwner.getContainingFile();
            if (!FileModificationService.getInstance().preparePsiElementForWrite(containingFile)) {
                return;
            }
            PsiAnnotation inserted =
                modifierList.addAnnotation(annotationName);
            for (PsiNameValuePair pair : attributes) {
                inserted.setDeclaredAttributeValue(pair.getName(),
                    pair.getValue());
            }
            JavaCodeStyleManager codeStyleManager =
                JavaCodeStyleManager.getInstance(project);
            codeStyleManager.shortenClassReferences(inserted);
            if (containingFile != fromFile) {
                LanguageUndoUtil.markPsiFileForUndo(fromFile);
            }
        }
    }
}
