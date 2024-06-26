/*
 * User: anna
 * Date: 27-Jun-2007
 */
package com.intellij.codeInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ExternalAnnotationsListener;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import consulo.language.editor.intention.IntentionAction;
import com.intellij.java.impl.codeInsight.intention.impl.DeannotateIntentionAction;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.document.FileDocumentManager;
import consulo.module.Module;
import consulo.project.Project;
import com.intellij.java.language.projectRoots.roots.AnnotationOrderRootType;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.content.OrderRootType;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.util.lang.Trinity;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.io.StreamUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import consulo.component.messagebus.MessageBusConnection;

public abstract class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;
  private Project myProject;
  private boolean myExpectedEventWasProduced = false;
  private boolean myUnexpectedEventWasProduced = false;
  private MessageBusConnection myBusConnection = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(false));

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = "/codeInsight/externalAnnotations";
    myFixture.setTestDataPath(dataPath);
    final JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    myModule = builder.getFixture().getModule();
    myProject = myFixture.getProject();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
    myProject = null;
    assertNull(myBusConnection);
  }

  private void addDefaultLibrary() {
    addLibrary("/content/anno");
  }

  private void addLibrary(final @Nonnull String... annotationsDirs) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final LibraryTable libraryTable = model.getModuleLibraryTable();
        final Library library = libraryTable.createLibrary("test");

        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + "/lib"), OrderRootType.SOURCES);
        for (String annotationsDir : annotationsDirs) {
          libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + annotationsDir), AnnotationOrderRootType.getInstance());
        }
        libraryModel.commit();
        model.commit();
      }
    });
  }

  @Nonnull
  private PsiModifierListOwner getOwner() {
    CaretModel caretModel = myFixture.getEditor().getCaretModel();
    int position = caretModel.getOffset();
    PsiElement element = myFixture.getFile().findElementAt(position);
    assert element != null;
    PsiModifierListOwner container = AddAnnotationPsiFix.getContainer(element.getContainingFile(), element.getTextOffset());
    assert container != null;
    return container;
  }

  private void startListening(@Nonnull final List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence) {
    myBusConnection = myProject.getMessageBus().connect();
    myBusConnection.subscribe(ExternalAnnotationsManager.TOPIC, new DefaultAnnotationsListener() {
      private int index = 0;

      @Override
      public void afterExternalAnnotationChanging(@Nonnull PsiModifierListOwner owner, @Nonnull String annotationFQName,
                                                  boolean successful) {
        if (index < expectedSequence.size() && expectedSequence.get(index).first == owner
            && expectedSequence.get(index).second.equals(annotationFQName) && expectedSequence.get(index).third == successful) {
          index++;
          myExpectedEventWasProduced = true;
        }
        else {
          super.afterExternalAnnotationChanging(owner, annotationFQName, successful);
        }
      }
    });
  }

  private void startListening(@Nonnull final PsiModifierListOwner expectedOwner, @Nonnull final String expectedAnnotationFQName,
                              final boolean expectedSuccessful) {
    startListening(Arrays.asList(Trinity.create(expectedOwner, expectedAnnotationFQName, expectedSuccessful)));
  }

  private void startListeningForExternalChanges() {
    myBusConnection = myProject.getMessageBus().connect();
    myBusConnection.subscribe(ExternalAnnotationsManager.TOPIC, new DefaultAnnotationsListener() {
      private boolean notifiedOnce;

      @Override
      public void externalAnnotationsChangedExternally() {
        if (!notifiedOnce) {
          myExpectedEventWasProduced = true;
          notifiedOnce = true;
        }
        else {
          super.externalAnnotationsChangedExternally();
        }
      }
    });
  }

  private void stopListeningAndCheckEvents() {
    myBusConnection.disconnect();
    myBusConnection = null;

    assertTrue(myExpectedEventWasProduced);
    assertFalse(myUnexpectedEventWasProduced);

    myExpectedEventWasProduced = false;
    myUnexpectedEventWasProduced = false;
  }

  public void testAnnotateLibrary() throws Throwable {

    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    final IntentionAction fix = myFixture.findSingleIntention("Annotate method 'get' as @NotNull");
    assertTrue(fix.isAvailable(myProject, editor, file));

    // expecting other @Nullable annotations to be removed, and default @NotNull to be added
    List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence
      = new ArrayList<Trinity<PsiModifierListOwner, String, Boolean>>();
    for (String notNull : NullableNotNullManager.getInstance(myProject).getNullables()) {
      expectedSequence.add(Trinity.create(getOwner(), notNull, false));
    }
    expectedSequence.add(Trinity.create(getOwner(), AnnotationUtil.NOT_NULL, true));
    startListening(expectedSequence);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        fix.invoke(myProject, editor, file);
      }
    }.execute();

    FileDocumentManager.getInstance().saveAllDocuments();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));
    stopListeningAndCheckEvents();

    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsAnnotateLibrary_after.xml", false);
  }

  public void testPrimitive() throws Throwable {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());

    assertNotAvailable("Annotate method 'get' as @NotNull");
  }

  private void assertNotAvailable(String hint) {
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(hint);
    assertEmpty(actions);
  }

  public void testAnnotated() throws Throwable {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestAnnotated.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();
    assertNotAvailable("Annotate method 'get' as @NotNull");
    assertNotAvailable("Annotate method 'get' as @Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
  }

  public void testDeannotation() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation.java", "Annotate method 'get' as @NotNull", "Annotate method 'get' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation_after.xml", false);
  }

  public void testDeannotation1() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation1.java", "Annotate parameter 'ss' as @NotNull", "Annotate parameter 'ss' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation1_after.xml", false);
  }

  private void doDeannotate(@NonNls final String testPath, String hint1, String hint2) throws Throwable {
    myFixture.configureByFile(testPath);
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    assertNotAvailable(hint1);
    assertNotAvailable(hint2);

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertTrue(deannotateFix.isAvailable(myProject, editor, file));

    final PsiModifierListOwner container = DeannotateIntentionAction.getContainer(editor, file);
    startListening(container, AnnotationUtil.NOT_NULL, true);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(myProject).deannotate(container, AnnotationUtil.NOT_NULL);
      }
    }.execute();
    stopListeningAndCheckEvents();

    FileDocumentManager.getInstance().saveAllDocuments();

    IntentionAction fix = myFixture.findSingleIntention(hint1);
    assertNotNull(fix);

    fix = myFixture.findSingleIntention(hint2);
    assertNotNull(fix);

    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
  }

  public void testReadingOldPersistenceFormat() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("content/anno/persistence/annotations.xml");
    myFixture.configureByFiles("lib/persistence/Test.java");


    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myProject);
    PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    PsiParameter parameter = method.getParameterList().getParameters()[0];

    assertNotNull(manager.findExternalAnnotation(method, AnnotationUtil.NULLABLE));
    assertNotNull(manager.findExternalAnnotation(method, AnnotationUtil.NLS));
    assertEquals(2, manager.findExternalAnnotations(method).length);

    assertNotNull(manager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL));
    assertNotNull(manager.findExternalAnnotation(parameter, AnnotationUtil.NON_NLS));
    assertEquals(2, manager.findExternalAnnotations(parameter).length);
  }

  private static void assertMethodAndParameterAnnotationsValues(ExternalAnnotationsManager manager,
                                                         PsiMethod method,
                                                         PsiParameter parameter,
                                                         String expectedValue) {
    PsiAnnotation methodAnnotation = manager.findExternalAnnotation(method, AnnotationUtil.NULLABLE);
    assertNotNull(methodAnnotation);
    assertEquals(expectedValue, methodAnnotation.findAttributeValue("value").getText());

    PsiAnnotation parameterAnnotation = manager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL);
    assertNotNull(parameterAnnotation);
    assertEquals(expectedValue, parameterAnnotation.findAttributeValue("value").getText());
  }

  public void testEditingMultiRootAnnotations() {
    addLibrary("/content/annoMultiRoot/root1", "/content/annoMultiRoot/root2");
    myFixture.configureByFiles("/content/annoMultiRoot/root1/multiRoot/annotations.xml",
                               "/content/annoMultiRoot/root2/multiRoot/annotations.xml");
    myFixture.configureByFiles("lib/multiRoot/Test.java");

    final ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myProject);
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    final PsiParameter parameter = method.getParameterList().getParameters()[0];

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"foo\"");

    final PsiAnnotation annotationFromText =
      JavaPsiFacade.getElementFactory(myProject).createAnnotationFromText("@Annotation(value=\"bar\")", null);

    startListening(method, AnnotationUtil.NULLABLE, true);
    new WriteCommandAction(myProject) {
      @Override
      protected void run(final Result result) throws Throwable {
        manager.editExternalAnnotation(method, AnnotationUtil.NULLABLE, annotationFromText.getParameterList().getAttributes());
      }
    }.execute();
    stopListeningAndCheckEvents();

    startListening(parameter, AnnotationUtil.NOT_NULL, true);
    new WriteCommandAction(myProject) {
      @Override
      protected void run(final Result result) throws Throwable {
        manager.editExternalAnnotation(parameter, AnnotationUtil.NOT_NULL, annotationFromText.getParameterList().getAttributes());
      }
    }.execute();
    stopListeningAndCheckEvents();

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"bar\"");

    myFixture.checkResultByFile("content/annoMultiRoot/root1/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root1/multiRoot/annotations_after.xml", false);
    myFixture.checkResultByFile("content/annoMultiRoot/root2/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root2/multiRoot/annotations_after.xml", false);
  }

  public void testListenerNotifiedWhenOperationsFail() {
    addLibrary(); // no annotation roots: all operations should fail
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];

    startListening(method, AnnotationUtil.NOT_NULL, false);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(myProject).annotateExternally(method, AnnotationUtil.NOT_NULL, myFixture.getFile(), null);
      }
    }.execute();
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(myProject).editExternalAnnotation(method, AnnotationUtil.NOT_NULL, null);
      }
    }.execute();
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(myProject).deannotate(method, AnnotationUtil.NOT_NULL);
      }
    }.execute();
    stopListeningAndCheckEvents();
  }

  public void testListenerNotifiedOnExternalChanges() {
    addDefaultLibrary();
    myFixture.configureByFiles("/content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");

    ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotation(getOwner(), AnnotationUtil.NOT_NULL); // force creating service

    startListeningForExternalChanges();
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myFixture.getTempDirPath() + "/content/anno/p/annotations.xml");
        assert file != null;
        String newText = "  " + StreamUtil.readText(file.getInputStream()) + "      "; // adding newspace to the beginning and end of file
        FileUtil.writeToFile(VfsUtil.virtualToIoFile(file), newText); // writing using java.io.File to make this change external
        file.refresh(false, false);
      }
    }.execute();
    stopListeningAndCheckEvents();
  }

  private class DefaultAnnotationsListener extends ExternalAnnotationsListener.Adapter {
    @Override
    public void afterExternalAnnotationChanging(@Nonnull PsiModifierListOwner owner, @Nonnull String annotationFQName,
                                                boolean successful) {
      System.err.println("Unexpected ExternalAnnotationsListener.afterExternalAnnotationChanging event produced");
      System.err.println("owner = [" + owner + "], annotationFQName = [" + annotationFQName + "], successful = [" + successful + "]");
      myUnexpectedEventWasProduced = true;
    }

    @Override
    public void externalAnnotationsChangedExternally() {
      System.err.println("Unexpected ExternalAnnotationsListener.externalAnnotationsChangedExternally event produced");
      myUnexpectedEventWasProduced = true;
    }
  }
}
