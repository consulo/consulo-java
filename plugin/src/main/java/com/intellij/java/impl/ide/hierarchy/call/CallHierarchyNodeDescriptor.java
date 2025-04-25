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
package com.intellij.java.impl.ide.hierarchy.call;

import com.intellij.java.impl.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.component.util.Iconable;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.ide.localize.IdeLocalize;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class CallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {
    private int myUsageCount = 1;
    private final List<PsiReference> myReferences = new ArrayList<PsiReference>();
    private final boolean myNavigateToReference;

    public CallHierarchyNodeDescriptor(
        @Nonnull Project project,
        final HierarchyNodeDescriptor parentDescriptor,
        @Nonnull PsiElement element,
        final boolean isBase,
        final boolean navigateToReference
    ) {
        super(project, parentDescriptor, element, isBase);
        myNavigateToReference = navigateToReference;
    }

    /**
     * @return PsiMethod or PsiClass or JspFile
     */
    public final PsiMember getEnclosingElement() {
        PsiElement element = getPsiElement();
        return element == null ? null : getEnclosingElement(element);
    }

    public static PsiMember getEnclosingElement(final PsiElement element) {
        return PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class);
    }

    public final void incrementUsageCount() {
        myUsageCount++;
    }

    /**
     * Element for OpenFileDescriptor
     */
    public final PsiElement getTargetElement() {
        return getPsiElement();
    }

    @Override
    public final boolean isValid() {
        return getEnclosingElement() != null;
    }

    @Override
    @RequiredUIAccess
    public final boolean update() {
        final CompositeAppearance oldText = myHighlightedText;
        final Icon oldIcon = TargetAWT.to(getIcon());

        int flags = Iconable.ICON_FLAG_VISIBILITY;
        if (isMarkReadOnly()) {
            flags |= Iconable.ICON_FLAG_READ_STATUS;
        }

        boolean changes = super.update();

        final PsiElement enclosingElement = getEnclosingElement();

        if (enclosingElement == null) {
            final String invalidPrefix = IdeLocalize.nodeHierarchyInvalid().get();
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        Image newIcon = IconDescriptorUpdaters.getIcon(enclosingElement, flags);
        if (changes && myIsBase) {
            newIcon = ImageEffects.appendRight(AllIcons.Hierarchy.Base, newIcon);
        }
        setIcon(newIcon);

        myHighlightedText = new CompositeAppearance();
        TextAttributes mainTextAttributes = null;
        if (myColor != null) {
            mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
        }
        if (enclosingElement instanceof PsiMethod) {
            if (enclosingElement instanceof SyntheticElement) {
                PsiFile file = enclosingElement.getContainingFile();
                myHighlightedText.getEnding().addText(
                    file != null ? file.getName() : IdeLocalize.nodeCallHierarchyUnknownJsp().get(),
                    mainTextAttributes
                );
            }
            else {
                final PsiMethod method = (PsiMethod)enclosingElement;
                final StringBuilder buffer = new StringBuilder(128);
                final PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                    buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
                    buffer.append('.');
                }
                final String methodText = PsiFormatUtil.formatMethod(
                    method,
                    PsiSubstitutor.EMPTY,
                    PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                    PsiFormatUtilBase.SHOW_TYPE
                );
                buffer.append(methodText);

                myHighlightedText.getEnding().addText(buffer.toString(), mainTextAttributes);
            }
        }
        else {
            myHighlightedText.getEnding()
                .addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
        }

        if (myUsageCount > 1) {
            myHighlightedText.getEnding().addText(
                IdeLocalize.nodeCallHierarchyNUsages(myUsageCount).get(),
                HierarchyNodeDescriptor.getUsageCountPrefixAttributes()
            );
        }

        final PsiClass containingClass =
            enclosingElement instanceof PsiMethod method ? method.getContainingClass() : (PsiClass)enclosingElement;
        if (containingClass != null) {
            final String packageName = JavaHierarchyUtil.getPackageName(containingClass);
            myHighlightedText.getEnding().addText("  (" + packageName + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
        }

        myName = myHighlightedText.getText();

        if (!Comparing.equal(myHighlightedText, oldText) || !Comparing.equal(getIcon(), oldIcon)) {
            changes = true;
        }
        return changes;
    }

    public void addReference(final PsiReference reference) {
        myReferences.add(reference);
    }

    public boolean hasReference(PsiReference reference) {
        return myReferences.contains(reference);
    }

    @Override
    @RequiredReadAction
    public void navigate(boolean requestFocus) {
        if (!myNavigateToReference) {
            PsiElement element = getPsiElement();
            if (element instanceof Navigatable navigatable && navigatable.canNavigate()) {
                navigatable.navigate(requestFocus);
            }
            return;
        }

        final PsiReference firstReference = myReferences.get(0);
        final PsiElement element = firstReference.getElement();
        if (element == null) {
            return;
        }
        final PsiElement callElement = element.getParent();
        if (callElement instanceof Navigatable navigatable && navigatable.canNavigate()) {
            navigatable.navigate(requestFocus);
        }
        else {
            final PsiFile psiFile = callElement.getContainingFile();
            if (psiFile == null || psiFile.getVirtualFile() == null) {
                return;
            }
            FileEditorManager.getInstance(getProject()).openFile(psiFile.getVirtualFile(), requestFocus);
        }

        Editor editor = PsiUtilBase.findEditor(callElement);

        if (editor != null) {

            HighlightManager highlightManager = HighlightManager.getInstance(getProject());
            EditorColorsManager colorManager = EditorColorsManager.getInstance();
            TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
            ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
            for (PsiReference psiReference : myReferences) {
                final PsiElement eachElement = psiReference.getElement();
                if (eachElement != null) {
                    final PsiElement eachMethodCall = eachElement.getParent();
                    if (eachMethodCall != null) {
                        final TextRange textRange = eachMethodCall.getTextRange();
                        highlightManager.addRangeHighlight(
                            editor,
                            textRange.getStartOffset(),
                            textRange.getEndOffset(),
                            attributes,
                            false,
                            highlighters
                        );
                    }
                }
            }
        }
    }

    @Override
    public boolean canNavigate() {
        if (!myNavigateToReference) {
            PsiElement element = getPsiElement();
            return element instanceof Navigatable navigatable && navigatable.canNavigate();
        }
        if (myReferences.isEmpty()) {
            return false;
        }
        final PsiReference firstReference = myReferences.get(0);
        final PsiElement callElement = firstReference.getElement().getParent();
        if (callElement == null || !callElement.isValid()) {
            return false;
        }
        if (!(callElement instanceof Navigatable navigatable && navigatable.canNavigate())) {
            final PsiFile psiFile = callElement.getContainingFile();
            if (psiFile == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}
