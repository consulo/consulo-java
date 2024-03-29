/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import consulo.document.Document;
import consulo.codeEditor.EditorFactory;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.codeEditor.event.EditorEventMulticaster;
import consulo.document.FileDocumentManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.impl.ast.FileElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StubTree;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.util.PsiUtilBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.ide.impl.idea.util.FileContentUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@PlatformTestCase.WrapInCommand
public abstract class SmartPsiElementPointersTest extends CodeInsightTestCase {
  private VirtualFile myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/codeEditor/smartPsiElementPointers";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testChangeInDocument() {
    PsiClass aClass = myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  // This test is unfair. If pointer would be asked for getElement() between commits it'll never restore again anyway.
  //
  public void testChangeInDocumentTwice() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/*");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    document.insertString(offset + 2, "*/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testGetElementWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    document.insertString(offset, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testKeepBeltWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/******/");

    pointer.getElement();

    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testChangeInPsi() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testPsiChangesWithLazyPointers() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    final SmartPsiElementPointer<PsiIdentifier> pointer =
      SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass.getNameIdentifier());
    final PsiComment javadoc =
      JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createCommentFromText("/** javadoc */", aClass);
    aClass.getParent().addBefore(javadoc, aClass);

    final PsiIdentifier elt = pointer.getElement();
    assertNotNull(elt);
    assertSame(elt, aClass.getNameIdentifier());
  }

  public void testTypePointer() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    final PsiTypeElement typeElement = myJavaFacade.findClass("Test",GlobalSearchScope.allScope(getProject())).getFields()[0].getTypeElement();

    SmartPsiElementPointer typePointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(typeElement);
    SmartPsiElementPointer classPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);

    Document aClassDocument = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());
    Document testDocument = PsiDocumentManager.getInstance(myProject).getDocument(typeElement.getContainingFile());
    assertNotSame(aClassDocument, testDocument);

    aClassDocument.insertString(aClass.getTextOffset(), "/**/");
    testDocument.insertString(typeElement.getTextOffset(), "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = typePointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiTypeElement);
    assertTrue(element.isValid());
    assertEquals(classPointer.getElement(), PsiUtil.resolveClassInType(((PsiTypeElement)element).getType()));
  }

  public void testCreatePointerInBeforeDocumentChange() {
    final PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    final SmartPsiElementPointer[] pointer = new SmartPsiElementPointer[1];
    int offset = aClass.getTextOffset();
    DocumentListener listener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        pointer[0] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
      }

      @Override
      public void documentChanged(DocumentEvent event) {
      }
    };
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(listener);
    try {
      document.insertString(offset, "/******/");
    }
    finally {
      multicaster.removeDocumentListener(listener);
    }

    pointer[0].getElement();

    document.insertString(0, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer[0].getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testCreatePointerWhenNoPsiFile() {
    myPsiManager.startBatchFilesProcessingMode(); // to use weak refs

    try {
      final PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
      assertNotNull(aClass);

      VirtualFile vFile = myRoot.findChild("AClass.java");
      assertNotNull(vFile);
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
      Document document = FileDocumentManager.getInstance().getDocument(vFile);

      final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);

      System.gc();
      /*
      PsiFile psiFile = myPsiManager.getFileManager().getCachedPsiFile(vFile);
      assertNull(psiFile);
      */

      document.insertString(0, "class Foo{}\n");

      PsiElement element = pointer.getElement();
      assertEquals(aClass, element);

      document.insertString(0, "/**/");
      psiDocumentManager.commitAllDocuments();

      if (aClass.isValid()) {
        aClass.getChildren();
      }

      element = pointer.getElement();
      assertNotNull(element);
      assertTrue(element instanceof PsiClass);
      assertTrue(element.isValid());
    }
    finally {
      myPsiManager.finishBatchFilesProcessingMode(); // to use weak refs
    }
  }

  public void testReplaceFile() throws IOException {
    VirtualFile vfile = myRoot.createChildData(this, "X.java");
    VfsUtil.saveText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    SmartPsiElementPointer classp = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    SmartPsiElementPointer filep = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass.getContainingFile());

    FileContentUtil.reparseFiles(myProject, Collections.<VirtualFile>singleton(vfile), true);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertFalse(aClass.isValid());

    PsiElement element = classp.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
    assertEquals(vfile, element.getContainingFile().getVirtualFile());

    element = filep.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiFile);
    assertTrue(element.isValid());
    assertEquals(vfile, element.getContainingFile().getVirtualFile());
  }

  public void testCreatePointerDoesNotLoadPsiTree() throws IOException {
    VirtualFile vfile = myRoot.createChildData(this, "X.java");
    VfsUtil.saveText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    PsiFileImpl file = (PsiFileImpl)aClass.getContainingFile();

    assertTreeLoaded(file, false);

    SmartPsiElementPointer p = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    assertNotNull(p);

    assertTreeLoaded(file, false);

    assertInstanceOf(p.getElement(), PsiClass.class);

    assertTreeLoaded(file, false);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    document.insertString(0, "/** asdasd */");
    documentManager.commitAllDocuments();

    // loaded tree
    assertTreeLoaded(file, true);

    assertInstanceOf(p.getElement(), PsiClass.class);

    assertTreeLoaded(file, true);
  }

  private static void assertTreeLoaded(PsiFileImpl file, boolean loaded) {
    FileElement treeElement = file.getTreeElement();
    assertEquals(loaded, treeElement != null);
    StubTree stubTree = file.getStubTree();
    assertEquals(loaded, stubTree == null);
  }

  public void testPointerDisambiguationAfterDupLine() throws Exception {
    PsiJavaFile file = (PsiJavaFile)configureByText(JavaFileType.INSTANCE, "class XXX{ void foo() { \n" +
                                       " <caret>foo();\n" +
                                       "}}");
    PsiClass aClass = file.getClasses()[0];
    assertNotNull(aClass);

    PsiReferenceExpression ref1 = PsiTreeUtil.getParentOfType(PsiUtilBase.getElementAtCaret(getEditor()), PsiReferenceExpression.class);
    SmartPsiElementPointer pointer1 = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(ref1);

    ctrlD();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Set<PsiReferenceExpression> refs = new HashSet<PsiReferenceExpression>();
    int offset=0;
    while (true) {
      offset = getEditor().getDocument().getText().indexOf("foo();", offset+1);
      if (offset == -1) break;
      PsiReferenceExpression ref2 = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiReferenceExpression.class);
      refs.add(ref2);
    }
    refs.remove(ref1);
    assertEquals(1, refs.size());
    PsiReferenceExpression ref2 = refs.iterator().next();
    assertNotSame(ref1, ref2);
    SmartPsiElementPointer pointer2 = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(ref2);
    assertNotSame(pointer1, pointer2);

    PsiElement element1 = pointer1.getElement();
    PsiElement element2 = pointer2.getElement();

    assertNotNull(element1);
    assertNotNull(element2);
    assertNotSame(element1, element2);

    assertFalse(SmartPointerManager.getInstance(myProject).pointToTheSameElement(pointer1, pointer2));
  }
}
