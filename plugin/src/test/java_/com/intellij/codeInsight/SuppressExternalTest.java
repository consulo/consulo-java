/*
 * User: anna
 * Date: 17-Jun-2010
 */
package com.intellij.codeInsight;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;

import consulo.language.editor.intention.IntentionAction;
import com.intellij.java.impl.codeInspection.sillyAssignment.SillyAssignmentInspection;
import consulo.application.ApplicationManager;
import consulo.module.Module;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
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
    IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder(getTestName(false));
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath("/codeInsight/externalAnnotations");
    JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    new File(myFixture.getTempDirPath() + "/src/").mkdir();
    builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    myFixture.enableInspections(new SillyAssignmentInspection());
    myFixture.setUp();

    addAnnotationsModuleRoot();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    myLanguageLevel = LanguageLevel.HIGHEST; // LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    //LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  private void addAnnotationsModuleRoot() throws IOException {
    myFixture.copyDirectoryToProject("content/anno/suppressed", "content/anno/suppressed");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Module module = myFixture.getModule();
        ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        String url = VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno");
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
    IntentionAction action = myFixture.getAvailableIntention("Suppress for method", "src/suppressed/" + testName + ".java");
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
