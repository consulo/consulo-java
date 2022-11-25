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

package consulo.java.impl.module.extension;

import consulo.content.bundle.Sdk;
import consulo.util.lang.Comparing;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.module.extension.ModuleExtensionWithSdk;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 15-Nov-16.
 */
public class LazyValueBySdk<T> {
  private ModuleExtensionWithSdk<?> myExtension;

  private NotNullFunction<Sdk, T> myFunc;

  private final T myDefaultValue;

  private volatile T myValue;

  private Sdk myLastSdk;

  public LazyValueBySdk(@Nonnull ModuleExtensionWithSdk<?> extension, @Nonnull T defaultValue, @Nonnull NotNullFunction<Sdk, T> func) {
    myExtension = extension;
    myFunc = func;
    myDefaultValue = defaultValue;
    myValue = defaultValue;
  }

  public T getValue() {
    Sdk lastSdk = myLastSdk;
    Sdk currentSdk = myExtension.getSdk();

    T value = myValue;

    if (currentSdk == null) {
      value = myDefaultValue;
    } else if (!Comparing.equal(lastSdk, currentSdk)) {
      myLastSdk = currentSdk;
      value = myFunc.apply(currentSdk);
    }

    myValue = value;
    return value;
  }
}
