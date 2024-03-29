/*
 * Copyright 2013-2016 must-be.org
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

package consulo.java.language.psi;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import consulo.language.version.LanguageVersion;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 09-Dec-16.
 */
@SuppressWarnings("ExtensionImplIsNotAnnotatedInspection")
public class JavaLanguageVersion extends LanguageVersion {
  private LanguageLevel myLanguageLevel;

  public JavaLanguageVersion(String id, String name, LanguageLevel languageLevel) {
    super(id, name, JavaLanguage.INSTANCE);
    myLanguageLevel = languageLevel;
  }

  @Nonnull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }
}
