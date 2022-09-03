package com.intellij.codeInsight.completion;

import java.util.List;

import javax.annotation.Nonnull;

import org.junit.Assert;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.event.LookupEvent;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.ide.impl.idea.codeInsight.lookup.impl.LookupImpl;
import consulo.language.editor.WriteCommandAction;
import com.intellij.testFramework.TestModuleDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public abstract class LightFixtureCompletionTestCase extends LightCodeInsightFixtureTestCase {
  protected LookupElement[] myItems;

  @Nonnull
  @Override
  protected TestModuleDescriptor getProjectDescriptor() {
    return JAVA_1_6;
  }

  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    super.tearDown();
  }

  protected void configureByFile(String path) {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')));
    complete();
  }

  protected void complete() {
    myItems = myFixture.completeBasic();
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void checkResultByFile(String path) {
    myFixture.checkResultByFile(path);
  }

  protected void selectItem(LookupElement item, final char completionChar) {
    final LookupImpl lookup = getLookup();
    lookup.setCurrentItem(item);
    if (LookupEvent.isSpecialCompletionChar(completionChar)) {
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          lookup.finishLookup(completionChar);
        }
      }.execute().throwException();
    } else {
      type(completionChar);
    }
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
  }

  protected void assertFirstStringItems(String... items) {
    List<String> strings = myFixture.getLookupElementStrings();
    Assert.assertNotNull(strings);
    assertOrderedEquals(strings.subList(0, Math.min(items.length, strings.size())), items);
  }
  protected void assertStringItems(String... items) {
    assertOrderedEquals(myFixture.getLookupElementStrings(), items);
  }

  protected void type(String s) {
    myFixture.type(s);
  }
  protected void type(char c) {
    myFixture.type(c);
  }
}
