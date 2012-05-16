<?xml version="1.0" encoding="UTF-8"?>
<!-- ============================================================
     Docbook customization

     This file solves the issue of docbook using the <h2> elements
     both for chapter and for the first level of sections heading.
     ============================================================= -->
<xsl:stylesheet xmlns:xsl    = "http://www.w3.org/1999/XSL/Transform"
                xmlns:d      = "http://docbook.org/ns/docbook"
                xmlns:xslthl = "http://xslthl.sf.net"
                xmlns        = "http://www.w3.org/1999/xhtml"
                exclude-result-prefixes="xslthl d" version="1.0">
  <!-- Symbolic URN specific to docbkx-maven-plugin -->
  <xsl:import href="urn:docbkx:stylesheet/docbook.xsl"/>
  <xsl:import href="urn:docbkx:stylesheet/highlight.xsl"/>

  <!--
       Following is copied from "http://docbook.sourceforge.net/release/xsl/current/xhtml/component.xsl".
       Only the lines identified by a comment have been modified.  This file is used only for building
       the web site and is not included in the Geotk distributions.
  -->

  <xsl:template name="component.title">
    <xsl:param name="node" select="."/>
    <xsl:variable name="level">
      <xsl:choose>
        <xsl:when test="ancestor::section">
          <xsl:value-of select="count(ancestor::section)+1"/>
        </xsl:when>
        <xsl:when test="ancestor::sect5">6</xsl:when>
        <xsl:when test="ancestor::sect4">5</xsl:when>
        <xsl:when test="ancestor::sect3">4</xsl:when>
        <xsl:when test="ancestor::sect2">3</xsl:when>
        <xsl:when test="ancestor::sect1">2</xsl:when>
        <xsl:when test="ancestor::sect0">1</xsl:when>   <!-- Added -->
        <xsl:otherwise>0</xsl:otherwise>  <!-- Was 1, changed to 0 -->
      </xsl:choose>
    </xsl:variable>
    <xsl:element name="h{$level+1}" namespace="http://www.w3.org/1999/xhtml">
      <xsl:attribute name="class">title</xsl:attribute>
      <xsl:if test="$generate.id.attributes = 0">
        <xsl:call-template name="anchor">
	  <xsl:with-param name="node" select="$node"/>
          <xsl:with-param name="conditional" select="0"/>
        </xsl:call-template>
      </xsl:if>
      <xsl:apply-templates select="$node" mode="object.title.markup">
        <xsl:with-param name="allow-anchors" select="1"/>
      </xsl:apply-templates>
    </xsl:element>
  </xsl:template>

  <!--
       Syntax highlighting partially copied from the "docbook/xhtml-1_1/highlight.xsl" file
       in the "net/sf/docbook/docbook-xsl/docbook-xsl-ns-resources.zip" archive, then edited.
  -->
  <xsl:template match="xslthl:string" mode="xslthl">
    <span class="hl-string">
      <xsl:apply-templates mode="xslthl"/>
    </span>
  </xsl:template>
  <xsl:template match="xslthl:annotation" mode="xslthl">
    <span class="hl-annotation">
      <xsl:apply-templates mode="xslthl"/>
    </span>
  </xsl:template>
  <xsl:template match="xslthl:doccomment|xslthl:doctype" mode="xslthl">
    <span class="hl-doccomment">
      <xsl:apply-templates mode="xslthl"/>
    </span>
  </xsl:template>


  <!--
       Following should work according "http://lists.oasis-open.org/archives/docbook-apps/201204/msg00107.html".
       But the template seems to never match...
       Docbkx customization is described at "http://docbkx-tools.sourceforge.net/advanced.html".
  -->

  <xsl:template match="classname[@role = 'OGC']" mode="class.value">
    <xsl:value-of select="'OGC'"/>
  </xsl:template>

</xsl:stylesheet>
