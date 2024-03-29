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
package com.intellij.java.impl.psi.impl.source.tree;

import consulo.language.ast.TokenType;
import consulo.language.ast.TokenSet;

/**
 * @author yole
 */
public interface JavaJspElementType {
  TokenSet WHITE_SPACE_BIT_SET = TokenSet.create(TokenType.WHITE_SPACE/*,
                                                 JspElementType.JSP_TEMPLATE_EXPRESSION*/);
}
