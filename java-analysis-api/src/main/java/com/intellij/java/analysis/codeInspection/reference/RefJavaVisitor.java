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
package com.intellij.java.analysis.codeInspection.reference;

import consulo.language.editor.inspection.reference.RefVisitor;

import jakarta.annotation.Nonnull;

/**
 * Visitor for reference graph nodes.
 *
 * @see RefEntity#accept
 * @see RefManager#iterate
 * @since 6.0
 */
public class RefJavaVisitor extends RefVisitor
{
  public void visitField(@Nonnull RefField field) {
    visitElement(field);
  }

  public void visitMethod(@Nonnull RefMethod method) {
    visitElement(method);
  }

  public void visitParameter(@Nonnull RefParameter parameter) {
    visitElement(parameter);
  }

  public void visitClass(@Nonnull RefClass aClass) {
    visitElement(aClass);
  }

  public void visitPackage(@Nonnull RefPackage aPackage) {
    visitElement(aPackage);
  }
}
