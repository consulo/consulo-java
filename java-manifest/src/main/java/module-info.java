/**
 * @author VISTALL
 * @since 05/12/2022
 */
module consulo.java.manifest {
	requires transitive consulo.java.language.impl;

	// TODO remove in future
	requires java.desktop;

	exports consulo.java.manifest;
	exports consulo.java.manifest.editor;
	exports consulo.java.manifest.editor.completionProviders;
	exports consulo.java.manifest.editor.models;
	exports consulo.java.manifest.lang;
	exports consulo.java.manifest.lang.headerparser;
	exports consulo.java.manifest.lang.headerparser.impl;
	exports org.osmorc.manifest;
	exports org.osmorc.manifest.codeInspection;
	exports org.osmorc.manifest.lang;
	exports org.osmorc.manifest.lang.headerparser;
	exports org.osmorc.manifest.lang.headerparser.impl;
	exports org.osmorc.manifest.lang.psi;
	exports org.osmorc.manifest.lang.psi.elementtype;
	exports org.osmorc.manifest.lang.psi.impl;
	exports org.osmorc.manifest.lang.psi.stub;
	exports org.osmorc.manifest.lang.psi.stub.impl;
	exports org.osmorc.manifest.lang.valueparser;
	exports org.osmorc.manifest.lang.valueparser.impl;
	exports org.osmorc.manifest.lang.valueparser.impl.valueobject;
}