/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ig.maturity;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.PsiTodoSearchHelper;
import consulo.language.psi.search.TodoItem;

public class TodoUtil {
  private TodoUtil() {
    super();
  }

  public static boolean isTodoComment(PsiComment comment) {
    PsiFile file = comment.getContainingFile();
    PsiTodoSearchHelper searchHelper = PsiTodoSearchHelper.getInstance(comment.getProject());
    TodoItem[] todoItems = searchHelper.findTodoItems(file);
    for (TodoItem todoItem : todoItems) {
      TextRange commentTextRange = comment.getTextRange();
      TextRange todoTextRange = todoItem.getTextRange();
      if (commentTextRange.contains(todoTextRange)) {
        return true;
      }
    }
    return false;
  }
}