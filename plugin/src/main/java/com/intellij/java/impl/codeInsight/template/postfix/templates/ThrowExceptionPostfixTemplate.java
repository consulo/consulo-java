// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.language.LanguageLevel;
import consulo.application.dumb.DumbAware;
import consulo.java.language.module.util.JavaClassNames;
import jakarta.annotation.Nonnull;

import java.util.Collections;

public class ThrowExceptionPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
    public ThrowExceptionPostfixTemplate(@Nonnull JavaPostfixTemplateProvider provider) {
        super(
            "throw",
            "throw $EXPR$;$END$",
            "throw expr",
            Collections.singleton(
                new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(JavaClassNames.JAVA_LANG_THROWABLE)
            ),
            LanguageLevel.JDK_1_3, true, provider
        );
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }
}