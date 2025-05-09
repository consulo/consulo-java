package com.intellij.codeInsight.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import com.intellij.java.language.psi.*;
import consulo.undoRedo.UndoManager;
import consulo.virtualFileSystem.LocalFileSystem;
import org.jetbrains.annotations.NonNls;
import consulo.language.editor.CodeInsightSettings;
import consulo.ide.impl.idea.codeInsight.daemon.impl.DaemonListeners;
import consulo.language.editor.rawHighlight.HighlightInfo;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import consulo.ide.impl.idea.codeInsight.generation.actions.CommentByBlockCommentAction;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.java.language.impl.JavaFileType;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.language.editor.WriteCommandAction;
import consulo.ide.impl.idea.openapi.command.impl.UndoManagerImpl;
import consulo.logging.Logger;
import consulo.language.impl.internal.psi.LoadTextUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.codeStyle.PackageEntry;
import consulo.language.codeStyle.PackageEntryTable;
import com.intellij.java.impl.psi.impl.source.codeStyle.ImportHelper;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ui.ex.awt.UIUtil;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public abstract class ImportHelperTest extends DaemonAnalyzerTestCase
{
	private static final Logger LOGGER = Logger.getInstance(ImportHelper.class);

	@Override
	protected LocalInspectionTool[] configureLocalInspectionTools()
	{
		return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
	}

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
		settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
		CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
		DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(false);
	}

	@Override
	protected void tearDown() throws Exception
	{
		CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
		super.tearDown();
	}

	@WrapInCommand
	public void testImportsInsertedAlphabetically() throws Throwable
	{
		@NonNls String text = "class I {}";
		final PsiJavaFile file = (PsiJavaFile) configureByText(JavaFileType.INSTANCE, text);
		assertEmpty(highlightErrors());
		CommandProcessor.getInstance().executeCommand(getProject(), new Runnable()
		{
			@Override
			public void run()
			{
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							checkAddImport(file, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_LIST);
							checkAddImport(file, CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_LIST);
							checkAddImport(file, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST);
							checkAddImport(file, CommonClassNames.JAVA_UTIL_SORTED_MAP, CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_SORTED_MAP);
							checkAddImport(file, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP);
							checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP);
							checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames
									.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP);
							checkAddImport(file, "java.util.TreeMap", "java.util.AbstractList", CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP, "java.util.TreeMap");
							checkAddImport(file, "java.util.concurrent.atomic.AtomicBoolean", "java.util.AbstractList", CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST,
									CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP, "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
							checkAddImport(file, CommonClassNames.JAVA_IO_FILE, CommonClassNames.JAVA_IO_FILE, "java.util.AbstractList", CommonClassNames.JAVA_UTIL_ARRAY_LIST, CommonClassNames.JAVA_UTIL_HASH_MAP, CommonClassNames.JAVA_UTIL_LIST,
									CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_SORTED_MAP, "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
						}
						catch(Throwable e)
						{
							LOGGER.error(e);
						}
					}
				});
			}
		}, "", "");
	}

	@WrapInCommand
	public void testStaticImportsGrouping() throws Throwable
	{
		@NonNls String text = "import static java.lang.Math.max;\n" + "import java.util.Map;\n" + "\n" + "import static java.lang.Math.min;\n" + "\n" + "import java.awt.Component;\n" + "\n" + "\n" +
				"\n" + "import static javax.swing.SwingConstants.CENTER;\n" + "class I {{ max(0, 0); Map.class.hashCode(); min(0,0); Component.class.hashCode(); int i = CENTER; }}";

		final PsiJavaFile file = (PsiJavaFile) configureByText(JavaFileType.INSTANCE, text);
		assertEmpty(highlightErrors());
		CommandProcessor.getInstance().executeCommand(getProject(), new Runnable()
		{
			@Override
			public void run()
			{
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{

							CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
							settings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
							PackageEntryTable table = new PackageEntryTable();
							table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
							table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
							table.addEntry(new PackageEntry(false, "javax", true));
							table.addEntry(new PackageEntry(false, "java", true));
							table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
							table.addEntry(new PackageEntry(true, "java", true));
							table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
							table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);

							settings.IMPORT_LAYOUT_TABLE.copyFrom(table);
							CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
							try
							{
								JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);
							}
							finally
							{
								CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
							}

							assertOrder(file, "java.awt.*", CommonClassNames.JAVA_UTIL_MAP, "static java.lang.Math.max", "static java.lang.Math.min", "static javax.swing.SwingConstants.CENTER");

						}
						catch(Throwable e)
						{
							LOGGER.error(e);
						}
					}
				});
			}
		}, "", "");
	}

	private void checkAddImport(PsiJavaFile file, String fqn, String... expectedOrder)
	{
		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
		ImportHelper importHelper = new ImportHelper(settings);

		PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(fqn, GlobalSearchScope.allScope(getProject()));
		boolean b = importHelper.addImport(file, psiClass);
		assertTrue(b);

		assertOrder(file, expectedOrder);
	}

	private static void assertOrder(PsiJavaFile file, @NonNls String... expectedOrder)
	{
		PsiImportStatementBase[] statements = file.getImportList().getAllImportStatements();

		assertEquals(expectedOrder.length, statements.length);
		for(int i = 0; i < statements.length; i++)
		{
			PsiImportStatementBase statement = statements[i];
			String text = StringUtil.trimEnd(StringUtil.trimStart(statement.getText(), "import "), ";");
			assertEquals(expectedOrder[i], text);
		}
	}

	@NonNls
	private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting/reimportConflictingClasses";

	@WrapInCommand
	public void testReimportConflictingClasses() throws Exception
	{
		configureByFile(BASE_PATH + "/x/Usage.java", BASE_PATH);
		assertEmpty(highlightErrors());

		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
		settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
		CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
		try
		{
			new WriteCommandAction.Simple(getProject())
			{
				@Override
				protected void run() throws Throwable
				{
					JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile());
				}
			}.execute().throwException();
		}
		finally
		{
			CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
		}


		@NonNls String fullPath = getTestDataPath() + BASE_PATH + "/x/Usage_afterOptimize.txt";
		final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
		String text = LoadTextUtil.loadText(vFile).toString();
		assertEquals(text, getFile().getText());
	}

	@WrapInCommand
	public void testConflictingClassesFromCurrentPackage() throws Throwable
	{
		final PsiFile file = configureByText(JavaFileType.INSTANCE, "package java.util; class X{ Date d;}");
		assertEmpty(highlightErrors());

		new WriteCommandAction.Simple(getProject())
		{
			@Override
			protected void run() throws Throwable
			{
				CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
				ImportHelper importHelper = new ImportHelper(settings);

				PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("java.sql.Date", GlobalSearchScope.allScope(getProject()));
				boolean b = importHelper.addImport((PsiJavaFile) file, psiClass);
				assertFalse(b); // must fail
			}
		}.execute().throwException();
	}

	public void testAutoImportCaretLocation() throws Throwable
	{
		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		try
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
			configureByText(JavaFileType.INSTANCE, "class X { ArrayList<caret> c; }");
			((UndoManagerImpl) UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
			((UndoManagerImpl) UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
			type(" ");
			backspace();

			assertOneElement(highlightErrors());

			int offset = myEditor.getCaretModel().getOffset();
			PsiReference ref = myFile.findReferenceAt(offset - 1);
			assertTrue(ref instanceof PsiJavaCodeReferenceElement);

			ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement) ref).doFix(getEditor(), true, false);
			assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
			UIUtil.dispatchAllInvocationEvents();

			myEditor.getCaretModel().moveToOffset(offset - 1);
			result = new ImportClassFix((PsiJavaCodeReferenceElement) ref).doFix(getEditor(), true, false);
			assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
			UIUtil.dispatchAllInvocationEvents();

			assertEmpty(highlightErrors());
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportCaretLocation2() throws Throwable
	{
		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		try
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
			configureByText(JavaFileType.INSTANCE, "class X { <caret>ArrayList c = new ArrayList(); }");
			((UndoManagerImpl) UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
			((UndoManagerImpl) UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
			type(" ");
			backspace();

			assertEquals(2, highlightErrors().size());
			UIUtil.dispatchAllInvocationEvents();

			int offset = myEditor.getCaretModel().getOffset();
			PsiReference ref = myFile.findReferenceAt(offset);
			assertTrue(ref instanceof PsiJavaCodeReferenceElement);

			ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement) ref).doFix(getEditor(), true, false);
			assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
			UIUtil.dispatchAllInvocationEvents();

			assertEmpty(highlightErrors());
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportWorksWhenITypeSpaceAfterClassName() throws Throwable
	{
		@NonNls String text = "class S { ArrayList<caret> }";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			doHighlighting();
			//caret is too close
			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());

			type(" ");

			PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement) getFile().findReferenceAt(getEditor().getCaretModel().getOffset() - 2);
			ImportClassFix fix = new ImportClassFix(element);
			ImportClassFixBase.Result result = fix.doFix(getEditor(), false, false);
			assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);

			assertNotSame(0, ((PsiJavaFile) getFile()).getImportList().getAllImportStatements().length);
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportAfterUncomment() throws Throwable
	{
		@NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			doHighlighting();

			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());

			CommentByBlockCommentAction action = new CommentByBlockCommentAction();
			action.actionPerformedImpl(getProject(), getEditor());

			assertEmpty(highlightErrors());

			assertNotSame(0, ((PsiJavaFile) getFile()).getImportList().getAllImportStatements().length);
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportWorks() throws Throwable
	{
		@NonNls final String text = "class S { JFrame x; <caret> }";
		configureByText(JavaFileType.INSTANCE, text);
		((UndoManagerImpl) UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
		((UndoManagerImpl) UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
		assertFalse(DaemonListeners.canChangeFileSilently(getFile()));


		doHighlighting();
		assertFalse(DaemonListeners.canChangeFileSilently(getFile()));

		type(" ");
		assertTrue(DaemonListeners.canChangeFileSilently(getFile()));

		undo();

		assertFalse(DaemonListeners.canChangeFileSilently(getFile()));//CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
	}


	public void testAutoImportOfGenericReference() throws Throwable
	{
		@NonNls final String text = "class S {{ new ArrayList<caret><> }}";
		configureByText(JavaFileType.INSTANCE, text);
		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		((UndoManagerImpl) UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
		((UndoManagerImpl) UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
		type(" ");
		backspace();

		try
		{
			doHighlighting();
			//caret is too close
			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());

			caretRight();

			doHighlighting();

			assertNotSame(0, ((PsiJavaFile) getFile()).getImportList().getAllImportStatements().length);
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoOptimizeUnresolvedImports() throws Throwable
	{
		@NonNls String text = "import xxx.yyy; class S { } <caret> ";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			List<HighlightInfo> errs = highlightErrors();

			assertEquals(1, errs.size());

			assertEquals(1, ((PsiJavaFile) getFile()).getImportList().getAllImportStatements().length);

			type("/* */");
			doHighlighting();
			UIUtil.dispatchAllInvocationEvents();

			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());
		}
		finally
		{
			CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoInsertImportForInnerClass() throws Throwable
	{
		@NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			List<HighlightInfo> errs = highlightErrors();
			assertEquals(1, errs.size());

			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());
			type("/* */");
			doHighlighting();
			UIUtil.dispatchAllInvocationEvents();
			assertEmpty(((PsiJavaFile) getFile()).getImportList().getAllImportStatements());
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportSkipsClassReferenceInMethodPosition() throws Throwable
	{
		@NonNls String text = "package x; import java.util.HashMap; class S { HashMap<String,String> f(){ return  Hash<caret>Map <String, String >();} }  ";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			List<HighlightInfo> errs = highlightErrors();
			assertTrue(errs.size() > 1);

			PsiJavaFile javaFile = (PsiJavaFile) getFile();
			assertEquals(1, javaFile.getImportList().getAllImportStatements().length);

			PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset());
			ImportClassFix fix = new ImportClassFix((PsiJavaCodeReferenceElement) ref);
			assertFalse(fix.isAvailable(getProject(), getEditor(), getFile()));
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
		}
	}

	public void testAutoImportDoNotBreakCode() throws Throwable
	{
		@NonNls String text = "package x; class S {{ S.<caret>\n Runnable r; }}";
		configureByText(JavaFileType.INSTANCE, text);

		boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
		boolean opt = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
		CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
		CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
		DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

		try
		{
			List<HighlightInfo> errs = highlightErrors();
			assertEquals(1, errs.size());
		}
		finally
		{
			CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
			CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = opt;
		}
	}

	public void testAutoImportIgnoresUnresolvedImportReferences() throws Throwable
	{
		@NonNls String text = "package x; import xxx.yyy.ArrayList; class S {{ ArrayList<caret> r; }}";
		configureByText(JavaFileType.INSTANCE, text);

		PsiJavaFile javaFile = (PsiJavaFile) getFile();
		PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset() - 1);
		ImportClassFix fix = new ImportClassFix((PsiJavaCodeReferenceElement) ref);
		assertFalse(fix.isAvailable(getProject(), getEditor(), getFile()));
	}

}
