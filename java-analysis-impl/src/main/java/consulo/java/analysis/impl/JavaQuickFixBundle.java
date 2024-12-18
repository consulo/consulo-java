/*
 * Copyright 2013-2015 must-be.org
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

package consulo.java.analysis.impl;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import org.jetbrains.annotations.PropertyKey;
import consulo.component.util.localize.AbstractBundle;

/**
 * @author VISTALL
 * @since 27.12.2015
 */
@Deprecated
@DeprecationInfo("Use JavaQuickFixLocalize")
@MigratedExtensionsTo(JavaQuickFixLocalize.class)
public class JavaQuickFixBundle extends AbstractBundle
{
	private static final JavaQuickFixBundle ourInstance = new JavaQuickFixBundle();

	private JavaQuickFixBundle()
	{
		super("messages.JavaQuickFixBundle");
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaQuickFixBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaQuickFixBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}
