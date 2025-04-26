/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiMethod;
import consulo.colorScheme.TextAttributes;
import consulo.component.util.Iconable;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.ide.localize.IdeLocalize;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.lang.Comparing;

import java.awt.*;

public final class MethodHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
    private Image myRawIcon;
    private Image myStateIcon;
    private MethodHierarchyTreeStructure myTreeStructure;

    public MethodHierarchyNodeDescriptor(
        Project project,
        HierarchyNodeDescriptor parentDescriptor,
        PsiElement aClass,
        boolean isBase,
        MethodHierarchyTreeStructure treeStructure
    ) {
        super(project, parentDescriptor, aClass, isBase);
        myTreeStructure = treeStructure;
    }

    public final void setTreeStructure(MethodHierarchyTreeStructure treeStructure) {
        myTreeStructure = treeStructure;
    }

    PsiMethod getMethod(PsiClass aClass, boolean checkBases) {
        return MethodHierarchyUtil.findBaseMethodInClass(myTreeStructure.getBaseMethod(), aClass, checkBases);
    }

    public final PsiElement getPsiClass() {
        return getPsiElement();
    }

    /**
     * Element for OpenFileDescriptor
     */
    public final PsiElement getTargetElement() {
        PsiElement element = getPsiClass();
        if (!(element instanceof PsiClass aClass)) {
            return element;
        }
        if (!aClass.isValid()) {
            return null;
        }
        PsiMethod method = getMethod(aClass, false);
        if (method != null) {
            return method;
        }
        return aClass;
    }

    @Override
    @RequiredUIAccess
    public final boolean update() {
        int flags = Iconable.ICON_FLAG_VISIBILITY;
        if (isMarkReadOnly()) {
            flags |= Iconable.ICON_FLAG_READ_STATUS;
        }

        boolean changes = super.update();

        PsiElement aClass = getPsiClass();

        if (aClass == null) {
            String invalidPrefix = IdeLocalize.nodeHierarchyInvalid().get();
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        Image newRawIcon = IconDescriptorUpdaters.getIcon(aClass, flags);
        Image newStateIcon = aClass instanceof PsiClass psiClass ? calculateState(psiClass) : PlatformIconGroup.hierarchyMethoddefined();

        if (changes || newRawIcon != myRawIcon || newStateIcon != myStateIcon) {
            changes = true;

            myRawIcon = newRawIcon;
            myStateIcon = newStateIcon;

            Image newIcon = myRawIcon;

            if (myIsBase) {
                newIcon = ImageEffects.appendRight(PlatformIconGroup.hierarchyBase(), newIcon);
            }

            if (myStateIcon != null) {
                newIcon = ImageEffects.appendRight(myStateIcon, newIcon);
            }

            setIcon(newIcon);
        }

        CompositeAppearance oldText = myHighlightedText;

        myHighlightedText = new CompositeAppearance();
        TextAttributes classNameAttributes = null;
        if (myColor != null) {
            classNameAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
        }
        if (aClass instanceof PsiClass psiClass) {
            myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass(psiClass, false), classNameAttributes);
            myHighlightedText.getEnding().addText(
                "  (" + JavaHierarchyUtil.getPackageName(psiClass) + ")",
                HierarchyNodeDescriptor.getPackageNameAttributes()
            );
        }
        else if (aClass instanceof PsiFunctionalExpression functionalExpression) {
            myHighlightedText.getEnding()
                .addText(ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, false));
        }
        myName = myHighlightedText.getText();

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true;
        }
        return changes;
    }

    private Image calculateState(PsiClass psiClass) {
        PsiMethod method = getMethod(psiClass, false);
        if (method != null) {
            return method.isAbstract() ? null : PlatformIconGroup.hierarchyMethoddefined();
        }

        if (myTreeStructure.isSuperClassForBaseClass(psiClass)) {
            return PlatformIconGroup.hierarchyMethodnotdefined();
        }

        boolean isAbstractClass = psiClass.isAbstract();

        // was it implemented is in superclasses?
        PsiMethod baseClassMethod = getMethod(psiClass, true);

        boolean hasBaseImplementation = baseClassMethod != null && !baseClassMethod.isAbstract();

        return hasBaseImplementation || isAbstractClass
            ? PlatformIconGroup.hierarchyMethodnotdefined()
            : PlatformIconGroup.hierarchyShoulddefinemethod();
    }
}
