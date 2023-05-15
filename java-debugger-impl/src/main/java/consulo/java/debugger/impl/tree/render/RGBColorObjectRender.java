package consulo.java.debugger.impl.tree.render;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.ColorObjectRenderer;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.impl.ui.tree.render.ToStringBasedRenderer;
import consulo.annotation.component.ExtensionImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Value;
import consulo.ui.color.RGBColor;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 15/05/2023
 */
@ExtensionImpl
public class RGBColorObjectRender extends ToStringBasedRenderer
{
	@Inject
	public RGBColorObjectRender(NodeRendererSettings rendererSettings)
	{
		super(rendererSettings, "RGBColor", null, null);
		setClassName("consulo.ui.color.RGBColor");
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

				Integer redValue = ColorObjectRenderer.getFieldValue(refType, objRef, "myRed");
				Integer greenValue = ColorObjectRenderer.getFieldValue(refType, objRef, "myGreen");
				Integer blueValue = ColorObjectRenderer.getFieldValue(refType, objRef, "myBlue");
				Integer alphaValue = ColorObjectRenderer.getFieldValue(refType, objRef, "myAlpha");
				if(redValue != null && greenValue != null && blueValue != null && alphaValue != null)
				{
					RGBColor color = new RGBColor(redValue, greenValue, blueValue, alphaValue);
					return debuggerColorIcon(color);

				}
			}
			catch(Exception e)
			{
				throw new EvaluateException(e.getMessage(), e);
			}
		}
		return null;
	}

	public static Image debuggerColorIcon(RGBColor color)
	{
		return ImageEffects.canvas(Image.DEFAULT_ICON_SIZE, Image.DEFAULT_ICON_SIZE, canvas2D ->
		{
			canvas2D.setFillStyle(color);
			canvas2D.fillRect(2, 2, 12, 12);
		});
	}
}
