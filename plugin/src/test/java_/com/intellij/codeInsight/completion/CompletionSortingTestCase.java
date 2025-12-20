/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.ide.impl.idea.codeInsight.lookup.impl.LookupImpl;
import consulo.application.ui.UISettings;
import consulo.ide.impl.psi.statistics.StatisticsManager;
import consulo.ide.impl.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.TestDataPath;

/**
 * @author peter
 */
@TestDataPath("$CONTENT_ROOT/testData")
public abstract class CompletionSortingTestCase extends LightFixtureCompletionTestCase
{
	private final CompletionType myType;

	@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
	protected CompletionSortingTestCase(CompletionType type)
	{
		myType = type;
	}

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		((StatisticsManagerImpl) StatisticsManager.getInstance()).enableStatistics(getTestRootDisposable());
	}

	@Override
	protected void tearDown() throws Exception
	{
		LookupManager.getInstance(getProject()).hideActiveLookup();
		UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = false;
		CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
		super.tearDown();
	}

	@Override
	protected abstract String getBasePath();

	protected void checkPreferredItems(int selected, @NonNls String... expected)
	{
		invokeCompletion(getTestName(false) + ".java");
		assertPreferredItems(selected, expected);
	}

	protected void assertPreferredItems(int selected, @NonNls String... expected)
	{
		myFixture.assertPreferredCompletionItems(selected, expected);
	}

	protected LookupImpl invokeCompletion(String path)
	{
		configureNoCompletion(path);
		myFixture.complete(myType);
		return getLookup();
	}

	protected void configureNoCompletion(String path)
	{
		myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')));
	}

	protected static void incUseCount(LookupImpl lookup, int index)
	{
		imitateItemSelection(lookup, index);
		refreshSorting(lookup);
	}

	protected static void refreshSorting(LookupImpl lookup)
	{
		lookup.setSelectionTouched(false);
		lookup.resort(true);
	}

	protected static void imitateItemSelection(LookupImpl lookup, int index)
	{
		LookupElement item = lookup.getItems().get(index);
		lookup.setCurrentItem(item);
		StatisticsUpdate.collectStatisticChanges(item);
		StatisticsUpdate.applyLastCompletionStatisticsUpdate();
	}
}
