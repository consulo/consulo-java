/*
 * Copyright 2013-2018 consulo.io
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

package consulo.java.jam.impl;

import jakarta.inject.Singleton;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.jam.model.util.JamCommonUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.jam.util.JamCommonService;

/**
 * @author VISTALL
 * @since 2018-06-21
 */
@Singleton
public class JamCommonServiceImpl implements JamCommonService
{
	@RequiredReadAction
	@Override
	public boolean isPlainJavaFile(PsiElement element)
	{
		return JamCommonUtil.isKindOfJavaFile(element) && ((PsiFile) element).getFileType() == JavaFileType.INSTANCE; // prevent jsp processing
	}

	@RequiredReadAction
	@Override
	public boolean isPlainXmlFile(PsiElement element)
	{
		return element instanceof XmlFile && element.getLanguage() == JavaLanguage.INSTANCE;
	}
}
