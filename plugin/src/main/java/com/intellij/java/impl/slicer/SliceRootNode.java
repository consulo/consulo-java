/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.application.progress.ProgressIndicator;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceRootNode extends SliceNode
{
	private final SliceUsage myRootUsage;

	public SliceRootNode(@Nonnull Project project, @Nonnull DuplicateMap targetEqualUsages, final SliceUsage rootUsage)
	{
		super(project, SliceUsage.createRootUsage(rootUsage.getElement().getContainingFile(), rootUsage.params), targetEqualUsages);
		myRootUsage = rootUsage;
	}

	void switchToAllLeavesTogether(SliceUsage rootUsage)
	{
		SliceNode node = new SliceNode(getProject(), rootUsage, targetEqualUsages);
		myCachedChildren = Collections.singletonList(node);
	}

	@Nonnull
	@Override
	SliceRootNode copy()
	{
		SliceUsage newUsage = getValue().copy();
		SliceRootNode newNode = new SliceRootNode(getProject(), new DuplicateMap(), newUsage);
		newNode.dupNodeCalculated = dupNodeCalculated;
		newNode.duplicate = duplicate;
		return newNode;
	}

	@Override
	@Nonnull
	public Collection<? extends AbstractTreeNode<?>> getChildren()
	{
		if(myCachedChildren == null)
		{
			switchToAllLeavesTogether(myRootUsage);
		}
		return myCachedChildren;
	}

	@Nonnull
	@Override
	public List<? extends AbstractTreeNode> getChildrenUnderProgress(@Nonnull ProgressIndicator progress)
	{
		return (List<? extends AbstractTreeNode>) getChildren();
	}

	@Override
	protected boolean shouldUpdateData()
	{
		return super.shouldUpdateData();
	}

	@Override
	protected void update(PresentationData presentation)
	{
		if(presentation != null)
		{
			presentation.setChanged(presentation.isChanged() || changed);
			changed = false;
		}
	}


	@Override
	public void customizeCellRenderer(@Nonnull SliceUsageCellRenderer renderer,
			@Nonnull JTree tree,
			Object value,
			boolean selected,
			boolean expanded,
			boolean leaf,
			int row,
			boolean hasFocus)
	{
	}

	public SliceUsage getRootUsage()
	{
		return myRootUsage;
	}
}
