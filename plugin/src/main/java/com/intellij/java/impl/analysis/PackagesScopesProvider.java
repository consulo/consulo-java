/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.java.impl.analysis;

import consulo.content.internal.scope.CustomScopesProviderEx;
import consulo.content.scope.NamedScope;
import consulo.ide.impl.psi.search.scope.ProjectProductionScope;
import consulo.ide.impl.psi.search.scope.TestsScope;
import consulo.project.Project;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class PackagesScopesProvider extends CustomScopesProviderEx {
  private final NamedScope myProjectProductionScope;
  private final List<NamedScope> myScopes;

  public static PackagesScopesProvider getInstance(Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(project, PackagesScopesProvider.class);
  }

  public PackagesScopesProvider() {
    myProjectProductionScope = new ProjectProductionScope();
    final NamedScope projectTestScope = new TestsScope();
    myScopes = Arrays.asList(myProjectProductionScope, projectTestScope);
  }

  @Override
  public void acceptScopes(@Nonnull Consumer<NamedScope> consumer) {
    for (NamedScope scope : myScopes) {
      consumer.accept(scope);
    }
  }

  public NamedScope getProjectProductionScope() {
    return myProjectProductionScope;
  }
}