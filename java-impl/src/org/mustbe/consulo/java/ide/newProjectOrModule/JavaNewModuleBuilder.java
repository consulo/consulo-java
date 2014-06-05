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

package org.mustbe.consulo.java.ide.newProjectOrModule;

import org.consulo.java.module.extension.JavaMutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.ide.impl.NewModuleBuilder;
import org.mustbe.consulo.ide.impl.NewModuleContext;
import org.mustbe.consulo.ide.impl.UnzipNewModuleBuilderProcessor;
import org.mustbe.consulo.java.JavaIcons;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author VISTALL
 * @since 05.06.14
 */
public class JavaNewModuleBuilder implements NewModuleBuilder
{
	@Override
	public void setupContext(@NotNull NewModuleContext context)
	{
		context.addItem("#Java", "Java", JavaIcons.Java);
		context.addItem("#JavaHelloWorld", "Hello World", AllIcons.RunConfigurations.Application);

		context.setupItem(new String[]{
				"#Java",
				"#JavaHelloWorld"
		}, new UnzipNewModuleBuilderProcessor<JavaNewModuleBuilderPanel>("/moduleTemplates/#JavaHelloWorld.zip")
		{
			@NotNull
			@Override
			public JavaNewModuleBuilderPanel createConfigurationPanel()
			{
				return new JavaNewModuleBuilderPanel();
			}

			@Override
			public void setupModule(
					@NotNull JavaNewModuleBuilderPanel panel, @NotNull ContentEntry contentEntry, @NotNull ModifiableRootModel modifiableRootModel)
			{
				unzip(modifiableRootModel);

				// need get by id - due, extension can be from original Java impl, or from other plugin, like IKVM.NET
				JavaMutableModuleExtension<?> javaMutableModuleExtension = modifiableRootModel.getExtensionWithoutCheck("java");
				assert javaMutableModuleExtension != null;

				javaMutableModuleExtension.setEnabled(true);

				Sdk sdk = panel.getSdk();
				if(sdk != null)
				{
					javaMutableModuleExtension.getInheritableSdk().set(null, sdk);
					modifiableRootModel.addModuleExtensionSdkEntry(javaMutableModuleExtension);

					JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
					if(version != null)
					{
						javaMutableModuleExtension.getInheritableLanguageLevel().set(null, version.getMaxLanguageLevel());
					}
				}
				contentEntry.addFolder(contentEntry.getUrl() + "/src", ProductionContentFolderTypeProvider.getInstance());
			}
		});
	}
}
