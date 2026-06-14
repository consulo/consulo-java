// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiJavaModule;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;

import org.jspecify.annotations.Nullable;

/**
 * Allows searching for Java (JPMS) modules declared in the project
 */
public final class JavaModuleSearch extends ExtensibleQueryFactory<PsiJavaModule, JavaModuleSearch.Parameters> {
  public static final JavaModuleSearch INSTANCE = new JavaModuleSearch();

  public static class Parameters {
    private final String myName;
    private final Project myProject;
    private final GlobalSearchScope myScope;

    /**
     * @param name    module name (null to find all modules)
     * @param project project
     * @param scope   scope to search in
     */
    public Parameters(@Nullable String name, Project project, GlobalSearchScope scope) {
      myName = name;
      myProject = project;
      myScope = scope;
    }

    @Nullable
    public String getName() {
      return myName;
    }

    public Project getProject() {
      return myProject;
    }

    public GlobalSearchScope getScope() {
      return myScope;
    }
  }

  private JavaModuleSearch() {
    super(JavaModuleSearchExecutor.class);
  }

  /**
   * Find JPMS modules in the scope
   *
   * @param name    name of the module to find
   * @param project project
   * @param scope   scope to use
   * @return the query that contains found modules results
   */
  public static Query<PsiJavaModule> search(String name, Project project, GlobalSearchScope scope) {
    return INSTANCE.createQuery(new Parameters(name, project, scope));
  }

  /**
   * Find all JPMS modules in the scope
   *
   * @param project project
   * @param scope   scope to use
   * @return the query that contains found modules results
   */
  public static Query<PsiJavaModule> allModules(Project project, GlobalSearchScope scope) {
    return INSTANCE.createQuery(new Parameters(null, project, scope));
  }
}
