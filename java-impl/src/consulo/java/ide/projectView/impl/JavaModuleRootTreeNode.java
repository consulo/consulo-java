/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.ide.projectView.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import consulo.annotations.RequiredReadAction;
import consulo.java.JavaIcons;

/**
 * @author VISTALL
 * @since 10-Jan-17
 */
public class JavaModuleRootTreeNode extends PsiDirectoryNode
{
	public JavaModuleRootTreeNode(Project project, PsiDirectory value, ViewSettings viewSettings)
	{
		super(project, value, viewSettings);
	}

	@Override
	@RequiredReadAction
	protected void updateImpl(PresentationData data)
	{
		super.updateImpl(data);

		PsiDirectory value = getValue();
		if(value == null)
		{
			return;
		}

		PsiFile file = value.findFile(PsiJavaModule.MODULE_INFO_CLS_FILE);
		if(file instanceof PsiJavaFile)
		{
			String name = "INVALID";
			PsiJavaModule moduleDeclaration = ((PsiJavaFile) file).getModuleDeclaration();
			if(moduleDeclaration != null)
			{
				name = StringUtil.notNullize(moduleDeclaration.getModuleName(), name);
			}
			data.setPresentableText(name);
		}
	}

	@Override
	protected void setupIcon(PresentationData data, PsiDirectory psiDirectory)
	{
		data.setIcon(JavaIcons.Nodes.JavaModuleRoot);
	}

	@Override
	public void update(PresentationData data)
	{
		super.update(data);
	}
}
