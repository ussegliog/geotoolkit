/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2009-2011, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2009-2011, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotoolkit.internal.jaxb;

import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;
import java.util.GregorianCalendar;
import org.geotoolkit.xml.ObjectLinker;
import org.geotoolkit.xml.ObjectConverters;


/**
 * Thread-local status of a marshalling or unmarshalling process.
 *
 * @author Martin Desruisseaux (Geomatys)
 * @version 3.18
 *
 * @since 3.07
 * @module
 */
public final class MarshalContext {
    /**
     * The bit flag for enabling substitution of language codes by character strings.
     *
     * @since 3.18
     */
    public static final int SUBSTITUTE_LANGUAGE = 1;

    /**
     * The bit flag for enabling substitution of country codes by character strings.
     *
     * @since 3.18
     */
    public static final int SUBSTITUTE_COUNTRY = 2;

    /**
     * The thread-local context.
     */
    private static final ThreadLocal<MarshalContext> CURRENT = new ThreadLocal<MarshalContext>();

    /**
     * The object converters currently in use, or {@code null} for {@link ObjectConverters#DEFAULT}.
     */
    private ObjectConverters converters;

    /**
     * The object linker currently in use, or {@code null} for {@link ObjectLinker#DEFAULT}.
     *
     * @since 3.18
     */
    private ObjectLinker linker;

    /**
     * The base URL of ISO 19139 (or other standards) schemas. The valid values
     * are documented in the {@link org.geotoolkit.xml.XML#SCHEMAS} property.
     *
     * @since 3.17
     */
    private Map<String, String> schemas;

    /**
     * The locale to use for marshalling, or {@code null} if no locale were explicitly specified.
     *
     * @since 3.17
     */
    private Locale locale;

    /**
     * The timezone, or {@code null} if unspecified.
     * In the later case, an implementation-default (typically UTC) timezone is used.
     *
     * @since 3.17
     */
    private TimeZone timezone;

    /**
     * Various boolean attributes determines by the static constants above.
     *
     * @since 3.18
     */
    private int bitMasks;

    /**
     * {@code true} if a marshalling process is under progress.
     * The value is unchanged for unmarshalling processes.
     */
    private boolean isMarshalling;

    /**
     * The context which was previously used. This form a linked list allowing
     * to push properties (e.g. {@link #pushLocale(Locale)}) and pull back the
     * context to its previous state once finished.
     */
    private final MarshalContext previous;

    /**
     * Creates a new context. The new context is immediately set in the {@link #CURRENT} field.
     */
    private MarshalContext() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    /**
     * Inherits all configuration from the previous context, if any.
     */
    private void inherit() {
        final MarshalContext previous = this.previous;
        if (previous != null) {
            converters    = previous.converters;
            linker        = previous.linker;
            schemas       = previous.schemas;
            locale        = previous.locale;
            timezone      = previous.timezone;
            bitMasks      = previous.bitMasks;
            isMarshalling = previous.isMarshalling;
        }
    }

    /**
     * Returns the object linker in use for the current marshalling or unmarshalling process. If
     * no linker were explicitely set, then this method returns {@link ObjectLinker#DEFAULT}.
     *
     * @return The current object linker (never null).
     *
     * @since 3.18
     */
    public static ObjectLinker linker() {
        final MarshalContext current = CURRENT.get();
        if (current != null) {
            final ObjectLinker linker = current.linker;
            if (linker != null) {
                return linker;
            }
        }
        return ObjectLinker.DEFAULT;
    }

    /**
     * Returns the object converters in use for the current marshalling or unmarshalling process. If
     * no converter were explicitely set, then this method returns {@link ObjectConverters#DEFAULT}.
     *
     * @return The current object converters (never null).
     */
    public static ObjectConverters converters() {
        final MarshalContext current = CURRENT.get();
        if (current != null) {
            final ObjectConverters converters = current.converters;
            if (converters != null) {
                return converters;
            }
        }
        return ObjectConverters.DEFAULT;
    }

    /**
     * Returns the base URL of ISO 19139 (or other standards) schemas. The valid values
     * are documented in the {@link org.geotoolkit.xml.XML#SCHEMAS} property.
     *
     * @param  key One of the value documented in the "<cite>Map key</cite>" column of
     *         {@link org.geotoolkit.xml.XML#SCHEMAS}.
     * @return The base URL of the schema, or {@code null} if none were specified.
     *
     * @since 3.17
     */
    public static String schema(final String key) {
        final MarshalContext current = CURRENT.get();
        if (current != null) {
            final Map<String,String> schemas = current.schemas;
            if (schemas != null) {
                return schemas.get(key);
            }
        }
        return null;
    }

    /**
     * Returns the URL to a given code list in the given XML file. This method concatenates
     * the {@linkplain #schema(String) base schema URL} with the given directory, file and
     * identifier.
     *
     * @param  key One of the value documented in the "<cite>Map key</cite>" column of
     *         {@link org.geotoolkit.xml.XML#SCHEMAS}. Typical value is {@code "gmd"}.
     * @param  directory The directory to concatenate, for example {@code "resources/uom"}
     *         or {@code "resources/Codelist"} (<strong>no trailing {@code '/'}</strong>).
     * @param  file The XML file, for example {@code "gmxUom.xml"}, {@code "gmxCodelists.xml"}
     *         or {@code "ML_gmxCodelists.xml"} (<strong>no trailing {@code '#'}</strong>).
     * @param  identifier The UML identifier of the code list.
     * @return The URL to the given code list in the given schema.
     *
     * @since 3.17
     */
    public static String schema(final String key, final String directory, final String file, final String identifier) {
        final StringBuilder buffer = new StringBuilder(128);
        String base = schema(key);
        if (base == null) {
            base = "http://schemas.opengis.net/iso/19139/20070417/";
        }
        buffer.append(base);
        final int length = buffer.length();
        if (length != 0 && buffer.charAt(length - 1) != '/') {
            buffer.append('/');
        }
        return buffer.append(directory).append('/').append(file).append('#').append(identifier).toString();
    }

    /**
     * Returns whatever a marshalling process is under progress.
     *
     * @return {@code true} if a marshalling process is in progress.
     */
    public static boolean isMarshalling() {
        final MarshalContext current = CURRENT.get();
        return (current != null) ? current.isMarshalling : false;
    }

    /**
     * Creates a new Gregorian calendar for the current timezone and locale. If no locale or
     * timezone were explicitely set, then the default ones are used as documented in the
     * {@link org.geotoolkit.xml.XML#TIMEZONE} constant.
     *
     * @return A Gregorian calendar initialized with the current timezone and locale.
     *
     * @since 3.17
     */
    static GregorianCalendar createGregorianCalendar() {
        final MarshalContext current = CURRENT.get();
        if (current != null) {
            final Locale locale = current.locale;
            final TimeZone timezone = current.timezone;
            /*
             * Use the appropriate contructor rather than setting ourself the null values to
             * the default locale or timezone, because the JDK constructors perform a better
             * job of sharing existing timezone instances.
             */
            if (timezone != null) {
                return (locale != null) ? new GregorianCalendar(timezone, locale)
                                        : new GregorianCalendar(timezone);
            } else if (locale != null) {
                return new GregorianCalendar(locale);
            }
        }
        return new GregorianCalendar();
    }

    /**
     * Returns the timezone, or {@code null} if none were explicitely defined.
     * In the later case, an implementation-default (typically UTC) timezone is used.
     *
     * @return The timezone, or {@code null} if unspecified.
     *
     * @since 3.17
     */
    public static TimeZone getTimeZone() {
        final MarshalContext current = CURRENT.get();
        return (current != null) ? current.timezone : null;
    }

    /**
     * Returns the locale to use for marshalling, or {@code null} if no locale were explicitly
     * specified. A {@code null} value means that some locale-neutral language should be used
     * if available, or an implementation-default locale (typically English) otherwise.
     * <p>
     * When this method returns a null locale, callers shall select a default locale as documented
     * in the {@link org.geotoolkit.util.DefaultInternationalString#toString(Locale)} javadoc.  As
     * a matter of rule:
     * <p>
     * <ul>
     *   <li>If the locale is given to an {@code InternationalString.toString(Locale)} method,
     *       keep the {@code null} value since the international string is already expected to
     *       returns a "unlocalized" string in such case.</li>
     *   <li>Otherwise, if a {@code Locale} instance is really needed, use {@link Locale#UK}
     *       as an approximation (as documented in {@code DefaultInternationalString})</li>
     * </ul>
     *
     * @return The locale, or {@code null} is unspecified.
     *
     * @since 3.17
     */
    public static Locale getLocale() {
        final MarshalContext current = CURRENT.get();
        return (current != null) ? current.locale : null;
    }

    /**
     * Sets the locale to the given value. The old locales are remembered and will
     * be restored by the next call to {@link #pullLocale()}.
     *
     * @param locale The locale to set, or {@code null}.
     *
     * @since 3.17
     */
    public static void pushLocale(final Locale locale) {
        final MarshalContext current = new MarshalContext();
        current.inherit();
        if (locale != null) {
            current.locale = locale;
        }
    }

    /**
     * Restores the locale which was used prior the call to {@link #pushLocale(Locale)}.
     *
     * @since 3.17
     */
    public static void pullLocale() {
        final MarshalContext current = CURRENT.get();
        if (current != null) {
            current.finish();
        }
    }

    /**
     * Returns the bit masks. The returned value can be tested with the
     * {@link #SUBSTITUTE_LANGUAGE} and {@link #SUBSTITUTE_COUNTRY} masks.
     *
     * @return All bit masks. This is 0 if there is no bit set.
     *
     * @since 3.18
     */
    public static int getFlags() {
        final MarshalContext current = CURRENT.get();
        return (current != null) ? current.bitMasks : 0;
    }

    /**
     * Invoked when a marshalling or unmarshalling process is about to begin.
     * Must be followed by a call to {@link #finish()} in a {@code finally} block.
     *
     * {@preformat java
     *     MarshalContext ctx = begin(converters);
     *     try {
     *         ...
     *     } finally {
     *         ctx.finish();
     *     }
     * }
     *
     * @param  converters The converters in use.
     * @param  linker     The linker in use.
     * @param  schemas    The schemas root URL, or {@code null} if none.
     * @param  locale     The locale, or {@code null} if unspecified.
     * @param  timezone   The timezone, or {@code null} if unspecified.
     * @param  bitMasks   A combination of {@link #SUBSTITUTE_LANGUAGE}, {@link #SUBSTITUTE_COUNTRY} or others.
     * @return The context on which to invoke {@link #finish()} when the (un)marshalling is finished.
     */
    public static MarshalContext begin(final ObjectConverters converters, final ObjectLinker linker,
            final Map<String,String> schemas, final Locale locale, final TimeZone timezone, final int bitMasks)
    {
        final MarshalContext current = new MarshalContext();
        current.converters = converters;
        current.linker     = linker;
        current.schemas    = schemas; // NOSONAR: No clone, because this method is internal.
        current.locale     = locale;
        current.timezone   = timezone;
        current.bitMasks   = bitMasks;
        return current;
    }

    /**
     * Declares that the work which is about to begin is a marshalling.
     *
     * @see #isMarshalling()
     */
    public void setMarshalling() {
        isMarshalling = true;
    }

    /**
     * Invoked in a {@code finally} block when a unmarshalling process is finished.
     */
    public void finish() {
        if (previous != null) {
            CURRENT.set(previous);
        } else {
            CURRENT.remove();
        }
    }
}
