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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:38 PM
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AddDeprecationAnnotationIntention", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class AddDeprecationAnnotationIntention extends AddAnnotationIntention {
  @Nonnull
  @Override
  public Pair<String, String[]> getAnnotations(@Nonnull Project project) {
    return new Pair<String, String[]>("java.lang.annotation.Deprecated", ArrayUtil.EMPTY_STRING_ARRAY);
  }
}