/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import consulo.disposer.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.JavaQuickFixBundle;
import consulo.java.codeInspection.JavaExtensionPoints;

public abstract class EntryPointsManagerBase extends EntryPointsManager implements PersistentStateComponent<Element>
{
	@NonNls
	private static final String[] STANDARD_ANNOS = {
			"javax.ws.rs.*",
	};

	// null means uninitialized
	private volatile List<String> ADDITIONAL_ANNOS;

	public Collection<String> getAdditionalAnnotations()
	{
		List<String> annos = ADDITIONAL_ANNOS;
		if(annos == null)
		{
			annos = new ArrayList<String>();
			Collections.addAll(annos, STANDARD_ANNOS);
			for(EntryPoint extension : JavaExtensionPoints.DEAD_CODE_EP_NAME.getExtensions())
			{
				final String[] ignoredAnnotations = extension.getIgnoreAnnotations();
				if(ignoredAnnotations != null)
				{
					ContainerUtil.addAll(annos, ignoredAnnotations);
				}
			}
			ADDITIONAL_ANNOS = annos = Collections.unmodifiableList(annos);
		}
		return annos;
	}

	public JDOMExternalizableStringList ADDITIONAL_ANNOTATIONS = new JDOMExternalizableStringList();
	private final Map<String, SmartRefElementPointer> myPersistentEntryPoints;
	private final Set<RefElement> myTemporaryEntryPoints;
	private static final String VERSION = "2.0";
	@NonNls
	private static final String VERSION_ATTR = "version";
	@NonNls
	private static final String ENTRY_POINT_ATTR = "entry_point";
	private boolean myAddNonJavaEntries = true;
	private boolean myResolved = false;
	protected final Project myProject;
	private long myLastModificationCount = -1;

	public EntryPointsManagerBase(Project project)
	{
		myProject = project;
		myTemporaryEntryPoints = new HashSet<RefElement>();
		myPersistentEntryPoints = new LinkedHashMap<String, SmartRefElementPointer>(); // To keep the order between readExternal to writeExternal
		Disposer.register(project, this);
	}

	public static EntryPointsManagerBase getInstance(Project project)
	{
		return (EntryPointsManagerBase) ServiceManager.getService(project, EntryPointsManager.class);
	}

	@Override
	public void loadState(Element element)
	{
		Element entryPointsElement = element.getChild("entry_points");
		if(entryPointsElement != null)
		{
			final String version = entryPointsElement.getAttributeValue(VERSION_ATTR);
			if(!Comparing.strEqual(version, VERSION))
			{
				convert(entryPointsElement, myPersistentEntryPoints);
			}
			else
			{
				List content = entryPointsElement.getChildren();
				for(final Object aContent : content)
				{
					Element entryElement = (Element) aContent;
					if(ENTRY_POINT_ATTR.equals(entryElement.getName()))
					{
						SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(entryElement);
						myPersistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
					}
				}
			}
		}
		try
		{
			ADDITIONAL_ANNOTATIONS.readExternal(element);
		}
		catch(InvalidDataException ignored)
		{
		}
	}

	@Override
	public Element getState()
	{
		if(myPersistentEntryPoints.isEmpty())
		{
			return null;
		}

		Element element = new Element("state");
		writeExternal(element, myPersistentEntryPoints, ADDITIONAL_ANNOTATIONS);
		return element;
	}

	public static void writeExternal(final Element element, final Map<String, SmartRefElementPointer> persistentEntryPoints, final JDOMExternalizableStringList additional_annotations)
	{
		Element entryPointsElement = new Element("entry_points");
		entryPointsElement.setAttribute(VERSION_ATTR, VERSION);
		for(SmartRefElementPointer entryPoint : persistentEntryPoints.values())
		{
			assert entryPoint.isPersistent();
			entryPoint.writeExternal(entryPointsElement);
		}

		element.addContent(entryPointsElement);
		if(!additional_annotations.isEmpty())
		{
			additional_annotations.writeExternal(element);
		}
	}

	@Override
	public void resolveEntryPoints(@Nonnull final RefManager manager)
	{
		if(!myResolved)
		{
			myResolved = true;
			cleanup();
			validateEntryPoints();

			ApplicationManager.getApplication().runReadAction(new Runnable()
			{
				@Override
				public void run()
				{
					for(SmartRefElementPointer entryPoint : myPersistentEntryPoints.values())
					{
						if(entryPoint.resolve(manager))
						{
							RefEntity refElement = entryPoint.getRefElement();
							((RefElementImpl) refElement).setEntry(true);
							((RefElementImpl) refElement).setPermanentEntry(entryPoint.isPersistent());
						}
					}
				}
			});
		}
	}

	private void purgeTemporaryEntryPoints()
	{
		for(RefElement entryPoint : myTemporaryEntryPoints)
		{
			((RefElementImpl) entryPoint).setEntry(false);
		}

		myTemporaryEntryPoints.clear();
	}

	@Override
	public void addEntryPoint(@Nonnull RefElement newEntryPoint, boolean isPersistent)
	{
		if(!newEntryPoint.isValid())
		{
			return;
		}
		if(newEntryPoint instanceof RefClass)
		{
			RefClass refClass = (RefClass) newEntryPoint;

			if(refClass.isAnonymous())
			{
				// Anonymous class cannot be an entry point.
				return;
			}

			List<RefMethod> refConstructors = refClass.getConstructors();
			if(refConstructors.size() == 1)
			{
				addEntryPoint(refConstructors.get(0), isPersistent);
				return;
			}
			else if(refConstructors.size() > 1)
			{
				// Many constructors here. Need to ask user which ones are used
				for(int i = 0; i < refConstructors.size(); i++)
				{
					addEntryPoint(refConstructors.get(i), isPersistent);
				}

				return;
			}
		}

		if(!isPersistent)
		{
			myTemporaryEntryPoints.add(newEntryPoint);
			((RefElementImpl) newEntryPoint).setEntry(true);
		}
		else
		{
			if(myPersistentEntryPoints.get(newEntryPoint.getExternalName()) == null)
			{
				final SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, true);
				myPersistentEntryPoints.put(entry.getFQName(), entry);
				((RefElementImpl) newEntryPoint).setEntry(true);
				((RefElementImpl) newEntryPoint).setPermanentEntry(true);
				if(entry.isPersistent())
				{ //do save entry points
					final EntryPointsManager entryPointsManager = getInstance(newEntryPoint.getElement().getProject());
					if(this != entryPointsManager)
					{
						entryPointsManager.addEntryPoint(newEntryPoint, true);
					}
				}
			}
		}
	}

	@Override
	public void removeEntryPoint(@Nonnull RefElement anEntryPoint)
	{
		if(anEntryPoint instanceof RefClass)
		{
			RefClass refClass = (RefClass) anEntryPoint;
			if(!refClass.isInterface())
			{
				anEntryPoint = refClass.getDefaultConstructor();
			}
		}

		if(anEntryPoint == null)
		{
			return;
		}

		myTemporaryEntryPoints.remove(anEntryPoint);

		Set<Map.Entry<String, SmartRefElementPointer>> set = myPersistentEntryPoints.entrySet();
		String key = null;
		for(Map.Entry<String, SmartRefElementPointer> entry : set)
		{
			SmartRefElementPointer value = entry.getValue();
			if(value.getRefElement() == anEntryPoint)
			{
				key = entry.getKey();
				break;
			}
		}

		if(key != null)
		{
			myPersistentEntryPoints.remove(key);
			((RefElementImpl) anEntryPoint).setEntry(false);
		}

		if(anEntryPoint.isPermanentEntry() && anEntryPoint.isValid())
		{
			final Project project = anEntryPoint.getElement().getProject();
			final EntryPointsManager entryPointsManager = getInstance(project);
			if(this != entryPointsManager)
			{
				entryPointsManager.removeEntryPoint(anEntryPoint);
			}
		}
	}

	@Nonnull
	@Override
	public RefElement[] getEntryPoints()
	{
		validateEntryPoints();
		List<RefElement> entries = new ArrayList<RefElement>();
		Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
		for(SmartRefElementPointer refElementPointer : collection)
		{
			final RefEntity elt = refElementPointer.getRefElement();
			if(elt instanceof RefElement)
			{
				entries.add((RefElement) elt);
			}
		}
		entries.addAll(myTemporaryEntryPoints);

		return entries.toArray(new RefElement[entries.size()]);
	}

	@Override
	public void dispose()
	{
		cleanup();
	}

	private void validateEntryPoints()
	{
		long count = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
		if(count != myLastModificationCount)
		{
			myLastModificationCount = count;
			Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
			SmartRefElementPointer[] entries = collection.toArray(new SmartRefElementPointer[collection.size()]);
			for(SmartRefElementPointer entry : entries)
			{
				RefElement refElement = (RefElement) entry.getRefElement();
				if(refElement != null && !refElement.isValid())
				{
					myPersistentEntryPoints.remove(entry.getFQName());
				}
			}

			final Iterator<RefElement> it = myTemporaryEntryPoints.iterator();
			while(it.hasNext())
			{
				RefElement refElement = it.next();
				if(!refElement.isValid())
				{
					it.remove();
				}
			}
		}
	}

	@Override
	public void cleanup()
	{
		purgeTemporaryEntryPoints();
		Collection<SmartRefElementPointer> entries = myPersistentEntryPoints.values();
		for(SmartRefElementPointer entry : entries)
		{
			entry.freeReference();
		}
	}

	@Override
	public boolean isAddNonJavaEntries()
	{
		return myAddNonJavaEntries;
	}

	public void addAllPersistentEntries(EntryPointsManagerBase manager)
	{
		myPersistentEntryPoints.putAll(manager.myPersistentEntryPoints);
	}

	public static void convert(Element element, final Map<String, SmartRefElementPointer> persistentEntryPoints)
	{
		List content = element.getChildren();
		for(final Object aContent : content)
		{
			Element entryElement = (Element) aContent;
			if(ENTRY_POINT_ATTR.equals(entryElement.getName()))
			{
				String fqName = entryElement.getAttributeValue(SmartRefElementPointerImpl.FQNAME_ATTR);
				final String type = entryElement.getAttributeValue(SmartRefElementPointerImpl.TYPE_ATTR);
				if(Comparing.strEqual(type, RefJavaManager.METHOD))
				{

					int spaceIdx = fqName.indexOf(' ');
					int lastDotIdx = fqName.lastIndexOf('.');

					int parenIndex = fqName.indexOf('(');

					while(lastDotIdx > parenIndex)
					{
						lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);
					}

					boolean notype = false;
					if(spaceIdx < 0 || spaceIdx + 1 > lastDotIdx || spaceIdx > parenIndex)
					{
						notype = true;
					}

					final String className = fqName.substring(notype ? 0 : spaceIdx + 1, lastDotIdx);
					final String methodSignature = notype ? fqName.substring(lastDotIdx + 1) : fqName.substring(0, spaceIdx) + ' ' + fqName.substring(lastDotIdx + 1);

					fqName = className + " " + methodSignature;
				}
				else if(Comparing.strEqual(type, RefJavaManager.FIELD))
				{
					final int lastDotIdx = fqName.lastIndexOf('.');
					if(lastDotIdx > 0 && lastDotIdx < fqName.length() - 2)
					{
						String className = fqName.substring(0, lastDotIdx);
						String fieldName = fqName.substring(lastDotIdx + 1);
						fqName = className + " " + fieldName;
					}
					else
					{
						continue;
					}
				}
				SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(type, fqName);
				persistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
			}
		}
	}

	public void setAddNonJavaEntries(final boolean addNonJavaEntries)
	{
		myAddNonJavaEntries = addNonJavaEntries;
	}

	@Override
	public boolean isEntryPoint(@Nonnull PsiElement element)
	{
		if(!(element instanceof PsiModifierListOwner))
		{
			return false;
		}
		PsiModifierListOwner owner = (PsiModifierListOwner) element;
		if(!ADDITIONAL_ANNOTATIONS.isEmpty() && ADDITIONAL_ANNOTATIONS.contains(Deprecated.class.getName()) &&
				element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) element).isDeprecated())
		{
			return true;
		}
		return AnnotationUtil.isAnnotated(owner, ADDITIONAL_ANNOTATIONS) || AnnotationUtil.isAnnotated(owner, getAdditionalAnnotations());
	}

	public class AddImplicitlyWriteAnnotation implements IntentionAction
	{
		private final String myQualifiedName;

		public AddImplicitlyWriteAnnotation(String qualifiedName)
		{
			myQualifiedName = qualifiedName;
		}

		@Override
		@Nonnull
		public String getText()
		{
			return JavaQuickFixBundle.message("fix.unused.symbol.injection.text", "fields", myQualifiedName);
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return JavaQuickFixBundle.message("fix.unused.symbol.injection.family");
		}

		@Override
		public boolean isAvailable(@Nonnull Project project1, Editor editor, PsiFile file)
		{
			return true;
		}

		@Override
		public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
		{
			//TODO [VISTALL]
			//myWriteAnnotations.add(myQualifiedName);
			//ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
		}

		@Override
		public boolean startInWriteAction()
		{
			return false;
		}
	}
}
