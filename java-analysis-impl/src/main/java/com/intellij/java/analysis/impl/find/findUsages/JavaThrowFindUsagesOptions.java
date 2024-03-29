/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.find.findUsages;

import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
public class JavaThrowFindUsagesOptions extends JavaFindUsagesOptions
{
	private ThrowSearchUtil.Root root;

	public JavaThrowFindUsagesOptions(@Nonnull Project project)
	{
		super(project);
		isSearchForTextOccurrences = false;
	}

	public ThrowSearchUtil.Root getRoot()
	{
		return root;
	}

	public void setRoot(ThrowSearchUtil.Root root)
	{
		this.root = root;
	}
}
