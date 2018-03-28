/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.jdom.Element;
import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;

@State(name = "NullableNotNullManager", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/misc.xml"))
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>
{
	public static final List<String> DEFAULT_NOT_NULLS = Arrays.asList(
			Nonnull.class.getName(), "javax.annotation.Nonnull",
			"edu.umd.cs.findbugs.annotations.NonNull", "android.support.annotation.NonNull"
	);

	public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";

	public NullableNotNullManagerImpl(Project project)
	{
		super(project);
		myNotNulls.addAll(getPredefinedNotNulls());
	}

	@Override
	public List<String> getPredefinedNotNulls()
	{
		return DEFAULT_NOT_NULLS;
	}

	@Override
	protected boolean hasHardcodedContracts(PsiElement element)
	{
		return HardcodedContracts.hasHardcodedContracts(element);
	}


	@SuppressWarnings("deprecation")
	@Override
	public Element getState()
	{
		final Element component = new Element("component");

		if(hasDefaultValues())
		{
			return component;
		}

		try
		{
			DefaultJDOMExternalizer.writeExternal(this, component);
		}
		catch(WriteExternalException e)
		{
			LOG.error(e);
		}
		return component;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void loadState(Element state)
	{
		try
		{
			DefaultJDOMExternalizer.readExternal(this, state);
			if(myNullables.isEmpty())
			{
				Collections.addAll(myNullables, DEFAULT_NULLABLES);
			}
			if(myNotNulls.isEmpty())
			{
				myNotNulls.addAll(getPredefinedNotNulls());
			}
		}
		catch(InvalidDataException e)
		{
			LOG.error(e);
		}
	}

	private List<PsiClass> getAllNullabilityNickNames()
	{
		if(!getNotNulls().contains(JAVAX_ANNOTATION_NONNULL))
		{
			return Collections.emptyList();
		}
		return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
		{
			List<PsiClass> result = new ArrayList<>();
			GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
			for(PsiClass tqNick : JavaPsiFacade.getInstance(myProject).findClasses(TYPE_QUALIFIER_NICKNAME, scope))
			{
				result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), candidate ->
				{
					String qname = candidate.getQualifiedName();
					if(qname == null || qname.startsWith("javax.annotation."))
					{
						return false;
					}
					return getNickNamedNullability(candidate) != Nullness.UNKNOWN;
				}));
			}
			return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
		});
	}

	private static Nullness getNickNamedNullability(@Nonnull PsiClass psiClass)
	{
		if(AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null)
		{
			return Nullness.UNKNOWN;
		}

		PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
		return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullness.UNKNOWN;
	}

	@Nonnull
	private static Nullness extractNullityFromWhenValue(PsiAnnotation nonNull)
	{
		PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
		if(when instanceof PsiReferenceExpression)
		{
			String refName = ((PsiReferenceExpression) when).getReferenceName();
			if("ALWAYS".equals(refName))
			{
				return Nullness.NOT_NULL;
			}
			if("MAYBE".equals(refName) || "NEVER".equals(refName))
			{
				return Nullness.NULLABLE;
			}
		}
		return Nullness.UNKNOWN;
	}

	private List<String> filterNickNames(Nullness nullness)
	{
		return StreamEx.of(getAllNullabilityNickNames()).filter(c -> getNickNamedNullability(c) == nullness).map(PsiClass::getQualifiedName).toList();
	}

	@Nonnull
	@Override
	protected List<String> getNullablesWithNickNames()
	{
		return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> CachedValueProvider.Result.create(ContainerUtil.concat(getNullables(), filterNickNames(Nullness.NULLABLE)),
				PsiModificationTracker.MODIFICATION_COUNT));
	}

	@Nonnull
	@Override
	protected List<String> getNotNullsWithNickNames()
	{
		return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> CachedValueProvider.Result.create(ContainerUtil.concat(getNotNulls(), filterNickNames(Nullness.NOT_NULL)),
				PsiModificationTracker.MODIFICATION_COUNT));
	}
}
