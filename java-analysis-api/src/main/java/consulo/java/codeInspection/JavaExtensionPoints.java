/*
 * Copyright 2013 Consulo.org
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
package consulo.java.codeInspection;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

/**
 * @author VISTALL
 * @since 10:38/21.05.13
 */
public interface JavaExtensionPoints
{
	@Nonnull
	ExtensionPointName<EntryPoint> DEAD_CODE_EP_NAME = ExtensionPointName.create("consulo.java.deadCode");

	@Nonnull
	ExtensionPointName<Condition<PsiElement>> CANT_BE_STATIC_EP_NAME = ExtensionPointName.create("consulo.java.cantBeStatic");
}
