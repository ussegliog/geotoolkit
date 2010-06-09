/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2005-2010, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2007-2010, Geomatys
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
package org.geotoolkit.internal.sql.table;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import javax.sql.DataSource;
import java.lang.reflect.Constructor;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Properties;
import java.util.Calendar;
import java.util.ConcurrentModificationException;
import java.util.GregorianCalendar;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

import org.geotoolkit.factory.Hints;
import org.geotoolkit.lang.ThreadSafe;
import org.geotoolkit.util.Localized;
import org.geotoolkit.util.converter.Classes;
import org.geotoolkit.resources.Errors;
import org.geotoolkit.internal.sql.StatementPool;
import org.geotoolkit.internal.sql.AuthenticatedDataSource;


/**
 * Connection to a catalog database through JDBC (<cite>Java Database Connectivity</cite>).
 * The connection is specified by a {@link DataSource}, which should create pooled connections.
 *
 * {@section Concurrency}
 * This class is thread-safe and concurrent. However it is recommanded to access it only from a
 * limited number of threads (for example from a {@link java.util.concurrent.ThreadPoolExecutor})
 * and to recycle those threads, because this class may use a new connection for each thread.
 *
 * @author Martin Desruisseaux (IRD, Geomatys)
 * @version 3.13
 *
 * @since 3.09 (derived from Seagis)
 * @module
 */
@ThreadSafe(concurrent = true)
public class Database implements Localized {
    /**
     * The data source, which is mandatory. It is recommanded to provide a data source that
     * create pooled connections, because connections may be created and closed often.
     */
    private final AuthenticatedDataSource source;

    /**
     * The database catalog where the tables are defined, or {@code null} if none.
     */
    final String catalog;

    /**
     * The database schema where the tables are defined, or {@code null} if none.
     * If {@code null}, then the tables will be located using the default mechanism
     * on the underlying database. On PostgreSQL, the search order is determined by
     * the {@code "search_path"} database variable.
     */
    final String schema;

    /**
     * The timezone to use for reading and writing dates in the database. This timezone can be
     * set by the {@link ConfigurationKey#TIMEZONE} property and will be used by the calendar
     * returned by {@link Table#getCalendar()}.
     */
    private final TimeZone timezone;

    /**
     * The locale to use for formatting messages, or {@code null} for the system-default.
     */
    private volatile Locale locale;

    /**
     * The hints to use for fetching factories. Shall be considered read-only.
     */
    final Hints hints;

    /**
     * The {@code Session} instance used for each thread. Threads are identified by their
     * {@linkplain Thread#getId() ID}. New values are created by {@link #getLocalCache()}
     * if no existing values can be used. Values are removed by {@link Session#monitorExit}
     * after some delay (may be 2 seconds) of inactivity.
     *
     * {@note In a previous version, we used a single <code>ThreadLocal</code> instance.
     *        We switched to a map because this allow us to reuse an available session
     *        for a different thread, and because it allows us to know the set of all
     *        active sessions.}
     */
    private final Map<Long,Session> sessions = new LinkedHashMap<Long,Session>();

    /**
     * Holds thread-local information for SQL statements being executed.
     * Those information can not be shared between different threads.
     * <p>
     * This class opportunistically extends {@code StatementPool}, which duplicates the work of
     * connection pools provided in modern JDBC driver. But we use {@code StatementPool} anyway
     * because we want the {@code org.geotoolkit.coverage.sql} package to work raisonably well
     * in the absence of connection pool, and because the {@code StatementPool} timer provides
     * a convenient way to let the connections being closed automatically without worrying if
     * the connection is still in use. The alternative would be to maintain a {@code usageCount}
     * variable incremented when a {@link Table} uses the connection, and decremented when it is
     * done - but even this alternative would close the connection just before we get a new one,
     * for example when creating a {@code LayerEntry} and immediately invoking one of its method
     * which deferred the database query.
     *
     * @author Martin Desruisseaux (Geomatys)
     * @version 3.12
     *
     * @since 3.09
     * @module
     */
    @SuppressWarnings("serial")
    private static final class Session extends StatementPool<String, LocalCache.Stmt>
            implements LocalCache
    {
        /**
         * The {@linkplain Thread#getId() ID} of the thread which is using this {@code Session},
         * or {@code null} if this {@code Session} is available for reuse by any thread.
         */
        private Long threadID;

        /**
         * A copy of the {@link Database#sessions} references, used for synchronization purpose.
         * We don't take a reference to the encloding {@link Database} because we don't need it,
         * so we give more chances to GC to collect it.
         */
        private final Map<Long,Session> sessions;

        /**
         * The calendar to use for reading and writing dates in a database. This calendar
         * is created when first needed and shall use the {@link Database#timezone}.
         *
         * @see Database#getCalendar()
         */
        Calendar calendar;

        /**
         * Generators of named identifiers. Will be created only when first needed. The keys are
         * the column names for which ID are generated. Note that the same instance can be used
         * for different tables if the column name is the same.
         *
         * @since 3.11
         */
        Map<String,NameGenerator> generators;

        /**
         * Creates a new instance for the given data source.
         * We will cache a maximum of 8 prepared statements.
         */
        Session(final DataSource source, final Map<Long,Session> sessions) {
            super(8, source);
            this.sessions = sessions;
        }

        /**
         * Returns a prepared statement for the given SQL query.
         * See {@link LocalCache} javadoc for usage example.
         */
        @Override
        public Stmt prepareStatement(final Table table, final String sql) throws SQLException {
            Stmt value = remove(sql);
            if (value == null) {
                value = new Stmt(connection().prepareStatement(sql, table.wantsAutoGeneratedKeys() ?
                        Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS), sql);
            }
            return value;
        }

        /**
         * Invoked in a backround thread when the user thread exits its outer {@code synchronized}
         * statement. This method declares that this object is available for reuse. If all JDBC
         * resources have been closed, we will let the garbage collector collects this object.
         */
        @Override
        protected void monitorExit(final boolean closed) {
            super.monitorExit(closed);
            synchronized (sessions) {
                if (closed) {
                    final Session old = sessions.remove(threadID);
                    assert old == null || old == this : old;
                }
                threadID = null; // Declare this Session as available for reuse.
            }
        }

        /**
         * Returns a string representation for debugging purpose.
         */
        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder("Session[");
            if (threadID != null) {
                buffer.append("threadID=").append(threadID);
            }
            return buffer.append(']').toString();
        }
    }

    /**
     * Incremented everytime a modification is applied in the configuration of a table.
     * This is for internal use by {@link Table#fireStateChanged(String)} only.
     */
    final AtomicInteger modificationCount = new AtomicInteger();

    /**
     * Lock for transactions performing write operations. The {@link Connection#commit} or
     * {@link Connection#rollback} method will be invoked when the lock count reach zero.
     *
     * @see #transactionBegin()
     * @see #transactionEnd(boolean)
     */
    private final ReentrantLock transactionLock = new ReentrantLock(true);

    /**
     * Last table created for each category. Every time the {@link #getTable(Class)} method
     * is invoked, the last returned table is stored in this map so it can be used as a
     * template for the next table to create.
     *
     * {@section Synchronization note}
     * Every access to this map shall be synchronized on {@code tables}.
     */
    private final Map<Class<? extends Table>, Table> tables =
            new HashMap<Class<? extends Table>, Table>();

    /**
     * The properties given at construction time, or {@code null} if none.
     */
    private final Properties properties;

    /**
     * Creates a new instance using the same configuration than the given instance.
     * The new instance will have its own, initially empty, cache.
     *
     * @param toCopy The existing instance to copy.
     */
    public Database(final Database toCopy) {
        this.source     = toCopy.source;
        this.catalog    = toCopy.catalog;
        this.schema     = toCopy.schema;
        this.timezone   = toCopy.timezone;
        this.hints      = toCopy.hints;
        this.properties = toCopy.properties;
    }

    /**
     * Creates a new instance using the provided data source and configuration properties.
     * If a properties map is specified, then the keys enumerated in {@link ConfigurationKey}
     * will be used.
     *
     * @param  datasource The data source.
     * @param  properties The configuration properties, or {@code null} if none.
     */
    public Database(final DataSource datasource, final Properties properties) {
        Table.ensureNonNull("datasource", datasource);
        final String username, password, tz;
        this.properties = properties; // Must be set before to ask for properties.
        username = getProperty(ConfigurationKey.USER);
        password = getProperty(ConfigurationKey.PASSWORD);
        tz       = getProperty(ConfigurationKey.TIMEZONE);
        timezone = !tz.equalsIgnoreCase("local") ? TimeZone.getTimeZone(tz) : TimeZone.getDefault();
        catalog  = getProperty(ConfigurationKey.CATALOG);
        schema   = getProperty(ConfigurationKey.SCHEMA);
        source   = new AuthenticatedDataSource(datasource, username, password, Boolean.TRUE);
        hints    = null; // May be configurable in a future version.
    }

    /**
     * Returns a property. The key is usually a constant like {@link ConfigurationKey#TIMEZONE}.
     *
     * @param  key The key for the property to fetch.
     * @return The property value, or {@code null} if none and there is no default value.
     */
    final String getProperty(final ConfigurationKey key) {
        // No need to synchronize since 'Properties' is already synchronized.
        return (properties != null) ? properties.getProperty(key.key, key.defaultValue) : key.defaultValue;
    }

    /**
     * Returns the locale to use for formatting messages. This is used mostly for error messages
     * in exceptions, and for warnings during {@linkplain javax.imageio.ImageRead image read}
     * operations.
     *
     * @return The locale for message formatting, or {@code null} for the
     *         {@linkplain Locale#getDefault() system default}.
     */
    @Override
    public final Locale getLocale() {
        return locale;
    }

    /**
     * Sets the locale to use for formatting messages.
     *
     * @param locale The new locale for message formatting, or {@code null} for the
     *        {@linkplain Locale#getDefault() system default}.
     */
    public final void setLocale(final Locale locale) {
        this.locale = locale;
    }

    /**
     * Returns the timezone in which the dates in the database are expressed. This information
     * can be specified through the {@link ConfigurationKey#TIMEZONE} property. It is used in
     * order to convert the dates from the database timezone to UTC.
     *
     * @return The time zone for dates to appear in database records.
     *
     * @see Table#getCalendar()
     */
    public final TimeZone getTimeZone() {
        return (TimeZone) timezone.clone();
    }

    /**
     * Creates and returns a new calendar using the database timezone.
     *
     * {@section Implementation note}
     * We use {@link Locale#CANADA} because it is close to the format typically used in
     * databases ({@code "yyyy/mm/dd"}). We don't use {@link Calendar#getInstance()}
     * because it may be a totally different and incompatible calendar for our purpose.
     */
    final Calendar getCalendar() {
        final Session s = getLocalCache();
        assert Thread.holdsLock(s);
        Calendar calendar = s.calendar;
        if (calendar == null) {
            s.calendar = calendar = new GregorianCalendar(timezone, Locale.CANADA);
        }
        return calendar;
    }

    /**
     * Returns the data source which has been specified to the constructor.
     *
     * @param  wrap {@code true} for returning the data source in a wrapper which provide the
     *         username, password and set the connection to read-only mode, or {@code false}
     *         for returning directly the data source given to the constructor.
     * @return The data source.
     */
    public final DataSource getDataSource(final boolean wrap) {
        return wrap ? source : source.wrapped;
    }

    /**
     * Returns the {@link LocalCache} instance for the current thread. This is the interface to
     * use for getting the JDBC connection and prepared statements. See the {@link LocalCache}
     * javadoc for usage examples.
     *
     * @see Table#release()
     */
    final Session getLocalCache() {
        Session session;
        final Long threadID = Thread.currentThread().getId();
        synchronized (sessions) {
            /*
             * WARNING: Do not invoke any method which may synchronize
             *          on 'session' inside this synchronized block.
             */
            session = sessions.get(threadID);
            if (session != null) {
                if (session.threadID == null) {
                    session.threadID = threadID;
                } else {
                    assert threadID.equals(session.threadID) : session;
                }
            } else {
                /*
                 * Search for an existing Session instance which is available for reuse.
                 * If none is found, we will create a new one.
                 */
                for (final Iterator<Session> it=sessions.values().iterator(); it.hasNext();) {
                    final Session candidate = it.next();
                    if (candidate.threadID == null) {
                        session = candidate;
                        it.remove();
                        break;
                    }
                }
                if (session == null) {
                    session = new Session(source, sessions);
                }
                if (sessions.put(threadID, session) != null) {
                    throw new ConcurrentModificationException(); // Should never happen.
                }
                session.threadID = threadID;
            }
        }
        return session;
    }

    /**
     * Put the given statement back in the pool. This method is invoked by
     * {@link LocalCache.Stmt#release()} only. The later is the API to use.
     */
    final void release(final LocalCache.Stmt entry) throws SQLException {
        final LocalCache.Stmt old = getLocalCache().put(entry.sql, entry);
        if (old != null) {
            old.statement.close();
        }
    }

    /**
     * Returns a table of the specified type.
     *
     * @param  <T> The table class.
     * @param  type The table class.
     * @return An instance of a table of the specified type.
     * @throws NoSuchTableException if the specified type is unknown to this database.
     */
    public final <T extends Table> T getTable(final Class<T> type) throws NoSuchTableException {
        Table table;
        synchronized (tables) {
            table = tables.get(type);
            if (table != null && table.canReuse) {
                table.canReuse = false;
            } else {
                if (table != null) {
                    table = table.clone();
                } else try {
                    final Constructor<T> c = type.getConstructor(Database.class);
                    c.setAccessible(true);
                    table = c.newInstance(this);
                } catch (Exception exception) { // Too many exeptions for enumerating them.
                    throw new NoSuchTableException(Classes.getShortName(type), exception);
                }
                tables.put(type, table);
            }
        }
        return type.cast(table);
    }

    /**
     * Invoked before an arbitrary amount of {@code INSERT}, {@code UPDATE} or {@code DELETE}
     * SQL statement. This method <strong>must</strong> be invoked in a {@code try} ... {@code
     * finally} block as below:
     *
     * {@preformat java
     *     final LocalCache cache = getLocalCache();
     *     synchronized (cache) {
     *         boolean success = false;
     *         transactionBegin();
     *         try {
     *             // Do some operation here...
     *             success = true;
     *         } finally {
     *             transactionEnd(success);
     *         }
     *     }
     * }
     *
     * @throws SQLException If the operation failed.
     */
    final void transactionBegin() throws SQLException {
        boolean success = false;
        transactionLock.lock();
        try {
            if (transactionLock.getHoldCount() == 1) {
                final LocalCache sp = getLocalCache();
                assert Thread.holdsLock(sp); // Necessary for blocking the cleaner thread.
                final Connection connection = sp.connection();
                connection.setReadOnly(false);
                connection.setAutoCommit(false);
            }
            success = true;
        } finally {
            if (!success) {
                transactionLock.unlock();
            }
        }
    }

    /**
     * Invoked after the {@code INSERT}, {@code UPDATE} or {@code DELETE}
     * SQL statement finished.
     *
     * @param  success {@code true} if the operation succeed and should be commited,
     *         or {@code false} if we should rollback.
     * @throws SQLException If the commit or the rollback failed.
     */
    final void transactionEnd(final boolean success) throws SQLException {
        ensureOngoingTransaction();
        try {
            if (transactionLock.getHoldCount() == 1) {
                final LocalCache sp = getLocalCache();
                assert Thread.holdsLock(sp); // Necessary for blocking the cleaner thread.
                final Connection connection = sp.connection();
                if (success) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                connection.setAutoCommit(true);
                connection.setReadOnly(true);
            }
        } finally {
            transactionLock.unlock();
        }
    }

    /**
     * Ensures that the current thread is allowed to performs a transaction.
     */
    final void ensureOngoingTransaction() throws SQLException {
        if (!transactionLock.isHeldByCurrentThread()) {
            throw new SQLNonTransientException(Errors.getResources(getLocale())
                    .getString(Errors.Keys.THREAD_DOESNT_HOLD_LOCK));
        }
    }

    /**
     * Returns a {@link NameGenerator} for the given column name.
     *
     * @param  pk The name of the primary key column.
     * @return An identifier generator for the given column.
     * @throws SQLException If an error occured while creating the generator.
     */
    final NameGenerator getIdentifierGenerator(final String pk) throws SQLException {
        final Session s = getLocalCache();
        Map<String, NameGenerator> generators = s.generators;
        NameGenerator generator;
        if (generators != null) {
            generator = generators.get(pk);
            if (generator != null) {
                return generator;
            }
            generator = new NameGenerator(generators.values().iterator().next(), pk);
        } else {
            synchronized (s) {
                generator = new NameGenerator(s, pk);
            }
            s.generators = generators = new HashMap<String, NameGenerator>(4);
        }
        if (generators.put(pk, generator) != null) {
            throw new AssertionError(pk);
        }
        return generator;
    }

    /**
     * Closes all connections and restores the attributes to their initial state.
     *
     * @throws SQLException If an error occured while closing the connection.
     */
    public void reset() throws SQLException {
        final Session[] s;
        synchronized (sessions) {
            s = sessions.values().toArray(new Session[sessions.size()]);
            sessions.clear();
        }
        /*
         * The calls to Session.close() must be performed
         * outside the synchronized (sessions) block.
         */
        for (final Session session : s) {
            session.close();
        }
        locale = null;
    }
}
