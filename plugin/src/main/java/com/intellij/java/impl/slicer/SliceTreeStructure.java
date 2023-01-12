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
package com.intellij.java.impl.slicer;

import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeStructureBase;
import consulo.project.ui.view.tree.TreeStructureProvider;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceTreeStructure extends AbstractTreeStructureBase {
  private final SliceNode myRoot;

  public SliceTreeStructure(@Nonnull Project project, @Nonnull SliceNode rootNode) {
    super(project);
    myRoot = rootNode;
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  @Override
  public void commit() {

  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final Object element) {
    return true;//!ApplicationManager.getApplication().isUnitTestMode();
  }
}
