/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.spi.parsing;

import com.intellij.java.language.spi.SPILanguage;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.tree.java.IKeywordElementType;

/**
 * User: anna
 */
public interface SPITokenType extends JavaTokenType {
  IElementType SHARP = new IKeywordElementType("SHARP");
  IElementType DOLLAR = new IKeywordElementType("DOLLAR");
  IElementType IDENTIFIER = new IElementType("IDENTIFIER", SPILanguage.INSTANCE);
}
