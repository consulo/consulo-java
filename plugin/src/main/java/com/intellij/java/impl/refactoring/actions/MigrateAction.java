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
package com.intellij.java.impl.refactoring.actions;

import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.project.Project;
import com.intellij.java.impl.refactoring.RefactoringManager;
import consulo.ui.annotation.RequiredUIAccess;

public class MigrateAction extends AnAction
{
	@RequiredUIAccess
	@Override
	public void actionPerformed(AnActionEvent e)
	{
		Project project = e.getProject();
		RefactoringManager.getInstance(project).getMigrateManager().showMigrationDialog();
	}

	@RequiredUIAccess
	@Override
	public void update(AnActionEvent event)
	{
		Presentation presentation = event.getPresentation();
		Project project = event.getProject();
		presentation.setEnabled(project != null);
	}
}
