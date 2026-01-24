// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

/**
 * @author Vitaliy.Bibaev
 */
public interface PsiElementTransformer {
    void transform(PsiElement element);

    abstract class Base implements PsiElementTransformer {
        @Override
        public void transform(PsiElement element) {
            element.accept(getVisitor());
        }

        protected abstract PsiElementVisitor getVisitor();
    }
}
