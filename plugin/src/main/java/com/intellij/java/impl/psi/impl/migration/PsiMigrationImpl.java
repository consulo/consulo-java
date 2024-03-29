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
package com.intellij.java.impl.psi.impl.migration;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMigration;
import consulo.application.ApplicationManager;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dsl
 */
public class PsiMigrationImpl implements PsiMigration {
  private static final Logger LOG = Logger.getInstance(PsiMigrationImpl.class);
  private final PsiMigrationManager myMigrationManager;
  private final JavaPsiFacade myFacade;
  private final PsiManager myManager;
  private final Map<String, MigrationClassImpl> myQNameToClassMap = new HashMap<String, MigrationClassImpl>();
  private final Map<String, List<PsiClass>>  myPackageToClassesMap = new HashMap<String, List<PsiClass>>();
  private final Map<String, MigrationPackageImpl> myQNameToPackageMap = new HashMap<String, MigrationPackageImpl>();
  private final Map<String, List<PsiJavaPackage>>  myPackageToSubpackagesMap = new HashMap<String, List<PsiJavaPackage>>();
  private boolean myIsValid = true;

  public PsiMigrationImpl(PsiMigrationManager migrationManager, JavaPsiFacade facade, PsiManager manager) {
    myMigrationManager = migrationManager;
    myFacade = facade;
    myManager = manager;
  }

  @Override
  public PsiClass createClass(String qualifiedName) {
    assertValid();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final MigrationClassImpl migrationClass = new MigrationClassImpl(this, qualifiedName);
    final MigrationClassImpl oldMigrationClass = myQNameToClassMap.put(qualifiedName, migrationClass);
    LOG.assertTrue(oldMigrationClass == null, qualifiedName);
    String packageName = parentPackageName(qualifiedName);
    final PsiJavaPackage aPackage = myFacade.findPackage(packageName);
    if (aPackage == null) {
      createPackage(packageName);
    }
    List<PsiClass> psiClasses = getClassesList(packageName);
    psiClasses.add(migrationClass);
    myMigrationManager.migrationModified(false);
    return migrationClass;
  }

  private List<PsiClass> getClassesList(String packageName) {
    assertValid();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<PsiClass> psiClasses = myPackageToClassesMap.get(packageName);
    if (psiClasses == null) {
      psiClasses = new ArrayList<PsiClass>();
      myPackageToClassesMap.put(packageName, psiClasses);
    }
    return psiClasses;
  }

  @Override
  public PsiJavaPackage createPackage(String qualifiedName) {
    assertValid();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final MigrationPackageImpl migrationPackage = new MigrationPackageImpl(this, qualifiedName);
   	myQNameToPackageMap.putIfAbsent(qualifiedName, migrationPackage);
    final String parentName = parentPackageName(qualifiedName);
    final PsiJavaPackage aPackage = myFacade.findPackage(parentName);
    if (aPackage == null) {
      createPackage(parentName);
    }
    List<PsiJavaPackage> psiPackages = getSubpackagesList(parentName);
    psiPackages.add(migrationPackage);
    myMigrationManager.migrationModified(false);
    return migrationPackage;
  }

  @Override
  public void finish() {
    assertValid();
    myQNameToClassMap.clear();
    myQNameToPackageMap.clear();
    myPackageToClassesMap.clear();
    myPackageToSubpackagesMap.clear();
    myIsValid = false;
    myMigrationManager.migrationModified(true);
  }

  private void assertValid() {
    LOG.assertTrue(myIsValid);
  }

  private List<PsiJavaPackage> getSubpackagesList(final String parentName) {
    assertValid();
    List<PsiJavaPackage> psiPackages = myPackageToSubpackagesMap.get(parentName);
    if (psiPackages == null) {
      psiPackages = new ArrayList<PsiJavaPackage>();
      myPackageToSubpackagesMap.put(parentName, psiPackages);
    }
    return psiPackages;
  }

  public List<PsiClass> getMigrationClasses(String packageName) {
    assertValid();
    return getClassesList(packageName);
  }

  public List<PsiJavaPackage> getMigrationPackages(String packageName) {
    assertValid();
    return getSubpackagesList(packageName);
  }

  public PsiClass getMigrationClass(String qualifiedName) {
    assertValid();
    return myQNameToClassMap.get(qualifiedName);
  }

  public PsiJavaPackage getMigrationPackage(String qualifiedName) {
    assertValid();
    return myQNameToPackageMap.get(qualifiedName);
  }


  private static String parentPackageName(String qualifiedName) {
    final int lastDotIndex = qualifiedName.lastIndexOf('.');
    return lastDotIndex >= 0 ? qualifiedName.substring(0, lastDotIndex) : "";
  }

  PsiManager getManager() {
    return myManager;
  }

  boolean isValid() {
    return myIsValid;
  }
}
