package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.testFramework.PsiTestCase;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

/**
 *  @author dsl
 */
public abstract class ReferenceParameterListTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance(ReferenceParameterListTest.class);
  public void testParameterListInExtends() throws Exception {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiClass classFromText = factory.createClassFromText("class X extends Y<Z, W> {}", null);
    final PsiClass classX = classFromText.getInnerClasses()[0];
    final PsiJavaCodeReferenceElement[] extendsOfX = classX.getExtendsList().getReferenceElements();
    assertEquals(1, extendsOfX.length);
    final PsiJavaCodeReferenceElement ref = extendsOfX[0];
    assertEquals("Y<Z,W>", ref.getCanonicalText());
    assertEquals("Y", ref.getReferenceName());
    final PsiTypeElement[] refParams = ref.getParameterList().getTypeParameterElements();
    assertEquals(2, refParams.length);
    assertEquals("Z", refParams[0].getType().getCanonicalText());
    assertEquals("W", refParams[1].getType().getCanonicalText());
    final PsiType refType = factory.createType(ref);
    assertEquals("Y<Z,W>", refType.getCanonicalText());
    final PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType) refType).getReference();
    assertEquals("Y<Z,W>", reference.getCanonicalText());
  }
  public void testResolvableParameterListInExtends() throws Exception {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final PsiClass classFromText = factory.createClassFromText(
            "class Z {} class W{}" +
            "class Y<A, B> {} " +
            "class X extends Y<Z, W> {}",
            null);


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          classFromText.setName("Q");
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    final PsiClass classX = classFromText.getInnerClasses()[3];
    final PsiJavaCodeReferenceElement[] extendsOfX = classX.getExtendsList().getReferenceElements();
    assertEquals(1, extendsOfX.length);
    final PsiJavaCodeReferenceElement ref = extendsOfX[0];
    assertEquals("Q.Y<Q.Z,Q.W>", ref.getCanonicalText());
    assertEquals("Y", ref.getReferenceName());
    final PsiTypeElement[] refParams = ref.getParameterList().getTypeParameterElements();
    assertEquals(2, refParams.length);
    assertEquals("Q.Z", refParams[0].getType().getCanonicalText());
    assertEquals("Q.W", refParams[1].getType().getCanonicalText());
    final PsiType refType = factory.createType(ref);
    assertEquals("Q.Y<Q.Z,Q.W>", refType.getCanonicalText());
    final PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType) refType).getReference();
    assertEquals("Q.Y<Q.Z,Q.W>", reference.getCanonicalText());
  }
}
