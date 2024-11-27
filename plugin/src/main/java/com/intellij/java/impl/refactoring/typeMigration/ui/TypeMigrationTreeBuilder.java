// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.refactoring.typeMigration.ui;

import jakarta.annotation.Nullable;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.tree.AlphaComparator;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.application.progress.ProgressIndicator;
import consulo.project.Project;

/**
 * @author anna
 */
public class TypeMigrationTreeBuilder extends AbstractTreeBuilder
{
	public TypeMigrationTreeBuilder(JTree tree, Project project)
	{
		super(tree, (DefaultTreeModel) tree.getModel(), new TypeMigrationTreeStructure(project), AlphaComparator.INSTANCE, false);
		initRootNode();
	}

	@Override
	protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor)
	{
		return false;
	}

	public void setRoot(MigrationRootNode root)
	{
		((TypeMigrationTreeStructure) getTreeStructure()).setRoots(root);
	}
}
