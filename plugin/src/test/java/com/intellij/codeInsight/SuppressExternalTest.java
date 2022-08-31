/*
 * User: anna
 * Date: 17-Jun-2010
 */
package com.intellij.codeInsight;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

public abstract class SuppressExternalTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  private LanguageLevel myLanguageLevel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder(getTestName(false));
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath("/codeInsight/externalAnnotations");
    final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    new File(myFixture.getTempDirPath() + "/src/").mkdir();
    builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    myFixture.enableInspections(new SillyAssignmentInspection());
    myFixture.setUp();

    addAnnotationsModuleRoot();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    myLanguageLevel = LanguageLevel.HIGHEST; // LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    //LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  private void addAnnotationsModuleRoot() throws IOException {
    myFixture.copyDirectoryToProject("content/anno/suppressed", "content/anno/suppressed");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Module module = myFixture.getModule();
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        final String url = VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno");
        //model.getModuleExtensionOld(JavaModuleExternalPaths.class).setExternalAnnotationUrls(new String[]{url});
        model.commit();
      }
    });
  }


  @Override
  public void tearDown() throws Exception {
    //LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myLanguageLevel);
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }


  private void doTest(String testName) throws Exception {
    final IntentionAction action = myFixture.getAvailableIntention("Suppress for method", "src/suppressed/" + testName + ".java");
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("content/anno/suppressed/annotations.xml", "content/anno/suppressed/annotations" + testName + "_after.xml", true);
  }


  public void testNewSuppress() throws Throwable {
    doTest("NewSuppress");
  }

  public void testExistingExternalName() throws Exception {
    doTest("ExistingExternalName");
  }

  public void testSecondSuppression() throws Exception {
    doTest("SecondSuppression");
  }

}
