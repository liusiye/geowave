package mil.nga.giat.geowave.datastore.hbase.operations;

import java.io.IOException;
import java.util.Set;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.store.DataStoreOperations;
import mil.nga.giat.geowave.datastore.hbase.io.HBaseWriter;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseRequiredOptions;
import mil.nga.giat.geowave.datastore.hbase.query.RowCountEndpoint;
import mil.nga.giat.geowave.datastore.hbase.util.ConnectionPool;
import mil.nga.giat.geowave.datastore.hbase.util.HBaseUtils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.security.visibility.Authorizations;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class BasicHBaseOperations implements
		DataStoreOperations
{
	private final static Logger LOGGER = Logger.getLogger(BasicHBaseOperations.class);
	private static final String DEFAULT_TABLE_NAMESPACE = "";
	public static final Object ADMIN_MUTEX = new Object();
	private static final long SLEEP_INTERVAL_FOR_CF_VERIFY = 100L;

	private final Connection conn;
	private final String tableNamespace;	
	private final boolean schemaUpdateEnabled;


	// Test Only!
	static {
		LOGGER.setLevel(Level.DEBUG);
	}

	public BasicHBaseOperations(
			final String zookeeperInstances,
			final String geowaveNamespace )
			throws IOException {
		conn = ConnectionPool.getInstance().getConnection(
				zookeeperInstances);
		tableNamespace = geowaveNamespace;
		
		schemaUpdateEnabled = conn.getConfiguration().getBoolean(
				"hbase.online.schema.update.enable",
				false);
	}

	public BasicHBaseOperations(
			final String zookeeperInstances )
			throws IOException {
		this(
				zookeeperInstances,
				DEFAULT_TABLE_NAMESPACE);
	}

	public BasicHBaseOperations(
			final Connection connector ) {
		this(
				DEFAULT_TABLE_NAMESPACE,
				connector);
	}

	public BasicHBaseOperations(
			final String tableNamespace,
			final Connection connector ) {
		this.tableNamespace = tableNamespace;
		conn = connector;
		
		schemaUpdateEnabled = conn.getConfiguration().getBoolean(
				"hbase.online.schema.update.enable",
				false);
	}

	public static BasicHBaseOperations createOperations(
			final HBaseRequiredOptions options )
			throws IOException {
		return new BasicHBaseOperations(
				options.getZookeeper(),
				options.getGeowaveNamespace());
	}

	public Configuration getConfig() {
		return conn.getConfiguration();
	}

	public static TableName getTableName(
			final String tableName ) {
		return TableName.valueOf(tableName);
	}

	public HBaseWriter createWriter(
			final String sTableName,
			final String[] columnFamilies,
			final boolean createTable )
			throws IOException {
		return createWriter(
				sTableName,
				columnFamilies,
				createTable,
				null);
	}

	public HBaseWriter createWriter(
			final String sTableName,
			final String[] columnFamilies,
			final boolean createTable,
			final Set<ByteArrayId> splits )
			throws IOException {
		final String qTableName = getQualifiedTableName(sTableName);

		if (createTable) {
			createTable(
					columnFamilies,
					getTableName(qTableName),
					splits);
		}

		return new HBaseWriter(
				conn.getAdmin(),
				qTableName);
	}

	private void createTable(
			final String[] columnFamilies,
			final TableName name,
			final Set<ByteArrayId> splits )
			throws IOException {
		synchronized (ADMIN_MUTEX) {
			if (!conn.getAdmin().isTableAvailable(
					name)) {
				final HTableDescriptor desc = new HTableDescriptor(
						name);
				for (final String columnFamily : columnFamilies) {
					desc.addFamily(new HColumnDescriptor(
							columnFamily));
				}

				if ((splits != null) && !splits.isEmpty()) {
					final byte[][] splitKeys = new byte[splits.size()][];
					int i = 0;
					for (final ByteArrayId split : splits) {
						splitKeys[i++] = split.getBytes();
					}
					conn.getAdmin().createTable(
							desc,
							splitKeys);
				}
				else {
					conn.getAdmin().createTable(
							desc);
				}
			}
		}
	}

	public String getQualifiedTableName(
			final String unqualifiedTableName ) {
		return HBaseUtils.getQualifiedTableName(
				tableNamespace,
				unqualifiedTableName);
	}

	@Override
	public void deleteAll()
			throws IOException {
		final TableName[] tableNamesArr = conn.getAdmin().listTableNames();
		for (final TableName tableName : tableNamesArr) {
			if ((tableNamespace == null) || tableName.getNameAsString().startsWith(
					tableNamespace)) {
				synchronized (ADMIN_MUTEX) {
					if (conn.getAdmin().isTableAvailable(
							tableName)) {
						conn.getAdmin().disableTable(
								tableName);
						conn.getAdmin().deleteTable(
								tableName);
					}
				}
			}
		}
	}

	@Override
	public boolean tableExists(
			final String tableName )
			throws IOException {
		final String qName = getQualifiedTableName(tableName);
		synchronized (ADMIN_MUTEX) {
			return conn.getAdmin().isTableAvailable(
					getTableName(qName));
		}

	}

	public boolean columnFamilyExists(
			final String tableName,
			final String columnFamily )
			throws IOException {
		final String qName = getQualifiedTableName(tableName);
		synchronized (ADMIN_MUTEX) {
			final HTableDescriptor descriptor = conn.getAdmin().getTableDescriptor(
					getTableName(qName));

			if (descriptor != null) {
				for (final HColumnDescriptor hColumnDescriptor : descriptor.getColumnFamilies()) {
					if (hColumnDescriptor.getNameAsString().equalsIgnoreCase(
							columnFamily)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public ResultScanner getScannedResults(
			final Scan scanner,
			final String tableName,
			final String... authorizations )
			throws IOException {
		if (authorizations != null) {
			scanner.setAuthorizations(new Authorizations(
					authorizations));
		}

		final Table table = conn.getTable(getTableName(getQualifiedTableName(tableName)));

		final ResultScanner results = table.getScanner(scanner);

		table.close();

		return results;
	}

	public boolean deleteTable(
			final String tableName ) {
		final String qName = getQualifiedTableName(tableName);
		try {
			conn.getAdmin().deleteTable(
					getTableName(qName));
			return true;
		}
		catch (final IOException ex) {
			LOGGER.warn(
					"Unable to delete table '" + qName + "'",
					ex);
		}
		return false;

	}

	public RegionLocator getRegionLocator(
			final String tableName )
			throws IOException {
		return conn.getRegionLocator(getTableName(getQualifiedTableName(tableName)));
	}

	@Override
	public String getTableNameSpace() {
		return tableNamespace;
	}

	public Table getTable(
			final String tableName )
			throws IOException {
		return conn.getTable(getTableName(getQualifiedTableName(tableName)));
	}

	public void verifyCoprocessor(
			String tableNameStr,
			String coprocessorName,
			String coprocessorJar ) {
		try {
			Admin admin = conn.getAdmin();
			TableName tableName = getTableName(getQualifiedTableName(tableNameStr));
			HTableDescriptor td = admin.getTableDescriptor(tableName);

			if (!td.hasCoprocessor(coprocessorName)) {
				LOGGER.debug(tableNameStr + " does not have coprocessor. Adding " + coprocessorName);

				// Retrieve coprocessor jar path from config
				Path hdfsJarPath = new Path(
						coprocessorJar);
				LOGGER.debug("Coprocessor jar path: " + hdfsJarPath.toString());

				if (!schemaUpdateEnabled && !admin.isTableDisabled(tableName)) {
					LOGGER.debug("- disable table...");				
					admin.disableTable(tableName);
				}

				LOGGER.debug("- add coprocessor...");
				td.addCoprocessor(
						RowCountEndpoint.class.getName(),
						hdfsJarPath,
						Coprocessor.PRIORITY_USER,
						null);

				LOGGER.debug("- modify table...");
				admin.modifyTable(
						tableName,
						td);
				
				if (schemaUpdateEnabled) {
					do {
						try {
							Thread.sleep(SLEEP_INTERVAL_FOR_CF_VERIFY);
						}
						catch (final InterruptedException e) {
							LOGGER.warn(
									"Sleeping while coprocessor added interrupted",
									e);
						}
					}
					while (admin.getAlterStatus(
							tableName).getFirst() > 0);
				}
				
				if (!schemaUpdateEnabled) {
					LOGGER.debug("- enable table...");			
					admin.enableTable(tableName);
				}
				
				LOGGER.debug("Successfully added coprocessor");
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
