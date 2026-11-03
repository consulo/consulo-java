// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import consulo.language.psi.PsiElement;

/**
 * Represent a reference from the Javadoc.
 * Usually a reference to a class through its children ({@link com.intellij.java.language.psi.PsiJavaReference}), or a bare reference to a
 * method/field through itself.
 */
public interface PsiDocReferenceHolder extends PsiElement {
}
