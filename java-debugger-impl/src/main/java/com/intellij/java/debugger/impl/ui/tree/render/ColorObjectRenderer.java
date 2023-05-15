/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.annotation.component.ExtensionImpl;
import consulo.internal.com.sun.jdi.*;
import consulo.java.debugger.impl.tree.render.RGBColorObjectRender;
import consulo.ui.color.RGBColor;
import consulo.ui.image.Image;
import jakarta.inject.Inject;

/**
 * Created by Egor on 04.10.2014.
 */
@ExtensionImpl
public class ColorObjectRenderer extends ToStringBasedRenderer
{
	@Inject
	public ColorObjectRenderer(final NodeRendererSettings rendererSettings)
	{
		super(rendererSettings, "Color", null, null);
		setClassName("java.awt.Color");
		setEnabled(true);
	}

	@Override
	public Image calcValueIcon(ValueDescriptor descriptor,
							   EvaluationContext evaluationContext,
							   DescriptorLabelListener listener) throws EvaluateException
	{
		final Value value = descriptor.getValue();
		if(value instanceof ObjectReference)
		{
			try
			{
				final ObjectReference objRef = (ObjectReference) value;
				final ReferenceType refType = objRef.referenceType();

				Integer intValue = getFieldValue(refType, objRef, "value");
				if(intValue != null)
				{
					return RGBColorObjectRender.debuggerColorIcon(RGBColor.fromRGBValue(intValue));
				}
			}
			catch(Exception e)
			{
				throw new EvaluateException(e.getMessage(), e);
			}
		}
		return null;
	}

	public static Integer getFieldValue(ReferenceType refType, ObjectReference objRef, String fieldName) throws EvaluateException
	{
		final Field valueField = refType.fieldByName(fieldName);
		if(valueField != null)
		{
			final Value rgbValue = objRef.getValue(valueField);
			if(rgbValue instanceof IntegerValue)
			{
				return ((IntegerValue) rgbValue).value();
			}
		}
		return null;
	}
}
