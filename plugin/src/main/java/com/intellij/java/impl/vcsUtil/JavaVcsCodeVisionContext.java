// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.vcsUtil;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.versionControlSystem.codeVision.VcsCodeVisionCurlyBracketLanguageContext;

@ExtensionImpl
public class JavaVcsCodeVisionContext extends VcsCodeVisionCurlyBracketLanguageContext {

    @Override
    public boolean isAccepted(PsiElement element) {
        return element instanceof PsiMethod || (element instanceof PsiClass && !(element instanceof PsiTypeParameter));
    }

    @Override
    protected boolean isRBrace(PsiElement element) {
        return PsiUtil.isJavaToken(element, JavaTokenType.RBRACE);
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
