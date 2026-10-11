// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.SortableTreeElement;
import consulo.project.DumbService;
import consulo.util.lang.StringUtil;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.java.language.psi.util.PsiFormatUtil.*;

public class PsiFieldTreeElement extends JavaVariableBaseTreeElement<PsiField> implements SortableTreeElement {
    public PsiFieldTreeElement(PsiField field, boolean isInherited) {
        super(isInherited, field);
    }

    @Override
    @RequiredReadAction
    public Collection<StructureViewTreeElement> getChildrenBase() {
        if (getField() instanceof PsiEnumConstant enumConstant) {
            PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
            if (initializingClass != null) {
                return JavaClassTreeElement.getClassChildren(initializingClass);
            }
        }
        return Collections.emptyList();
    }

    @RequiredReadAction
    public PsiField getField() {
        return getElement();
    }
}
