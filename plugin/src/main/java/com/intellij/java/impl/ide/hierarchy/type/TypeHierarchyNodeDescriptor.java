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
package com.intellij.java.impl.ide.hierarchy.type;

import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import consulo.colorScheme.TextAttributes;
import consulo.ide.localize.IdeLocalize;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.psi.PsiElement;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.util.CompositeAppearance;
import consulo.ui.image.ImageEffects;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

public final class TypeHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
    public TypeHierarchyNodeDescriptor(
        Project project,
        HierarchyNodeDescriptor parentDescriptor,
        PsiElement classOrFunctionalExpression,
        boolean isBase
    ) {
        super(project, parentDescriptor, classOrFunctionalExpression, isBase);
    }

    public final PsiElement getPsiClass() {
        return getPsiElement();
    }

    @Override
    public boolean canBeDeleted() {
        PsiElement element = getPsiElement();
        return element instanceof PsiClass && !(element instanceof PsiAnonymousClass);
    }

    @Override
    public @Nullable String getQualifiedName() {
        return getPsiElement() instanceof PsiClass psiClass ? psiClass.getQualifiedName() : "";
    }

    @Override
    @RequiredUIAccess
    public final boolean update() {
        boolean changes = super.update();

        if (getPsiElement() == null) {
            String invalidPrefix = IdeLocalize.nodeHierarchyInvalid().get();
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        if (changes && myIsBase) {
            setIcon(ImageEffects.appendRight(PlatformIconGroup.hierarchyBase(), getIcon()));
        }

        PsiElement psiElement = getPsiClass();

        CompositeAppearance oldText = myHighlightedText;

        myHighlightedText = new CompositeAppearance();

        TextAttributes classNameAttributes = null;
        if (myColor != null) {
            classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
        }
        if (psiElement instanceof PsiClass psiClass) {
            myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass(psiClass, false).get(), classNameAttributes);
            myHighlightedText.getEnding().addText(
                " (" + JavaHierarchyUtil.getPackageName(psiClass) + ")",
                HierarchyNodeDescriptor.getPackageNameAttributes()
            );
        }
        else if (psiElement instanceof PsiFunctionalExpression functionalExpression) {
            myHighlightedText.getEnding()
                .addText(ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, false).get());
        }
        myName = myHighlightedText.getText();

        if (!Objects.equals(myHighlightedText, oldText)) {
            changes = true;
        }
        return changes;
    }
}
