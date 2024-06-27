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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.java.impl.codeInspection.ex;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.ex.EntryPointsManager;
import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefField;
import com.intellij.java.analysis.codeInspection.reference.RefJavaManager;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.content.scope.SearchScope;
import consulo.ide.impl.idea.codeInspection.ex.GlobalInspectionContextImpl;
import consulo.ide.impl.idea.codeInspection.ui.InspectionToolPresentation;
import consulo.language.editor.impl.inspection.scheme.GlobalInspectionToolWrapper;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.inspection.scheme.JobDescriptor;
import consulo.language.editor.inspection.scheme.Tools;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.resolve.PsiReferenceProcessor;
import consulo.language.psi.resolve.PsiReferenceProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.*;

public class GlobalJavaInspectionContextImpl extends GlobalJavaInspectionContext
{
	private static final Logger LOG = Logger.getInstance(GlobalJavaInspectionContextImpl.class);

	private Map<SmartPsiElementPointer, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
	private Map<SmartPsiElementPointer, List<DerivedClassesProcessor>> myDerivedClassesRequests;
	private Map<SmartPsiElementPointer, List<UsagesProcessor>> myMethodUsagesRequests;
	private Map<SmartPsiElementPointer, List<UsagesProcessor>> myFieldUsagesRequests;
	private Map<SmartPsiElementPointer, List<UsagesProcessor>> myClassUsagesRequests;


	@Override
	public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p)
	{
		if (myClassUsagesRequests == null)
		{
			myClassUsagesRequests = new HashMap<>();
		}
		enqueueRequestImpl(refClass, myClassUsagesRequests, p);

	}

	@Override
	public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p)
	{
		if (myDerivedClassesRequests == null)
		{
			myDerivedClassesRequests = new HashMap<>();
		}
		enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
	}

	@Override
	public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p)
	{
		if (refMethod.isConstructor() || refMethod.isStatic())
		{
			return;
		}
		if (myDerivedMethodsRequests == null)
		{
			myDerivedMethodsRequests = new HashMap<>();
		}
		enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
	}

	@Override
	public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p)
	{
		if (myFieldUsagesRequests == null)
		{
			myFieldUsagesRequests = new HashMap<>();
		}
		enqueueRequestImpl(refField, myFieldUsagesRequests, p);
	}

	@Override
	public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p)
	{
		if (myMethodUsagesRequests == null)
		{
			myMethodUsagesRequests = new HashMap<>();
		}
		enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
	}

	@Override
	public EntryPointsManager getEntryPointsManager(final RefManager manager)
	{
		return manager.getExtension(RefJavaManager.MANAGER).getEntryPointsManager();
	}

	@RequiredReadAction
	@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
	public static boolean isInspectionsEnabled(final boolean online, @Nonnull Project project)
	{
		final Module[] modules = ModuleManager.getInstance(project).getModules();
		if (online)
		{
			if (modules.length == 0)
			{
				Messages.showMessageDialog(
					project,
					InspectionLocalize.inspectionNoModulesErrorMessage().get(),
					CommonLocalize.titleError().get(),
					UIUtil.getErrorIcon()
				);
				return false;
			}
			while (isBadSdk(project, modules))
			{
				Messages.showMessageDialog(
					project,
					InspectionLocalize.inspectionNoJdkErrorMessage().get(),
					CommonLocalize.titleError().get(),
					UIUtil.getErrorIcon()
				);
				final Sdk projectJdk = null;
				if (projectJdk == null)
				{
					return false;
				}
			}
		}
		else
		{
			if (modules.length == 0)
			{
				System.err.println(InspectionLocalize.inspectionNoModulesErrorMessage().get());
				return false;
			}
			if (isBadSdk(project, modules))
			{
				System.err.println(InspectionLocalize.inspectionNoJdkErrorMessage().get());
				System.err.println(InspectionLocalize.offlineInspectionsJdkNotFound("").get());
				return false;
			}
			for (Module module : modules)
			{
				final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
				final OrderEntry[] entries = rootManager.getOrderEntries();
				for (OrderEntry entry : entries)
				{
					if (entry instanceof ModuleExtensionWithSdkOrderEntry sdkOrderEntry)
					{
						if (/*!ModuleType.get(module).isValidSdk(module, null)*/Boolean.FALSE)
						{
							System.err.println(InspectionLocalize.offlineInspectionsModuleJdkNotFound(sdkOrderEntry.getSdkName(), module.getName()));
							return false;
						}
					}
					else if (entry instanceof LibraryOrderEntry libraryOrderEntry)
					{
						final Library library = libraryOrderEntry.getLibrary();
						if (library == null
							|| library.getFiles(BinariesOrderRootType.getInstance()).length < library.getUrls(BinariesOrderRootType.getInstance()).length)
						{
							System.err.println(
								InspectionLocalize.offlineInspectionsLibraryWasNotResolved(libraryOrderEntry.getPresentableName(), module.getName()).get()
							);
						}
					}
				}
			}
		}
		return true;
	}

	private static boolean isBadSdk(final Project project, final Module[] modules)
	{
   /* boolean anyModuleAcceptsSdk = false;
	boolean anyModuleUsesProjectSdk = false;
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    for (Module module : modules) {
      if (ModuleRootManager.getInstance(module).isSdkInherited()) {
        anyModuleUsesProjectSdk = true;
        /*if (ModuleType.get(module).isValidSdk(module, projectSdk)) {
          anyModuleAcceptsSdk = true;
        }
      }
    } */
		return false;
	}

	private static <T extends Processor> void enqueueRequestImpl(
		RefElement refElement,
		Map<SmartPsiElementPointer, List<T>> requestMap,
		T processor
	)
	{
		List<T> requests = requestMap.get(refElement.getPointer());
		if (requests == null)
		{
			requests = new ArrayList<>();
			requestMap.put(refElement.getPointer(), requests);
		}
		requests.add(processor);
	}

	@Override
	public void cleanup()
	{
		myDerivedMethodsRequests = null;
		myDerivedClassesRequests = null;
		myMethodUsagesRequests = null;
		myFieldUsagesRequests = null;
		myClassUsagesRequests = null;
	}

	public void processSearchRequests(final GlobalInspectionContext context)
	{
		final RefManager refManager = context.getRefManager();
		final AnalysisScope scope = refManager.getScope();

		final SearchScope searchScope = new GlobalSearchScope(refManager.getProject())
		{
			@Override
			public boolean contains(@Nonnull VirtualFile file)
			{
				return !scope.contains(file) || file.getFileType() != JavaFileType.INSTANCE;
			}

			@Override
			public int compare(@Nonnull VirtualFile file1, @Nonnull VirtualFile file2)
			{
				return 0;
			}

			@Override
			public boolean isSearchInModuleContent(@Nonnull Module aModule)
			{
				return true;
			}

			@Override
			public boolean isSearchInLibraries()
			{
				return false;
			}
		};

		if (myDerivedClassesRequests != null)
		{
			final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedClassesRequests);
			for (SmartPsiElementPointer sortedID : sortedIDs)
			{
				final PsiClass psiClass = (PsiClass) dereferenceInReadAction(sortedID);
				if (psiClass == null)
				{
					continue;
				}
				context.incrementJobDoneAmount(
					context.getStdJobDescriptors().FIND_EXTERNAL_USAGES,
					Application.get().runReadAction(
						new Computable<>()
						{
							@Override
							public String compute()
							{
								return psiClass.getQualifiedName();
							}
						}
					)
				);

				final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(sortedID);
				LOG.assertTrue(processors != null, psiClass.getClass().getName());
				ClassInheritorsSearch.search(psiClass, searchScope, false)
					.forEach(createMembersProcessor(processors, scope));
			}

			myDerivedClassesRequests = null;
		}

		if (myDerivedMethodsRequests != null)
		{
			final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
			for (SmartPsiElementPointer sortedID : sortedIDs)
			{
				final PsiMethod psiMethod = (PsiMethod) dereferenceInReadAction(sortedID);
				if (psiMethod == null)
				{
					continue;
				}
				final RefMethod refMethod = (RefMethod) refManager.getReference(psiMethod);

				context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refMethod));

				final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(sortedID);
				LOG.assertTrue(processors != null, psiMethod.getClass().getName());
				OverridingMethodsSearch.search(psiMethod, searchScope, true)
						.forEach(createMembersProcessor(processors, scope));
			}

			myDerivedMethodsRequests = null;
		}

		if (myFieldUsagesRequests != null)
		{
			final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myFieldUsagesRequests);
			for (SmartPsiElementPointer sortedID : sortedIDs)
			{
				final PsiField psiField = (PsiField) dereferenceInReadAction(sortedID);
				if (psiField == null)
				{
					continue;
				}
				final List<UsagesProcessor> processors = myFieldUsagesRequests.get(sortedID);

				LOG.assertTrue(processors != null, psiField.getClass().getName());
				context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(psiField)));

				ReferencesSearch.search(psiField, searchScope, false)
					.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
			}

			myFieldUsagesRequests = null;
		}

		if (myClassUsagesRequests != null)
		{
			final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myClassUsagesRequests);
			for (SmartPsiElementPointer sortedID : sortedIDs)
			{
				final PsiClass psiClass = (PsiClass) dereferenceInReadAction(sortedID);
				if (psiClass == null)
				{
					continue;
				}
				final List<UsagesProcessor> processors = myClassUsagesRequests.get(sortedID);

				LOG.assertTrue(processors != null, psiClass.getClass().getName());
				context.incrementJobDoneAmount(
					context.getStdJobDescriptors().FIND_EXTERNAL_USAGES,
					Application.get().runReadAction(
						new Computable<>()
						{
							@Override
							public String compute()
							{
								return psiClass.getQualifiedName();
							}
						}
					)
				);

				ReferencesSearch.search(psiClass, searchScope, false)
					.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
			}

			myClassUsagesRequests = null;
		}

		if (myMethodUsagesRequests != null)
		{
			List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myMethodUsagesRequests);
			for (SmartPsiElementPointer sortedID : sortedIDs)
			{
				final PsiMethod psiMethod = (PsiMethod) dereferenceInReadAction(sortedID);
				if (psiMethod == null)
				{
					continue;
				}
				final List<UsagesProcessor> processors = myMethodUsagesRequests.get(sortedID);

				LOG.assertTrue(processors != null, psiMethod.getClass().getName());
				context.incrementJobDoneAmount(
					context.getStdJobDescriptors().FIND_EXTERNAL_USAGES,
					refManager.getQualifiedName(refManager.getReference(psiMethod))
				);

				MethodReferencesSearch.search(psiMethod, searchScope, true)
					.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
			}

			myMethodUsagesRequests = null;
		}
	}

	@RequiredReadAction
	private static PsiElement dereferenceInReadAction(final SmartPsiElementPointer sortedID)
	{
		return Application.get().runReadAction((Computable<PsiElement>)() -> sortedID.getElement());
	}

	private static <Member extends PsiMember, P extends Processor<Member>>
	PsiElementProcessorAdapter<Member> createMembersProcessor(final List<P> processors, final AnalysisScope scope)
	{
		return new PsiElementProcessorAdapter<>((PsiElementProcessor<Member>)member -> {
			if (scope.contains(member))
			{
				return true;
			}
			final List<P> processorsArrayed = new ArrayList<>(processors);
			for (P processor : processorsArrayed)
			{
				if (!processor.process(member))
				{
					processors.remove(processor);
				}
			}
			return !processors.isEmpty();
		});
	}

	private int getRequestCount()
	{
		int sum = 0;

		sum += getRequestListSize(myClassUsagesRequests);
		sum += getRequestListSize(myDerivedClassesRequests);
		sum += getRequestListSize(myDerivedMethodsRequests);
		sum += getRequestListSize(myFieldUsagesRequests);
		sum += getRequestListSize(myMethodUsagesRequests);

		return sum;
	}

	private static int getRequestListSize(Map<?, ?> list)
	{
		if (list == null)
		{
			return 0;
		}
		return list.size();
	}

	private static List<SmartPsiElementPointer> getSortedIDs(final Map<SmartPsiElementPointer, ?> requests)
	{
		final List<SmartPsiElementPointer> result = new ArrayList<>();

		Application.get().runReadAction(() -> {
			for (SmartPsiElementPointer id : requests.keySet())
			{
				if (id != null)
				{
					final PsiElement psi = id.getElement();
					if (psi != null)
					{
						result.add(id);
					}
				}
			}
			Collections.sort(result, (o1, o2) -> {
				PsiElement p1 = o1.getElement();
				PsiElement p2 = o2.getElement();
				final PsiFile psiFile1 = p1 != null ? p1.getContainingFile() : null;
				LOG.assertTrue(psiFile1 != null);
				final PsiFile psiFile2 = p2 != null ? p2.getContainingFile() : null;
				LOG.assertTrue(psiFile2 != null);
				return psiFile1.getName().compareTo(psiFile2.getName());
			});
		});

		return result;
	}

	private static PsiReferenceProcessor createReferenceProcessor(
		@Nonnull final List<UsagesProcessor> processors,
		final GlobalInspectionContext context
	)
	{
		return reference -> {
			AnalysisScope scope = context.getRefManager().getScope();
			if (scope.contains(reference.getElement()) && reference.getElement().getLanguage() == JavaLanguage.INSTANCE
				|| PsiTreeUtil.getParentOfType(reference.getElement(), PsiDocComment.class) != null)
			{
				return true;
			}

			synchronized (processors)
			{
				UsagesProcessor[] processorsArrayed = processors.toArray(new UsagesProcessor[processors.size()]);
				for (UsagesProcessor processor : processorsArrayed)
				{
					if (!processor.process(reference))
					{
						processors.remove(processor);
					}
				}
			}

			return !processors.isEmpty();
		};
	}

	@Override
	public void performPreRunActivities(
		@Nonnull final List<Tools> globalTools,
		@Nonnull final List<Tools> localTools,
		@Nonnull final GlobalInspectionContext context
	)
	{
		getEntryPointsManager(context.getRefManager()).resolveEntryPoints(context.getRefManager());
		// UnusedDeclarationInspection should run first
		for (int i = 0; i < globalTools.size(); i++)
		{
			InspectionToolWrapper toolWrapper = globalTools.get(i).getTool();
			if (UnusedDeclarationInspection.SHORT_NAME.equals(toolWrapper.getShortName()))
			{
				Collections.swap(globalTools, i, 0);
				break;
			}
		}
	}


	@Override
	public void performPostRunActivities(@Nonnull List<InspectionToolWrapper> needRepeatSearchRequest, @Nonnull final GlobalInspectionContext context)
	{
		JobDescriptor progress = context.getStdJobDescriptors().FIND_EXTERNAL_USAGES;
		progress.setTotalAmount(getRequestCount());

		do
		{
			processSearchRequests(context);
			InspectionToolWrapper[] requestors = needRepeatSearchRequest.toArray(new InspectionToolWrapper[needRepeatSearchRequest.size()]);
			InspectionManager inspectionManager = InspectionManager.getInstance(context.getProject());
			for (InspectionToolWrapper toolWrapper : requestors)
			{
				boolean result = false;
				if (toolWrapper instanceof GlobalInspectionToolWrapper globalInspectionToolWrapper)
				{
					InspectionToolPresentation presentation = ((GlobalInspectionContextImpl) context).getPresentation(toolWrapper);
					Object state = globalInspectionToolWrapper.getState();
					result = globalInspectionToolWrapper.getTool().queryExternalUsagesRequests(inspectionManager, context, presentation, state);
				}
				if (!result)
				{
					needRepeatSearchRequest.remove(toolWrapper);
				}
			}
			int oldSearchRequestCount = progress.getTotalAmount();
			int oldDoneAmount = progress.getDoneAmount();
			int totalAmount = oldSearchRequestCount + getRequestCount();
			progress.setTotalAmount(totalAmount);
			progress.setDoneAmount(oldDoneAmount);
		}
		while (!needRepeatSearchRequest.isEmpty());
	}

}
