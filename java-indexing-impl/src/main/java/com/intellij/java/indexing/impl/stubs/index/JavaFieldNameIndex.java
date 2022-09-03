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
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;

import javax.annotation.Nonnull;
import java.util.Collection;

public class JavaFieldNameIndex extends StringStubIndexExtension<PsiField> {

  private static final JavaFieldNameIndex ourInstance = new JavaFieldNameIndex();
  public static JavaFieldNameIndex getInstance() {
    return ourInstance;
  }

  @Nonnull
  @Override
  public StubIndexKey<String, PsiField> getKey() {
    return JavaStubIndexKeys.FIELDS;
  }

  @Override
  public Collection<PsiField> get(final String s, final Project project, @Nonnull final ProjectAwareSearchScope scope) {
    return super.get(s, project, new JavaSourceFilterScope(scope));
  }
}