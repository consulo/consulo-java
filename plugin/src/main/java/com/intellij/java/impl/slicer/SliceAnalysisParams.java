/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.slicer;

import consulo.language.editor.scope.AnalysisScope;

/**
 * User: cdr
 */
public class SliceAnalysisParams {
  public boolean dataFlowToThis = true; // to/from this
  public boolean showInstanceDereferences = true; // show method calls or field access on the variable being analysed
  public AnalysisScope scope;

  public SliceAnalysisParams() {
  }
}
