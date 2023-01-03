/**
 * @author VISTALL
 * @since 02/12/2022
 */
module consulo.java.indexing.api {
	requires transitive consulo.java.language.api;
	
	exports com.intellij.java.indexing.search.searches;
}