// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.application.dumb.DumbAware;
import jakarta.annotation.Nonnull;

import java.util.Collections;

public class FormatPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
    public FormatPostfixTemplate(@Nonnull JavaPostfixTemplateProvider provider) {
        super(
            "format",
            "String.format($EXPR$, $END$)",
            "String.format(expr)",
            Collections.singleton(
                new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(CommonClassNames.JAVA_LANG_STRING)
            ),
            LanguageLevel.JDK_1_3, false, provider
        );
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }
}