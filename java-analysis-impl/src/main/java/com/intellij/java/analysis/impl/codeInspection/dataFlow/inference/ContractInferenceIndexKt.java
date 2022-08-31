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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.lang.LighterAST;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.PsiFileGist;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * from kotlin
 */
class ContractInferenceIndexKt
{
	private static PsiFileGist<Map<Integer, MethodData>> gist = GistManager.getInstance().newPsiFileGist("javaContractInference", 19, MethodDataExternalizer.INSTANCE, file -> indexFile(file.getNode
			().getLighterAST()));

	@Nonnull
	private static Map<Integer, MethodData> indexFile(LighterAST tree)
	{
		InferenceVisitor visitor = new InferenceVisitor(tree);
		visitor.visitNode(tree.getRoot());
		return visitor.getResult();
	}

	@Nullable
	public static MethodData getIndexedData(PsiMethodImpl method)
	{
		PsiFile file = method.getContainingFile();

		Map<PsiMethod, MethodData> map = CachedValuesManager.getCachedValue(file, () ->
		{
			Map<Integer, MethodData> fileData = gist.getFileData(file);
			Map<PsiMethod, MethodData> result = new HashMap<>();

			if(fileData != null)
			{
				StubbedSpine spine = ((PsiFileImpl) file).getStubbedSpine();
				int methodIndex = 0;

				for(int i = 0; i < spine.getStubCount(); i++)
				{
					if(spine.getStubType(i) == JavaElementType.METHOD)
					{
						MethodData methodData = fileData.get(methodIndex);
						if(methodData != null)
						{
							result.put((PsiMethod) spine.getStubPsi(i), methodData);
						}
						methodIndex ++;
					}
				}
			}

			return CachedValueProvider.Result.create(result, file);
		});

		return map.get(method);
	}
}
