/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;

import java.util.Set;

/**
 * Represents a Java module declaration.
 *
 * @since 2016.3
 */
public interface PsiJavaModule extends NavigatablePsiElement, PsiNameIdentifierOwner, PsiModifierListOwner, PsiJavaDocumentedElement {
  String MODULE_INFO_CLASS = "module-info";
  String MODULE_INFO_FILE = MODULE_INFO_CLASS + ".java";
  String MODULE_INFO_CLS_FILE = MODULE_INFO_CLASS + ".class";
  String JAVA_BASE = "java.base";
  String AUTO_MODULE_NAME = "Automatic-Module-Name";

  /* See http://openjdk.java.net/jeps/261#Class-loaders, "Class loaders" */
  Set<String> UPGRADEABLE = Set.of(
      "java.activation", "java.compiler", "java.corba", "java.transaction", "java.xml.bind", "java.xml.ws", "java.xml.ws.annotation",
      "jdk.internal.vm.compiler", "jdk.xml.bind", "jdk.xml.ws");

  @Override
  PsiJavaModuleReferenceElement getNameIdentifier();

  @Override
  String getName();

  Iterable<PsiRequiresStatement> getRequires();

  Iterable<PsiPackageAccessibilityStatement> getExports();

  Iterable<PsiPackageAccessibilityStatement> getOpens();

  Iterable<PsiUsesStatement> getUses();

  Iterable<PsiProvidesStatement> getProvides();

  /**
   * Checks whether the module should be excluded from automatic resolution when loaded from the classpath.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code DO_NOT_RESOLVE_BY_DEFAULT}, preventing automatic resolution;
   *         {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  default boolean doNotResolveByDefault() {
    return false;
  }

  /**
   * Checks whether the module is marked as deprecated.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_DEPRECATED}, indicating that it is deprecated;
   * {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  default boolean warnDeprecated() {
    return false;
  }

  /**
   * Checks whether the module is deprecated and scheduled for removal in a future release.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_DEPRECATED_FOR_REMOVAL}, indicating that
   * it is deprecated and will be removed in a future release; {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  default boolean warnDeprecatedForRemoval() {
    return false;
  }

  /**
   * Checks whether the module is in incubating mode, meaning it is not yet standardized.
   * This method reads the module resolution attributes from {@code module-info.class}, as specified in JEP 11.
   *
   * @return {@code true} if the module is marked with {@code WARN_INCUBATING}, indicating that it is
   * in incubating mode; {@code false} otherwise.
   * @see <a href="https://openjdk.org/jeps/11">JEP 11: Incubator Modules</a>
   */
  default boolean warnIncubating() {
    return false;
  }
}