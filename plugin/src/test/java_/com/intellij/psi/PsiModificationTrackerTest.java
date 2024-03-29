package com.intellij.psi;

import com.intellij.java.language.impl.JavaFileType;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.SelectionModel;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiModificationTracker;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import consulo.application.util.function.Processor;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiModificationTrackerTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testAnnotationNotChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"zz\")\n" +
                  "public class Foo { <selection></selection>}",
                  "hi");
  }

  public void testAnnotationNameChanged() throws Exception {
    doReplaceTest("@Suppr<selection>ess</selection>Warnings(\"zz\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationParameterChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"<selection>zz</selection>\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")</selection>\n" +
                  "public class Foo { }",
                  "");
  }

  public void testAnnotationWithClassRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public </selection> class Foo { }",
                  "");
  }

  public void testRemoveAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "  <selection>  " +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void method() {}\n" +
                  "</selection>" +
                  "}",
                  "");
  }

  public void testRenameAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void me<selection>th</selection>od() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRenameAnnotatedClass() throws Exception {
    doReplaceTest("   @SuppressWarnings(\"\")\n" +
                  "public class F<selection>o</selection>o {\n" +
                  "    public void method() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRemoveAll() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public  class Foo { }</selection>",
                  "");
  }

  public void testRemoveFile() throws Exception {
    doTest("<selection>@SuppressWarnings(\"zz\")\n" +
           "public  class Foo { }</selection>",
           new Processor<PsiFile>() {
             @Override
             public boolean process(PsiFile psiFile) {
               try {
                 final VirtualFile vFile = psiFile.getVirtualFile();
                 assert vFile != null : psiFile;
                 FileEditorManager.getInstance(getProject()).closeFile(vFile);
                 vFile.delete(this);
               }
               catch (IOException e) {
                 fail(e.getMessage());
               }
               return false;
             }
           });
  }

  private void doReplaceTest(@NonNls String text, @NonNls final String with) {
    doTest(text, new Processor<PsiFile>() {
      @Override
      public boolean process(PsiFile psiFile) {
        replaceSelection(with);
        return false;
      }
    });
  }

  private void doTest(@NonNls String text, Processor<PsiFile> run) {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, text);
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getModificationCount();
    run.process(file);
    assertFalse(modificationTracker.getModificationCount() == count);
  }

  private void replaceSelection(final String with) {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        SelectionModel sel = myFixture.getEditor().getSelectionModel();
        myFixture.getEditor().getDocument().replaceString(sel.getSelectionStart(), sel.getSelectionEnd(), with);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }.execute();
  }

  public void testJavaStructureModificationChangesAfterPackageDelete() {
    PsiFile file = myFixture.addFileToProject("/x/y/Z.java", "text");
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getJavaStructureModificationCount();

    file.getContainingDirectory().delete();

    assertEquals(count + 1, modificationTracker.getJavaStructureModificationCount());
  }
}
