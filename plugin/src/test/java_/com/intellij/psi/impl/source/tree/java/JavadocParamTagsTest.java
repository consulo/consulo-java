package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.testFramework.LightIdeaTestCase;
import consulo.application.ApplicationManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.undoRedo.CommandProcessor;

/**
 *  @author dsl
 */
public abstract class JavadocParamTagsTest extends LightIdeaTestCase {
  public void testDeleteTag1() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        tags[0].delete();
      }
    });

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p2\n" +
                 " */", docComment.getText());

  }

  public void testDeleteTag2() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        tags[1].delete();
      }
    });

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p1\n" +
                 " */", docComment.getText());

  }

  public void testDeleteTag3() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " * @param p3\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        tags[1].delete();
      }
    });

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p1\n" +
                 " * @param p3\n" +
                 " */", docComment.getText());
  }

  public void testTagCreation() throws Exception {
    createAndTestTag("@param p1 Text", "p1", "Text");
    createAndTestTag("@param p2", "p2", "");
    createAndTestTag("@param p2 FirstLine\n * SecondLine", "p2", "FirstLine\nSecondLine");
  }

  public void testAddTag1() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.addAfter(tag2, tags[0]);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p1\n" +
      " * @param p2\n" +
      " */", docComment.getText());
  }

  public void testAddTag2() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.addBefore(tag2, tags[0]);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p2\n" +
      " * @param p1\n" +
      " */", docComment.getText());
  }

  public void testAddTag3() throws Exception {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          @Override
          public void run() {
            final PsiElementFactory factory = getFactory();
            final PsiJavaFile psiFile;
            try {
              psiFile = (PsiJavaFile)createFile("aaa.java", "class A {/**\n" +
                                                            " * Javadoc\n" +
                                                            " * @param p1\n" +
                                                            " * @param p3\n" +
                                                            " */\n" +
                                                            "void m();}");
            final PsiClass psiClass = psiFile.getClasses()[0];
            final PsiMethod method = psiClass.getMethods()[0];
            PsiDocComment docComment = method.getDocComment();
            assertNotNull(docComment);
            final PsiDocTag[] tags = docComment.getTags();
            final PsiDocTag tag2 = factory.createParamTag("p2", "");
            docComment.addAfter(tag2, tags[0]);
            docComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(docComment);
            assertEquals(
              "/**\n" +
              " * Javadoc\n" +
              " * @param p1\n" +
              " * @param p2\n" +
              " * @param p3\n" +
              " */", docComment.getText());
            }
            catch (IncorrectOperationException e) {}
          }
        });
      }
    }, "", null);
  }

  public void testAddTag4() throws Exception {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.add(tag2);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p2\n" +
      " */", docComment.getText());
  }

  private static PsiElementFactory getFactory() {
    final PsiManager manager = getPsiManager();
    return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
  }

  private static void createAndTestTag(String expectedText, String parameterName, String description) throws IncorrectOperationException {
    PsiElementFactory factory = getFactory();
    final PsiDocTag paramTag = factory.createParamTag(parameterName, description);
    assertEquals(expectedText, paramTag.getText());
  }
}
