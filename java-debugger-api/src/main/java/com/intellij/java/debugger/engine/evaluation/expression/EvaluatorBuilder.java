/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.debugger.engine.evaluation.expression;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentFactory;
import consulo.language.psi.PsiElement;

/**
 * Main interface to extend evaluation for different JVM languages.
 * @see CodeFragmentFactory
 */
public interface EvaluatorBuilder {
  ExpressionEvaluator build(PsiElement codeFragment, final SourcePosition position) throws EvaluateException;
}
