package com.intellij.refactoring;

import static org.junit.Assert.assertTrue;

import com.intellij.JavaTestUtil;
import consulo.codeEditor.Editor;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;

/**
 *  @author dsl
 */
public abstract class IntroduceVariableMultifileTest extends MultiFileTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    //LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/introduceVariable/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSamePackageRef() throws Exception {
    doTest(
      createAction("pack1.A",
                   new MockIntroduceVariableHandler("b", false, false, false, "pack1.B")
      )
    );
  }

  public void testGenericTypeWithInner() throws Exception {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericTypeWithInner1() throws Exception {
    doTest(
      createAction("test.Client",
                   new MockIntroduceVariableHandler("l", false, true, true, "test.List<test.A.B>")
      )
    );
  }

  public void testGenericWithTwoParameters() throws Exception {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "util.Pair<java.lang.String,util.Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  public void testGenericWithTwoParameters2() throws Exception {
    doTest(
      createAction("Client",
                   new MockIntroduceVariableHandler("p", false, false, true,
                                                    "Pair<java.lang.String,Pair<java.lang.Integer,java.lang.Boolean>>")
      )
    );
  }

  PerformAction createAction(final String className, final IntroduceVariableBase testMe) {
    return new PerformAction() {
      @Override
      public void performAction(VirtualFile vroot, VirtualFile rootAfter) {
        final JavaPsiFacade psiManager = getJavaFacade();
        final PsiClass aClass = psiManager.findClass(className, GlobalSearchScope.allScope(myProject));
        assertTrue(className + " class not found", aClass != null);
        final PsiFile containingFile = aClass.getContainingFile();
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        assertTrue(virtualFile != null);
        final Editor editor = createEditor(virtualFile);
        setupCursorAndSelection(editor);
        testMe.invoke(myProject, editor, containingFile, null);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }
}
