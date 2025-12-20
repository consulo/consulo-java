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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AddNullableAnnotationIntention", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class AddNullableAnnotationIntention extends AddAnnotationIntention {
  @Nonnull
  @Override
  public Pair<String, String[]> getAnnotations(@Nonnull Project project) {
    return new Pair<String, String[]>(NullableNotNullManager.getInstance(project).getDefaultNullable(), getNotNulls(project));
  }

  @Nonnull
  private static String[] getNotNulls(@Nonnull Project project) {
    List<String> notnulls = NullableNotNullManager.getInstance(project).getNotNulls();
    return ArrayUtil.toStringArray(notnulls);
  }
}
