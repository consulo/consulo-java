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
package com.intellij.java.debugger.impl.memory.action;

import com.intellij.java.debugger.impl.memory.ui.ClassesTable;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.ReferenceType;
import jakarta.annotation.Nullable;

public abstract class ClassesActionBase extends AnAction
{
	@Override
	public void update(AnActionEvent e)
	{
		e.getPresentation().setEnabled(isEnabled(e));
	}

	@Override
	public void actionPerformed(AnActionEvent e)
	{
		perform(e);
	}

	protected boolean isEnabled(AnActionEvent e)
	{
		final Project project = e.getData(Project.KEY);
		return project != null && !project.isDisposed();
	}

	protected abstract void perform(AnActionEvent e);

	@Nullable
	protected ReferenceType getSelectedClass(AnActionEvent e)
	{
		return e.getData(ClassesTable.SELECTED_CLASS_KEY);
	}
}
