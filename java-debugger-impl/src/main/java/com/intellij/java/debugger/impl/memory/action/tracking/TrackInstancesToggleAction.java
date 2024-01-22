/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.memory.action.tracking;

import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import com.intellij.java.debugger.impl.memory.tracking.TrackingType;
import com.intellij.java.debugger.impl.memory.ui.ClassesTable;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

public class TrackInstancesToggleAction extends ToggleAction
{
	@Override
	public void update(@Nonnull AnActionEvent e)
	{
		ReferenceType selectedClass = getSelectedClass(e);
		if(selectedClass instanceof ArrayType)
		{
			e.getPresentation().setEnabled(false);
		}
		else
		{
			super.update(e);
		}
	}

	@Override
	public boolean isSelected(AnActionEvent e)
	{
		ReferenceType selectedClass = getSelectedClass(e);
		final Project project = e.getData(Project.KEY);
		if(project != null && selectedClass != null && !project.isDisposed())
		{
			InstancesTracker tracker = InstancesTracker.getInstance(project);
			return tracker.isTracked(selectedClass.name());
		}

		return false;
	}

	@Override
	public void setSelected(AnActionEvent e, boolean state)
	{
		final ReferenceType selectedClass = getSelectedClass(e);
		final Project project = e.getData(Project.KEY);
		if(selectedClass != null && project != null && !project.isDisposed())
		{
			InstancesTracker tracker = InstancesTracker.getInstance(project);
			boolean isAlreadyTracked = tracker.isTracked(selectedClass.name());

			if(isAlreadyTracked && !state)
			{
				tracker.remove(selectedClass.name());
			}

			if(!isAlreadyTracked && state)
			{
				tracker.add(selectedClass.name(), TrackingType.CREATION);
			}
		}
	}

	@Nullable
	private static ReferenceType getSelectedClass(AnActionEvent e)
	{
		return e.getData(ClassesTable.SELECTED_CLASS_KEY);
	}
}
