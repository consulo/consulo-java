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
package com.intellij.testFramework.fixtures;

import java.io.File;

import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import consulo.language.Language;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestModuleDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import consulo.java.language.module.extension.JavaMutableModuleExtension;

/**
 * @author peter
 */
public abstract class LightCodeInsightFixtureTestCase extends UsefulTestCase {
  public static final TestModuleDescriptor JAVA_1_6 = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      model.getExtensionWithoutCheck(JavaMutableModuleExtension.class).getInheritableLanguageLevel().set(null, LanguageLevel.JDK_1_6);
    }
  };
  public static final TestModuleDescriptor JAVA_LATEST = new DefaultLightProjectDescriptor();


  protected JavaCodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());

    myModule = myFixture.getModule();
  }

  /**
   * Return relative path to the test data.
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  @Nonnull
  protected TestModuleDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }


  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected String getTestDataPath() {
    String communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
    String path = communityPath + getBasePath();
    if (new File(path).exists()) return path;
    return communityPath + "/../" + getBasePath();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
    super.tearDown();
  }
  protected final void runTestBare() throws Throwable {
    LightCodeInsightFixtureTestCase.super.runTest();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }

  protected PsiFile createLightFile(final FileType fileType, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText("a." + fileType.getDefaultExtension(), fileType, text);
  }

  public PsiFile createLightFile(final String fileName, final Language language, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, false, true);
  }

}
