/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.ui;

import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.java.language.psi.PsiType;
import consulo.ide.impl.idea.ui.DuplicateNodeRenderer;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.tree.PresentationData;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * @author anna
 */
public class MigrationRootNode extends AbstractTreeNode<TypeMigrationLabeler> implements DuplicateNodeRenderer.DuplicatableNode {
  private final TypeMigrationLabeler myLabeler;
  private List<MigrationNode> myCachedChildren;
  private final PsiElement[] myRoots;
  private final boolean myPreviewUsages;

  protected MigrationRootNode(Project project, TypeMigrationLabeler labeler, final PsiElement[] roots, final boolean previewUsages) {
    super(project, labeler);
    myLabeler = labeler;
    myRoots = roots;
    myPreviewUsages = previewUsages;
  }

  @Override
  @Nonnull
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myCachedChildren == null) {
      myCachedChildren = new ArrayList<>();
      if (myPreviewUsages) {
        for (Pair<TypeMigrationUsageInfo, PsiType> root : myLabeler.getMigrationRoots()) {
          addRoot(root.getFirst(), root.getSecond());
        }
      } else {
        for (PsiElement root : myRoots) {
          addRoot(new TypeMigrationUsageInfo(root), myLabeler.getMigrationRootTypeFunction().apply(root));
        }
      }
    }
    return myCachedChildren;
  }

  private void addRoot(TypeMigrationUsageInfo info, PsiType migrationType) {
    final HashSet<TypeMigrationUsageInfo> parents = new HashSet<>();
    parents.add(info);
    final MigrationNode migrationNode = new MigrationNode(getProject(), info, migrationType, myLabeler, parents, new HashMap<>());

    myCachedChildren.add(migrationNode);
  }

  @Override
  protected void update(final PresentationData presentation) {

  }

  @Override
  public DefaultMutableTreeNode getDuplicate() {
    return null;
  }

}
