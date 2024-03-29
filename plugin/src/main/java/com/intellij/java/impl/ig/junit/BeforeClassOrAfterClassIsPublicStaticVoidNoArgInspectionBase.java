// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.junit;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

import java.util.Arrays;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase extends BaseInspection
{
	private static final String[] STATIC_CONFIGS = {
			"org.junit.BeforeClass",
			"org.junit.AfterClass",
			"org.junit.jupiter.api.BeforeAll",
			"org.junit.jupiter.api.AfterAll"
	};

	protected static boolean isJunit4Annotation(String annotation)
	{
		return annotation.endsWith("Class");
	}

	@Override
	@Nonnull
	public String getID()
	{
		return "BeforeOrAfterWithIncorrectSignature";
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionGadgetsBundle.message("before.class.or.after.class.is.public.static.void.no.arg.display.name");
	}

	@Override
	@Nonnull
	protected String buildErrorString(Object... infos)
	{
		return InspectionGadgetsBundle.message("before.class.or.after.class.is.public.static.void.no.arg.problem.descriptor", infos[1]);
	}

	@Override
	public BaseInspectionVisitor buildVisitor()
	{
		return new BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor();
	}

	private static class BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor extends BaseInspectionVisitor
	{

		@Override
		public void visitMethod(@Nonnull PsiMethod method)
		{
			//note: no call to super;
			String annotation = Arrays.stream(STATIC_CONFIGS).filter(anno -> AnnotationUtil.isAnnotated(method, anno, CHECK_HIERARCHY)).findFirst().orElse(null);
			if(annotation == null)
			{
				return;
			}
			final PsiType returnType = method.getReturnType();
			if(returnType == null)
			{
				return;
			}
			final PsiClass targetClass = method.getContainingClass();
			if(targetClass == null)
			{
				return;
			}

			final PsiParameterList parameterList = method.getParameterList();
			boolean junit4Annotation = isJunit4Annotation(annotation);
			if(junit4Annotation && (parameterList.getParametersCount() != 0 || !method.hasModifierProperty(PsiModifier.PUBLIC)) || !returnType.equals(PsiType.VOID) || !method.hasModifierProperty
					(PsiModifier.STATIC) && (junit4Annotation || !TestUtils.testInstancePerClass(targetClass)))
			{
				registerMethodError(method, method, annotation);
			}
		}
	}
}
