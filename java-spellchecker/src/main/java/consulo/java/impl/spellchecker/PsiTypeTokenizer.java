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
package consulo.java.impl.spellchecker;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDisjunctionType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.spellcheker.tokenizer.TokenConsumer;
import consulo.language.spellcheker.tokenizer.Tokenizer;
import consulo.language.spellcheker.tokenizer.splitter.IdentifierTokenSplitter;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class PsiTypeTokenizer extends Tokenizer<PsiTypeElement>
{
	@Override
	@RequiredReadAction
	public void tokenize(@jakarta.annotation.Nonnull PsiTypeElement element, TokenConsumer consumer)
	{
		final PsiType type = element.getType();
		if(type instanceof PsiDisjunctionType)
		{
			tokenizeComplexType(element, consumer);
			return;
		}

		final PsiClass psiClass = PsiUtil.resolveClassInType(type);

		if(psiClass == null || psiClass.getContainingFile() == null || psiClass.getContainingFile().getVirtualFile() == null)
		{
			return;
		}

		final String name = psiClass.getName();
		if(name == null)
		{
			return;
		}

		final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();

		final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();

		final boolean isInSource = (virtualFile != null) && fileIndex.isInContent(virtualFile);
		if(isInSource)
		{
			final String elementText = element.getText();
			if(elementText.contains(name))
			{
				consumer.consumeToken(element, elementText, true, 0, getRangeToCheck(elementText, name), IdentifierTokenSplitter.getInstance());
			}
		}
	}

	@RequiredReadAction
	private void tokenizeComplexType(PsiTypeElement element, TokenConsumer consumer)
	{
		final List<PsiTypeElement> subTypes = PsiTreeUtil.getChildrenOfTypeAsList(element, PsiTypeElement.class);
		for(PsiTypeElement subType : subTypes)
		{
			tokenize(subType, consumer);
		}
	}

	@jakarta.annotation.Nonnull
	private static TextRange getRangeToCheck(@jakarta.annotation.Nonnull String text, @Nonnull String name)
	{
		final int i = text.indexOf(name);
		return new TextRange(i, i + name.length());
	}
}