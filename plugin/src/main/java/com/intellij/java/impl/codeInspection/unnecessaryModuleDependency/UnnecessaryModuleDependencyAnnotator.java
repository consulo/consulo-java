package com.intellij.java.impl.codeInspection.unnecessaryModuleDependency;

import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefModule;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class UnnecessaryModuleDependencyAnnotator extends RefGraphAnnotator {
  public static final Key<Set<Module>> DEPENDENCIES = Key.create("inspection.dependencies");

  private final RefManager myManager;

  public UnnecessaryModuleDependencyAnnotator(final RefManager manager) {
    myManager = manager;
  }

  @Override
  public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
    final PsiElement onElement = refWhat.getElement();
    final PsiElement fromElement = refFrom.getElement();
    if (onElement != null && fromElement!= null){
      final Module onModule = ModuleUtil.findModuleForPsiElement(onElement);
      final Module fromModule = ModuleUtil.findModuleForPsiElement(fromElement);
      if (onModule != null && fromModule != null && onModule != fromModule){
        final RefModule refModule = myManager.getRefModule(fromModule);
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
