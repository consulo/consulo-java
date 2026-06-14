// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaModuleSearchExecutor extends QueryExecutor<PsiJavaModule, JavaModuleSearch.Parameters> {
}
