/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.internal.com.sun.jdi.Type;
import consulo.logging.Logger;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

public abstract class TypeRenderer implements Renderer
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ReferenceRenderer");
	protected BasicRendererProperties myProperties = new BasicRendererProperties(false);

	protected TypeRenderer()
	{
		this(CommonClassNames.JAVA_LANG_OBJECT);
	}

	protected TypeRenderer(@Nonnull String className)
	{
		myProperties.setClassName(className);
	}

	public String getClassName()
	{
		return myProperties.getClassName();
	}

	public void setClassName(String className)
	{
		myProperties.setClassName(className);
	}

	@Override
	public Renderer clone()
	{
		try
		{
			final TypeRenderer cloned = (TypeRenderer) super.clone();
			cloned.myProperties = myProperties.clone();
			return cloned;
		}
		catch(CloneNotSupportedException e)
		{
			LOG.error(e);
		}
		return null;
	}

	@Override
	public boolean isApplicable(Type type)
	{
		return DebuggerUtils.instanceOf(type, getClassName());
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException
	{
		myProperties.writeExternal(element);
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException
	{
		myProperties.readExternal(element);
	}

	protected CachedEvaluator createCachedEvaluator()
	{
		return new CachedEvaluator()
		{
			@Override
			protected String getClassName()
			{
				return TypeRenderer.this.getClassName();
			}
		};
	}
}
