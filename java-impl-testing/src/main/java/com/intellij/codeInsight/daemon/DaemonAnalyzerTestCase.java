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
package com.intellij.codeInsight.daemon;

import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.Pass;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.language.editor.impl.internal.daemon.DaemonCodeAnalyzerEx;
import consulo.ide.impl.idea.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import consulo.language.editor.rawHighlight.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionManager;
import consulo.ide.impl.idea.codeInsight.intention.impl.ShowIntentionActionsHandler;
import consulo.language.editor.inspection.scheme.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.scheme.ModifiableModel;
import com.intellij.codeInspection.ex.*;
import com.intellij.java.language.impl.JavaFileType;
import consulo.project.internal.StartupManagerEx;
import consulo.ide.impl.idea.ide.startup.impl.StartupManagerImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.editor.annotation.ExternalAnnotatorsFilter;
import com.intellij.lang.LanguageAnnotators;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.inject.InjectedLanguageManager;
import com.intellij.java.language.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.component.extension.Extensions;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import consulo.project.startup.StartupManager;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import consulo.language.editor.inspection.scheme.InspectionProfileManager;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeEx;
import consulo.language.cacheBuilder.CacheManager;
import consulo.language.psi.search.IndexPatternBuilder;
import consulo.language.psi.ReferenceProvidersRegistry;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.UsageSearchContext;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.FileTreeAccessFilter;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import consulo.language.util.IncorrectOperationException;
import com.intellij.xml.XmlSchemaProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.ui.UIAccess;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.*;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase
{
	private static final Logger LOGGER = Logger.getInstance(DaemonAnalyzerTestCase.class);

	private final Map<String, InspectionToolWrapper> myAvailableTools = new HashMap<String, InspectionToolWrapper>();
	private final FileTreeAccessFilter myFileTreeAccessFilter = new FileTreeAccessFilter();

	@Override
	protected boolean isRunInWriteAction()
	{
		return false;
	}

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();

		final LocalInspectionTool[] tools = configureLocalInspectionTools();
		for(LocalInspectionTool tool : tools)
		{
			enableInspectionTool(tool);
		}

		final InspectionProfileImpl profile = new InspectionProfileImpl(LightPlatformTestCase.PROFILE)
		{
			@Override
			@Nonnull
			public ModifiableModel getModifiableModel()
			{
				mySource = this;
				return this;
			}

			@Override
			@Nonnull
			public InspectionToolWrapper[] getInspectionTools(PsiElement element)
			{
				Collection<InspectionToolWrapper> values = myAvailableTools.values();
				return values.toArray(new InspectionToolWrapper[values.size()]);
			}

			@Nonnull
			@Override
			public List<Tools> getAllEnabledInspectionTools(Project project)
			{
				List<Tools> result = new ArrayList<Tools>();
				for(InspectionToolWrapper toolWrapper : getInspectionTools(null))
				{
					result.add(new ToolsImpl(toolWrapper, toolWrapper.getDefaultLevel(), true));
				}
				return result;
			}

			@Override
			public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element)
			{
				return key != null && myAvailableTools.containsKey(key.toString());
			}

			@Override
			public HighlightDisplayLevel getErrorLevel(@Nonnull HighlightDisplayKey key, PsiElement element)
			{
				final InspectionToolWrapper localInspectionTool = myAvailableTools.get(key.toString());
				return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
			}

			@Override
			public InspectionToolWrapper getInspectionTool(@Nonnull String shortName, @Nonnull PsiElement element)
			{
				return myAvailableTools.get(shortName);
			}
		};
		final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
		inspectionProfileManager.addProfile(profile);
		inspectionProfileManager.setRootProfile(LightPlatformTestCase.PROFILE);
		Disposer.register(getProject(), new Disposable()
		{
			@Override
			public void dispose()
			{
				inspectionProfileManager.deleteProfile(LightPlatformTestCase.PROFILE);
			}
		});
		InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
		InspectionProjectProfileManager.getInstance(getProject()).setProjectProfile(profile.getName());
		DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(getProject());
		daemonCodeAnalyzer.prepareForTest();
		final StartupManagerImpl startupManager = (StartupManagerImpl) StartupManagerEx.getInstanceEx(getProject());
		startupManager.runStartupActivities(UIAccess.get());
		startupManager.runPostStartupActivities(UIAccess.get());
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);

		if(isPerformanceTest())
		{
			IntentionManager.getInstance().getAvailableIntentionActions();  // hack to avoid slowdowns in PyExtensionFactory
			ReferenceProvidersRegistry.getInstance(); // pre-load tons of classes
			InjectedLanguageManager.getInstance(getProject()); // zillion of Dom Sem classes
			LanguageAnnotators.INSTANCE.allForLanguage(JavaLanguage.INSTANCE); // pile of annotator classes loads
			LanguageAnnotators.INSTANCE.allForLanguage(XMLLanguage.INSTANCE);
			ProblemHighlightFilter.EP_NAME.getExtensions();
			Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
			Extensions.getExtensions(XmlSchemaProvider.EP_NAME);
			Extensions.getExtensions(XmlFileNSInfoProvider.EP_NAME);
			Extensions.getExtensions(ExternalAnnotatorsFilter.EXTENSION_POINT_NAME);
			Extensions.getExtensions(IndexPatternBuilder.EP_NAME);
		}
	}

	@Override
	protected void tearDown() throws Exception
	{
		((StartupManagerImpl) StartupManager.getInstance(getProject())).checkCleared();
		((DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(getProject())).cleanupAfterTest();
		super.tearDown();
		//((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).assertPointersDisposed();
	}

	protected void enableInspectionTool(@Nonnull InspectionProfileEntry tool)
	{
		InspectionToolWrapper toolWrapper = InspectionToolRegistrar.wrapTool(tool);
		LightPlatformTestCase.enableInspectionTool(myAvailableTools, toolWrapper);
	}

	protected void enableInspectionToolsFromProvider(InspectionToolProvider toolProvider)
	{
		try
		{
			for(Class c : toolProvider.getInspectionClasses())
			{
				enableInspectionTool((LocalInspectionTool) c.newInstance());
			}
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	protected void disableInspectionTool(String shortName)
	{
		myAvailableTools.remove(shortName);
	}

	protected LocalInspectionTool[] configureLocalInspectionTools()
	{
		return LocalInspectionTool.EMPTY_ARRAY;
	}

	protected static LocalInspectionTool[] createLocalInspectionTools(final InspectionToolProvider... provider)
	{
		final ArrayList<LocalInspectionTool> result = new ArrayList<LocalInspectionTool>();
		for(InspectionToolProvider toolProvider : provider)
		{
			for(Class aClass : toolProvider.getInspectionClasses())
			{
				try
				{
					final Object tool = aClass.newInstance();
					Assert.assertTrue(tool instanceof LocalInspectionTool);
					result.add((LocalInspectionTool) tool);
				}
				catch(Exception e)
				{
					LOGGER.error(e);
				}
			}
		}
		return result.toArray(new LocalInspectionTool[result.size()]);
	}

	protected void doTest(@NonNls @Nonnull String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception
	{
		configureByFile(filePath);
		doDoTest(checkWarnings, checkInfos, checkWeakWarnings);
	}

	protected void doTest(@NonNls @Nonnull String filePath, boolean checkWarnings, boolean checkInfos) throws Exception
	{
		doTest(filePath, checkWarnings, checkInfos, false);
	}

	protected void doTest(@NonNls @Nonnull String filePath, @NonNls String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception
	{
		configureByFile(filePath, projectRoot);
		doDoTest(checkWarnings, checkInfos);
	}

	@Nonnull
	@SuppressWarnings("TestMethodWithIncorrectSignature")
	protected HighlightTestInfo testFile(@NonNls @Nonnull String... filePath)
	{
		return new HighlightTestInfo(getTestRootDisposable(), filePath)
		{
			@Override
			public HighlightTestInfo doTest()
			{
				try
				{
					configureByFiles(projectRoot, filePaths);
				}
				catch(Exception e)
				{
					throw new RuntimeException(e);
				}
				ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile);
				if(checkSymbolNames)
				{
					data.checkSymbolNames();
				}
				checkHighlighting(data);
				return this;
			}
		};
	}

	protected void doTest(@Nonnull VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception
	{
		doTest(new VirtualFile[]{vFile}, checkWarnings, checkInfos);
	}

	protected void doTest(@Nonnull VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception
	{
		configureByFiles(null, vFile);
		doDoTest(checkWarnings, checkInfos);
	}

	protected void doTest(boolean checkWarnings, boolean checkInfos, String... files) throws Exception
	{
		configureByFiles(null, files);
		doDoTest(checkWarnings, checkInfos);
	}

	@Nonnull
	protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos)
	{
		return doDoTest(checkWarnings, checkInfos, false);
	}

	protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings)
	{
		return checkHighlighting(new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, myFile));
	}

	@Nonnull
	protected Collection<HighlightInfo> checkHighlighting(@Nonnull final ExpectedHighlightingData data)
	{
		data.init();
		PsiDocumentManager.getInstance(myProject).commitAllDocuments();

		//to load text
		ApplicationManager.getApplication().runWriteAction(new Runnable()
		{
			@Override
			public void run()
			{
				TreeUtil.clearCaches((TreeElement) myFile.getNode());
			}
		});


		//to initialize caches
		if(!DumbService.isDumb(getProject()))
		{
			CacheManager.getInstance(myProject).getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true);
		}
		final JavaPsiFacadeEx facade = getJavaFacade();
		if(facade != null)
		{
			facade.setAssertOnFileLoadingFilter(myFileTreeAccessFilter, null); // check repository work
		}

		Collection<HighlightInfo> infos = doHighlighting();

		if(facade != null)
		{
			facade.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, null);
		}

		String text = myEditor.getDocument().getText();
		List lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(getFile()), getProject());
		data.checkLineMarkers(lineMarkers, text);
		data.checkResult(infos, text);
		return infos;
	}

	public void allowTreeAccessForFile(@Nonnull VirtualFile file)
	{
		myFileTreeAccessFilter.allowTreeAccessForFile(file);
	}

	@Nonnull
	protected List<HighlightInfo> highlightErrors()
	{
		return doHighlighting(HighlightSeverity.ERROR);
	}

	@Nonnull
	protected List<HighlightInfo> doHighlighting(@Nonnull HighlightSeverity minSeverity)
	{
		return filter(doHighlighting(), minSeverity);
	}

	@Nonnull
	protected List<HighlightInfo> doHighlighting()
	{
		PsiDocumentManager.getInstance(myProject).commitAllDocuments();

		IntList toIgnore = IntLists.newArrayList();
		if(!doTestLineMarkers())
		{
			toIgnore.add(Pass.LINE_MARKERS);
		}

		if(!doExternalValidation())
		{
			toIgnore.add(Pass.EXTERNAL_TOOLS);
		}
		if(forceExternalValidation())
		{
			toIgnore.add(Pass.LINE_MARKERS);
			toIgnore.add(Pass.LOCAL_INSPECTIONS);
			toIgnore.add(Pass.POPUP_HINTS);
			toIgnore.add(Pass.UPDATE_ALL);
		}

		boolean canChange = canChangeDocumentDuringHighlighting();
		List<HighlightInfo> infos = CodeInsightTestFixtureImpl.instantiateAndRun(getFile(), getEditor(), toIgnore.toArray(), canChange);

		if(!canChange)
		{
			Document document = getDocument(getFile());
			DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
			daemonCodeAnalyzer.getFileStatusMap().assertAllDirtyScopesAreNull(document);
		}

		return infos;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({
			ElementType.METHOD,
			ElementType.TYPE
	})
	public @interface CanChangeDocumentDuringHighlighting
	{
	}

	private boolean canChangeDocumentDuringHighlighting()
	{
		return annotatedWith(CanChangeDocumentDuringHighlighting.class);
	}

	@Nonnull
	public static List<HighlightInfo> filter(@Nonnull List<HighlightInfo> infos, @Nonnull HighlightSeverity minSeverity)
	{
		ArrayList<HighlightInfo> result = new ArrayList<HighlightInfo>();
		for(final HighlightInfo info : infos)
		{
			if(info.getSeverity().compareTo(minSeverity) >= 0)
			{
				result.add(info);
			}
		}
		return result;
	}

	protected boolean doTestLineMarkers()
	{
		return false;
	}

	protected boolean doExternalValidation()
	{
		return true;
	}

	protected boolean forceExternalValidation()
	{
		return false;
	}

	protected static void findAndInvokeIntentionAction(@Nonnull Collection<HighlightInfo> infos,
			@Nonnull String intentionActionName,
			@Nonnull Editor editor,
			@Nonnull PsiFile file) throws IncorrectOperationException
	{
		IntentionAction intentionAction = findIntentionAction(infos, intentionActionName, editor, file);

		Assert.assertNotNull(intentionActionName, intentionAction);
		Assert.assertTrue(ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, intentionAction, intentionActionName));
	}

	protected static IntentionAction findIntentionAction(@Nonnull Collection<HighlightInfo> infos, @Nonnull String intentionActionName, @Nonnull Editor editor, @Nonnull PsiFile file)
	{
		List<IntentionAction> actions = LightQuickFixTestCase.getAvailableActions(editor, file);
		IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(actions, intentionActionName);

		if(intentionAction == null)
		{
			final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();

			for(HighlightInfo info : infos)
			{
				if(info.quickFixActionRanges != null)
				{
					for(Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges)
					{
						IntentionAction action = pair.first.getAction();
						if(action.isAvailable(file.getProject(), editor, file))
						{
							availableActions.add(action);
						}
					}
				}
			}

			intentionAction = LightQuickFixTestCase.findActionWithText(availableActions, intentionActionName);
		}
		return intentionAction;
	}

	public void checkHighlighting(Editor editor, boolean checkWarnings, boolean checkInfos)
	{
		setActiveEditor(editor);
		doDoTest(checkWarnings, checkInfos);
	}

	public PsiClass createClass(String text) throws IOException
	{
		return createClass(myModule, text);
	}

	protected PsiClass createClass(final Module module, final String text) throws IOException
	{
		return new WriteCommandAction<PsiClass>(getProject())
		{
			@Override
			protected void run(Result<PsiClass> result) throws Throwable
			{
				final PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
				final PsiJavaFile javaFile = (PsiJavaFile) factory.createFileFromText("a.java", JavaFileType.INSTANCE, text);
				final String qname = javaFile.getClasses()[0].getQualifiedName();
				Assert.assertNotNull(qname);
				final VirtualFile[] files = ModuleRootManager.getInstance(module).getSourceRoots();
				File dir;
				if(files.length > 0)
				{
					dir = VfsUtilCore.virtualToIoFile(files[0]);
				}
				else
				{
					dir = createTempDirectory();
					VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
					addSourceContentToRoots(module, vDir);
				}

				File file = new File(dir, qname.replace('.', '/') + ".java");
				FileUtil.createIfDoesntExist(file);
				VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getCanonicalPath().replace(File.separatorChar, '/'));
				Assert.assertNotNull(vFile);
				consulo.ide.impl.idea.openapi.vfs.VfsUtil.saveText(vFile, text);
				PsiJavaFile psiFile = (PsiJavaFile) myPsiManager.findFile(vFile);
				Assert.assertNotNull(psiFile);
				PsiClass psiClass = psiFile.getClasses()[0];
				result.setResult(psiClass);

			}
		}.execute().throwException().getResultObject();
	}
}
