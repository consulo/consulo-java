package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import consulo.language.editor.intention.IntentionAction;
import consulo.ide.impl.idea.codeInspection.ex.QuickFixWrapper;
import com.intellij.java.impl.codeInspection.inheritance.ChangeSuperClassFix;
import com.intellij.java.impl.codeInspection.inheritance.SuperClassHasFrequentlyUsedInheritorsInspection;
import consulo.util.lang.Pair;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import consulo.util.collection.ContainerUtil;
import java.util.HashSet;
import javax.annotation.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
@SuppressWarnings("ALL")
public abstract class SuperClassHasFrequentlyUsedInheritorsInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/smartInheritance/";
  }

  //search tests

  public void testRelevantClassShowed() {
    doTest(Pair.create("C", 75), Pair.create("B", 91));
  }

  public void testInterfacesNotShowed() {
    assertEmptyResult();
  }

  public void testInterfacesNotShowed2() {
    doTest(Pair.create("D", 83));
  }

  public void testAnonymousClasses() {
    doTest(Pair.create("B", 83));
  }

  public void testAnonymousClassesInStats() {
    doTest(Pair.create("A", 62));
  }

  public void testAbstractClass() {
    doTest(Pair.create("B", 85));
  }

  public void  testNoCompletionForAbstractClasses() {
    assertEmptyResult();
  }

  public void testNoMoreThanMaxCountIntentions() {
    doTest(SuperClassHasFrequentlyUsedInheritorsInspection.MAX_QUICK_FIX_COUNTS);
  }

  // completion tests

  private void assertEmptyResult() {
    doTest();
  }

  private void doTest(final Pair<String, Integer>... expectedResults) {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(SuperClassHasFrequentlyUsedInheritorsInspection.class);

    final Set<Pair<String, Integer>> actualSet = new HashSet<Pair<String, Integer>>();
    for (IntentionAction intentionAction : myFixture.getAvailableIntentions()) {
      if (intentionAction instanceof QuickFixWrapper) {
        LocalQuickFix localQuickFix = ((QuickFixWrapper)intentionAction).getFix();
        ChangeSuperClassFix changeSuperClassFix = getQuickFixFromWrapper((QuickFixWrapper)intentionAction);
        if (changeSuperClassFix != null) {
          actualSet.add(Pair.create(changeSuperClassFix.getNewSuperClass().getQualifiedName(), changeSuperClassFix.getPercent()));
        }
      }
    }

    final Set<Pair<String, Integer>> expectedSet = ContainerUtil.newHashSet(expectedResults);
    assertEquals(expectedSet, actualSet);
  }

  private void doTest(final int expectedSize) {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(SuperClassHasFrequentlyUsedInheritorsInspection.class);


    final Set<Pair<String, Integer>> actualSet = new HashSet<Pair<String, Integer>>();
    for (IntentionAction intentionAction : myFixture.getAvailableIntentions()) {
      if (intentionAction instanceof QuickFixWrapper) {
        ChangeSuperClassFix changeSuperClassFix = getQuickFixFromWrapper((QuickFixWrapper)intentionAction);
        if (changeSuperClassFix != null) {
          actualSet.add(Pair.create(changeSuperClassFix.getNewSuperClass().getQualifiedName(), changeSuperClassFix.getPercent()));
        }
      }
    }

    assertSize(expectedSize, actualSet);
  }

  @Nullable
  private final static ChangeSuperClassFix getQuickFixFromWrapper(final QuickFixWrapper quickFixWrapper) {
    final LocalQuickFix quickFix = quickFixWrapper.getFix();
    if (quickFix instanceof ChangeSuperClassFix) {
      return (ChangeSuperClassFix)quickFix;
    }
    return null;
  }
}
