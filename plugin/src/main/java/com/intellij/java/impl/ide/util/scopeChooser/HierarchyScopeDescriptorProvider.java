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
package com.intellij.java.impl.ide.util.scopeChooser;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.ScopeDescriptor;
import consulo.content.scope.ScopeDescriptorProvider;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class HierarchyScopeDescriptorProvider implements ScopeDescriptorProvider {
  @Nonnull
  public ScopeDescriptor[] getScopeDescriptors(Project project) {
    return new ScopeDescriptor[]{new ClassHierarchyScopeDescriptor(project)};
  }
}