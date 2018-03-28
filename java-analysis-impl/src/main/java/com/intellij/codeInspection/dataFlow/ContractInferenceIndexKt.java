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

package com.intellij.codeInspection.dataFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.PsiFileGist;

/**
 * from kotlin
 */
class ContractInferenceIndexKt
{
	private static PsiFileGist<Map<Integer, MethodData>> ourMethodDataPsiFileGist = GistManager.getInstance().newPsiFileGist("javaContractInference", 2, MethodDataExternalizer.INSTANCE, file ->
			indexFile(file.getNode().getLighterAST()));

	@Nonnull
	private static Map<Integer, MethodData> indexFile(LighterAST tree)
	{
		Map<Integer, MethodData> result = new HashMap<>();

		new RecursiveLighterASTNodeWalkingVisitor(tree)
		{
			int methodIndex;

			@Override
			public void visitNode(@Nonnull LighterASTNode element)
			{
				if(element.getTokenType() == JavaElementType.METHOD)
				{
					MethodData methodData = calcData(tree, element);
					if(methodData != null)
					{
						result.put(methodIndex, methodData);
					}

					methodIndex++;
				}

				if(JavaLightStubBuilder.isCodeBlockWithoutStubs(element))
				{
					return;
				}

				super.visitNode(element);
			}
		}.visitNode(tree.getRoot());
		return result;
	}

	public static MethodData getIndexedData(PsiMethodImpl method)
	{
		Map<Integer, MethodData> map = ourMethodDataPsiFileGist.getFileData(method.getContainingFile());
		return map == null ? null : map.get(methodIndex(method));
	}

	private static int methodIndex(PsiMethodImpl method)
	{
		PsiFileImpl file = (PsiFileImpl) method.getContainingFile();
		StubTree stubTree = null;
		try
		{
			stubTree = file.getStubTree();
			stubTree = stubTree == null ? file.calcStubTree() : stubTree;
		}
		catch(ProcessCanceledException e)
		{
			throw e;
		}
		catch(RuntimeException e)
		{
			throw new RuntimeException("While inferring contract for " + PsiUtil.getMemberQualifiedName(method), e);
		}

		List<PsiElement> elements = new ArrayList<>();
		for(StubElement<?> stubElement : stubTree.getPlainList())
		{
			if(stubElement.getStubType() == JavaElementType.METHOD)
			{
				elements.add(stubElement.getPsi());
			}
		}
		return elements.indexOf(method);
	}

	@javax.annotation.Nullable
	private static MethodData calcData(LighterAST tree, LighterASTNode method)
	{
		LighterASTNode body = LightTreeUtil.firstChildOfType(tree, method, JavaElementType.CODE_BLOCK);
		if(body == null)
		{
			return null;
		}
		List<LighterASTNode> statements = ContractInferenceInterpreter.getStatements(body, tree);
		List<PreContract> contracts = new ContractInferenceInterpreter(tree, method, body).inferContracts(statements);
		NullityInference.NullityInferenceVisitor nullityVisitor = new NullityInference.NullityInferenceVisitor(tree, body);
		PurityInference.PurityInferenceVisitor purityVisitor = new PurityInference.PurityInferenceVisitor(tree, body);

		for(LighterASTNode statement : statements)
		{
			walkMethodBody(tree, statement, it ->
			{
				nullityVisitor.visitNode(it);
				purityVisitor.visitNode(it);
			});
		}
		return createData(body, contracts, nullityVisitor.getResult(), purityVisitor.getResult());
	}

	@javax.annotation.Nullable
	private static MethodData createData(LighterASTNode body, List<PreContract> contracts, NullityInferenceResult nullity, PurityInferenceResult purity)
	{
		if(nullity == null && purity == null && contracts.isEmpty())
		{
			return null;
		}

		return new MethodData(nullity, purity, contracts, body.getStartOffset(), body.getEndOffset());
	}

	private static void walkMethodBody(LighterAST tree, LighterASTNode root, Consumer<LighterASTNode> processor)
	{
		new RecursiveLighterASTNodeWalkingVisitor(tree)
		{
			@Override
			public void visitNode(@Nonnull LighterASTNode element)
			{
				IElementType type = element.getTokenType();
				if(type == JavaElementType.CLASS || type == JavaElementType.FIELD || type == JavaElementType.METHOD || type == JavaElementType.ANNOTATION_METHOD || type == JavaElementType
						.LAMBDA_EXPRESSION)
				{
					return;
				}
				processor.accept(element);
				super.visitNode(element);
			}
		}.visitNode(root);
	}
}
