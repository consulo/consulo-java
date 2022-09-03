package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.impl.psi.impl.light.LightClass;
import consulo.util.collection.ArrayUtil;
import javax.annotation.Nonnull;

import java.util.Map;

public class MoveJavaClassesInFileHandler extends MoveAllClassesInFileHandler {

  @Override
  public void processMoveAllClassesInFile(@Nonnull Map<PsiClass, Boolean> allClasses, PsiClass psiClass, PsiElement... elementsToMove) {
    if (psiClass instanceof LightClass) return;
    final PsiClassOwner containingFile = (PsiClassOwner)psiClass.getContainingFile();
    final PsiClass[] classes = containingFile.getClasses();
    boolean all = true;
    for (PsiClass aClass : classes) {
      if (ArrayUtil.find(elementsToMove, aClass) == -1) {
        all = false;
        break;
      }
    }
    for (PsiClass aClass : classes) {
      allClasses.put(aClass, all);
    }
  }
}
