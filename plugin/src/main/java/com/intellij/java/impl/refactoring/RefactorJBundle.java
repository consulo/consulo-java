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
package com.intellij.java.impl.refactoring;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.application.CommonBundle;
import consulo.java.localize.JavaRefactoringLocalize;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

@Deprecated
@DeprecationInfo("Use JavaRefactoringLocalize")
@MigratedExtensionsTo(JavaRefactoringLocalize.class)
public class RefactorJBundle{
    private static final ResourceBundle ourBundle =
            ResourceBundle.getBundle("com.intellij.refactoring.RefactorJBundle");

    private RefactorJBundle(){
    }

    public static String message(@PropertyKey(resourceBundle = "com.intellij.refactoring.RefactorJBundle")String key,
                                 Object... params){
        return CommonBundle.message(ourBundle, key, params);
    }
}
