// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;

/**
 * Represents a module that is implicitly imported.
 */
public final class ImplicitlyImportedModule implements ImplicitlyImportedElement {

  private final String myModuleName;
  private final PsiImportModuleStatement myModuleStatement;

  private ImplicitlyImportedModule(Project project, String moduleName) {
    myModuleName = moduleName;
    myModuleStatement = createImportStatementInner(project, moduleName);
  }

  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public PsiImportStatementBase createImportStatement() {
    return myModuleStatement;
  }

  private static PsiImportModuleStatement createImportStatementInner(Project project, String moduleName) {
    if (PsiJavaModule.JAVA_BASE.equals(moduleName)) {
      return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
        PsiElementFactory factory = PsiElementFactory.getInstance(project);
        return CachedValueProvider.Result.create(factory.createImportModuleStatementFromText(PsiJavaModule.JAVA_BASE),
                                                 ProjectRootModificationTracker.getInstance(project));
      });
    }
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    return factory.createImportModuleStatementFromText(moduleName);
  }

  public static ImplicitlyImportedModule create(Project project, String moduleName) {
    return new ImplicitlyImportedModule(project, moduleName);
  }
}
