/*
 * Copyright 2013-2014 must-be.org
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

package org.mustbe.consulo.java.module.extension;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.ModifiableRootModel;
import consulo.module.extension.ModuleExtension;
import consulo.roots.ContentFolderSupportPatcher;
import consulo.roots.ContentFolderTypeProvider;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import consulo.roots.impl.TestContentFolderTypeProvider;
import consulo.roots.impl.TestResourceContentFolderTypeProvider;

/**
 * @author VISTALL
 * @since 05.05.14
 */
public class JavaContentFolderSupportPatcher implements ContentFolderSupportPatcher
{
	@Override
	public void patch(@NotNull ModifiableRootModel model, @NotNull Set<ContentFolderTypeProvider> set)
	{
		ModuleExtension javaModuleExtension = model.getExtension("java");
		if(javaModuleExtension != null)
		{
			set.add(ProductionContentFolderTypeProvider.getInstance());
			set.add(ProductionResourceContentFolderTypeProvider.getInstance());
			set.add(TestContentFolderTypeProvider.getInstance());
			set.add(TestResourceContentFolderTypeProvider.getInstance());
		}
	}
}
