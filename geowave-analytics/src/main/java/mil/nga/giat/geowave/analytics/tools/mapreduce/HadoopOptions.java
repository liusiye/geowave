package mil.nga.giat.geowave.analytics.tools.mapreduce;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

import mil.nga.giat.geowave.accumulo.mapreduce.GeoWaveConfiguratorBase;
import mil.nga.giat.geowave.analytics.parameters.MapReduceParameters;
import mil.nga.giat.geowave.analytics.parameters.MapReduceParameters.MRConfig;
import mil.nga.giat.geowave.analytics.tools.PropertyManagement;

import org.apache.commons.cli.Option;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

/**
 * This class encapsulates the command-line options and parsed values specific
 * to staging intermediate data to HDFS.
 */
public class HadoopOptions
{
	private final static Logger LOGGER = Logger.getLogger(HadoopOptions.class);
	private final String hdfsHostPort;
	private final Path basePath;
	private final String jobTrackerHostPort;
	private final Configuration config = new Configuration();

	public HadoopOptions(
			final PropertyManagement runTimeProperties )
			throws IOException {
		final boolean setRemoteInvocation = runTimeProperties.hasProperty(MRConfig.HDFS_HOST_PORT) || runTimeProperties.hasProperty(MRConfig.JOBTRACKER_HOST_PORT);
		final String hostport = runTimeProperties.getProperty(
				MRConfig.HDFS_HOST_PORT,
				"localhost:53000");
		hdfsHostPort = (!hostport.contains("://")) ? "hdfs://" + hostport : hostport;
		basePath = new Path(
				runTimeProperties.getProperty(MRConfig.HDFS_BASE_DIR),
				"/");
		jobTrackerHostPort = runTimeProperties.getProperty(
				MRConfig.JOBTRACKER_HOST_PORT,
				runTimeProperties.getProperty(MRConfig.YARN_RESOURCE_MANAGER));

		final String name = runTimeProperties.getProperty(MapReduceParameters.MRConfig.CONFIG_FILE);
		if (name != null) {
			if (name != null) {
				try {
					config.addResource(
							new FileInputStream(
									name),
							name);
				}
				catch (final IOException ex) {
					LOGGER.error(
							"Configuration file " + name + " not found",
							ex);
					throw ex;
				}
			}
		}

		if (setRemoteInvocation) {
			GeoWaveConfiguratorBase.setRemoteInvocationParams(
					hdfsHostPort,
					jobTrackerHostPort,
					config);
		}
		else {
			LOGGER.info("Assuming local job submission");
		}
		final FileSystem fs = FileSystem.get(config);
		if (!fs.exists(basePath)) {
			LOGGER.fatal("HDFS base directory " + basePath + " does not exist");
			return;
		}
	}

	public HadoopOptions(
			final String hdfsHostPort,
			final Path basePath,
			final String jobTrackerHostport ) {
		this.hdfsHostPort = hdfsHostPort;
		this.basePath = basePath;
		jobTrackerHostPort = jobTrackerHostport;
	}

	public static void fillOptions(
			final Set<Option> allOptions ) {
		MapReduceParameters.fillOptions(allOptions);
	}

	public String getHdfsHostPort() {
		return hdfsHostPort;
	}

	public Path getBasePath() {
		return basePath;
	}

	public String getJobTrackerOrResourceManagerHostPort() {
		return jobTrackerHostPort;
	}

	public Configuration getConfiguration() {
		return config;
	}
}
