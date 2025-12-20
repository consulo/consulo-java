/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.inferNullity;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.SequentialModalProgressTask;
import consulo.application.progress.SequentialTask;
import consulo.project.Project;
import consulo.usage.UsageInfo;

class AnnotateTask implements SequentialTask
{
	private final Project myProject;
	private final UsageInfo[] myInfos;
	private final SequentialModalProgressTask myTask;
	private int myCount;
	private final int myTotal;
	private final NullableNotNullManager myNotNullManager;

	public AnnotateTask(Project project, SequentialModalProgressTask progressTask, UsageInfo[] infos)
	{
		myProject = project;
		myInfos = infos;
		myNotNullManager = NullableNotNullManager.getInstance(myProject);
		myTask = progressTask;
		myTotal = infos.length;
	}

	@Override
	public void prepare()
	{
	}

	@Override
	public boolean isDone()
	{
		return myCount > myTotal - 1;
	}

	@Override
	public boolean iteration()
	{
		ProgressIndicator indicator = myTask.getIndicator();
		if(indicator != null)
		{
			indicator.setFraction(((double) myCount) / myTotal);
		}

		NullityInferrer.apply(myProject, myNotNullManager, myInfos[myCount++]);

		return isDone();
	}

	@Override
	public void stop()
	{
	}
}