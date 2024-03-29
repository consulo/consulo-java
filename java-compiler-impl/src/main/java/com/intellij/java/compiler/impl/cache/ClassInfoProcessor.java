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
 * @author: Eugene Zhuravlev
 * Date: May 14, 2003
 * Time: 10:42:29 AM
 */
package com.intellij.java.compiler.impl.cache;


import consulo.compiler.CacheCorruptedException;

public interface ClassInfoProcessor {
  /**
   * @param classQName of a class info to be processed
   * @return true if superclasses of info should be processed and false otherwise
   */
  boolean process(int classQName) throws CacheCorruptedException;
}
