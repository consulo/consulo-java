/*
 * Copyright 2013-2014 must-be.org
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

package consulo.java.analysis.impl.codeInsight;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import org.jetbrains.annotations.PropertyKey;
import consulo.component.util.localize.AbstractBundle;

/**
 * @author VISTALL
 * @since 15.07.14
 */
@Deprecated
@DeprecationInfo("Use JavaInspectionsLocalize")
@MigratedExtensionsTo(JavaInspectionsLocalize.class)
public class JavaInspectionsBundle extends AbstractBundle
{
	public static final String BUNDLE = "messages.JavaInspectionsBundle";

	private static final JavaInspectionsBundle ourInstance = new JavaInspectionsBundle();

	private JavaInspectionsBundle()
	{
		super(BUNDLE);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaInspectionsBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaInspectionsBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}
