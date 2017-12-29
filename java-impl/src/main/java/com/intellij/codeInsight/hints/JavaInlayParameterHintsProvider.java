package com.intellij.codeInsight.hints;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;

/**
 * from kotlin
 */
public class JavaInlayParameterHintsProvider implements InlayParameterHintsProvider
{
	private static String[] ourDefaultBlackList = {
			"(begin*, end*)",
			"(start*, end*)",
			"(first*, last*)",
			"(first*, second*)",
			"(from*, to*)",
			"(min*, max*)",
			"(key, value)",
			"(format, arg*)",
			"(message)",
			"(message, error)",
			"*Exception",
			"*.add(*)",
			"*.set(*,*)",
			"*.get(*)",
			"*.create(*)",
			"*.getProperty(*)",
			"*.setProperty(*,*)",
			"*.print(*)",
			"*.println(*)",
			"*.append(*)",
			"*.charAt(*)",
			"*.indexOf(*)",
			"*.contains(*)",
			"*.startsWith(*)",
			"*.endsWith(*)",
			"*.equals(*)",
			"*.equal(*)",
			"java.lang.Math.*",
			"org.slf4j.Logger.*"
	};

	@NotNull
	@Override
	public List<InlayInfo> getParameterHints(@NotNull PsiElement element)
	{
		if(element instanceof PsiCallExpression)
		{
			return ContainerUtil.newArrayList(JavaInlayHintsProvider.createHints((PsiCallExpression) element));
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public MethodInfo getMethodInfo(@NotNull PsiElement element)
	{
		if(element instanceof PsiCallExpression)
		{
			PsiElement resolvedElement = ((PsiCallExpression) element).resolveMethodGenerics().getElement();
			if(resolvedElement instanceof PsiMethod)
			{
				return getMethodInfo(resolvedElement);
			}
		}
		return null;
	}

	@NotNull
	@Override
	public Set<String> getDefaultBlackList()
	{
		return ContainerUtil.newHashSet(ourDefaultBlackList);
	}
}
