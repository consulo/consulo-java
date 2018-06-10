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

/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import javax.annotation.Nonnull;

import com.intellij.core.JavaCoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import consulo.ui.image.Image;

public class AnnotationsOrderRootTypeUIFactory implements OrderRootTypeUIFactory
{
	@Nonnull
	@Override
	public Image getIcon()
	{
		return AllIcons.Modules.Annotation;
	}

	@Nonnull
	@Override
	public String getNodeText()
	{
		return JavaCoreBundle.message("sdk.configure.external.annotations.tab");
	}

	@Nonnull
	@Override
	public SdkPathEditor createPathEditor(Sdk sdk)
	{
		return new SdkPathEditor(JavaCoreBundle.message("sdk.configure.external.annotations.tab"), AnnotationOrderRootType.getInstance(), new FileChooserDescriptor(false, true, true, false, true, false), sdk);
	}
}
