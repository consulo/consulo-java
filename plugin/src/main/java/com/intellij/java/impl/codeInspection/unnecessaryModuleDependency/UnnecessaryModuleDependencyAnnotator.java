package com.intellij.java.impl.codeInspection.unnecessaryModuleDependency;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.util.dataholder.Key;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private final RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(RefManager manager) {
    myManager = manager;
  }

  @Override
  @RequiredReadAction
  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    PsiElement onElement = refWhat.getElement();
    PsiElement fromElement = refFrom.getElement();
    if (onElement != null && fromElement!= null){
      Module onModule = onElement.getModule();
      Module fromModule = fromElement.getModule();
      if (onModule != null && fromModule != null && onModule != fromModule){
        RefModule refModule = myManager.getRefModule(fromModule);
        if (refModule != null) {
          Set<Module> modules = refModule.getUserData(DEPENDENCIES);
          if (modules == null){
            modules = new HashSet<Module>();
            refModule.putUserData(DEPENDENCIES, modules);
          }
          modules.add(onModule);
        }
      }
    }
  }
}
