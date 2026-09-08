package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeDeclarationContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeExpressionContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.localize.JavaLiveTemplateLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class JavaPlainLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    public String groupId() {
        return "javaplain";
    }

    @Override
    public LocalizeValue groupName() {
        return JavaLiveTemplateLocalize.groupNameJavaPlain();
    }

    @Override
    public void contribute(LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder("javaplainSt", "St", "String ", JavaLiveTemplateLocalize.descriptionSt())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
            builder.withContextsOf(JavaLikeDeclarationContextType.class, true);
            builder.withContextsOf(JavaLikeExpressionContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaplainPsf",
            "psf",
            "public static final ",
            JavaLiveTemplateLocalize.descriptionPsf()
        )) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaplainPsfi",
            "psfi",
            "public static final int ",
            JavaLiveTemplateLocalize.descriptionPsfi()
        )) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaplainPsfs",
            "psfs",
            "public static final String ",
            JavaLiveTemplateLocalize.descriptionPsfs()
        )) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaplainThr", "thr", "throw new ", JavaLiveTemplateLocalize.descriptionThr())) {
            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }
    }
}
