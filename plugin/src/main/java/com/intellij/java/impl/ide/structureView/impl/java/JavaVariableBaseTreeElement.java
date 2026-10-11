// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.structureView.tree.SortableTreeElement;
import consulo.localize.LocalizeValue;
import consulo.project.DumbService;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import static com.intellij.java.language.psi.util.PsiFormatUtil.formatVariable;
import static com.intellij.java.language.psi.util.PsiFormatUtilBase.*;

abstract class JavaVariableBaseTreeElement<T extends PsiVariable> extends JavaClassTreeElementBase<T> implements SortableTreeElement {
    protected JavaVariableBaseTreeElement(boolean isInherited, T element) {
        super(isInherited, element);
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getPresentableText() {
        T field = getElement();
        if (field == null) {
            return LocalizeValue.empty();
        }

        boolean dumb = DumbService.isDumb(field.getProject());
        return LocalizeValue.of(StringUtil.replace(
            formatVariable(
                field,
                SHOW_NAME | (dumb ? 0 : SHOW_TYPE) | TYPE_AFTER | (dumb ? 0 : SHOW_INITIALIZER),
                PsiSubstitutor.EMPTY
            ),
            ":",
            ": "
        ));
    }

    @Override
    @RequiredReadAction
    public String getAlphaSortKey() {
        T element = getElement();
        if (element != null) {
            String name = element.getName();
            if (name != null) {
                return name;
            }
        }
        return "";
    }
}
