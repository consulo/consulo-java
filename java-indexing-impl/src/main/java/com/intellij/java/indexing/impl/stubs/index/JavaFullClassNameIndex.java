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
 * @author max
 */
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.indexing.impl.search.JavaSourceFilterScope;
import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.IntStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;

import javax.annotation.Nonnull;
import java.util.Collection;

@ExtensionImpl
public class JavaFullClassNameIndex extends IntStubIndexExtension<PsiClass> {
  private static final JavaFullClassNameIndex ourInstance = new JavaFullClassNameIndex();

  public static JavaFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @Nonnull
  @Override
  public StubIndexKey<Integer, PsiClass> getKey() {
    return JavaStubIndexKeys.CLASS_FQN;
  }

  @Override
  public Collection<PsiClass> get(@Nonnull final Integer integer, @Nonnull final Project project, @Nonnull final ProjectAwareSearchScope scope) {
    return StubIndex.getElements(getKey(), integer, project, new JavaSourceFilterScope(scope), PsiClass.class);
  }
}