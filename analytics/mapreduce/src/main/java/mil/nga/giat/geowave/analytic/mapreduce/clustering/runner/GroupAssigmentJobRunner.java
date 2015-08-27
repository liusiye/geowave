package mil.nga.giat.geowave.analytic.mapreduce.clustering.runner;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import mil.nga.giat.geowave.analytic.PropertyManagement;
import mil.nga.giat.geowave.analytic.clustering.CentroidManagerGeoWave;
import mil.nga.giat.geowave.analytic.clustering.NestedGroupCentroidAssignment;
import mil.nga.giat.geowave.analytic.mapreduce.GeoWaveAnalyticJobRunner;
import mil.nga.giat.geowave.analytic.mapreduce.clustering.GroupAssignmentMapReduce;
import mil.nga.giat.geowave.analytic.param.CentroidParameters;
import mil.nga.giat.geowave.analytic.param.GlobalParameters;
import mil.nga.giat.geowave.analytic.param.MapReduceParameters;
import mil.nga.giat.geowave.analytic.param.ParameterEnum;
import mil.nga.giat.geowave.analytic.param.StoreParameters;
import mil.nga.giat.geowave.analytic.param.StoreParameters.StoreParam;
import mil.nga.giat.geowave.core.cli.DataStoreCommandLineOptions;
import mil.nga.giat.geowave.core.store.config.ConfigUtils;
import mil.nga.giat.geowave.mapreduce.input.GeoWaveInputFormat;
import mil.nga.giat.geowave.mapreduce.input.GeoWaveInputKey;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * 
 * Assign group IDs to input items based on centroids.
 * 
 * 
 */
public class GroupAssigmentJobRunner extends
		GeoWaveAnalyticJobRunner
{
	private int zoomLevel = 1;

	public GroupAssigmentJobRunner() {
		super.setReducerCount(8);
	}

	public void setZoomLevel(
			final int zoomLevel ) {
		this.zoomLevel = zoomLevel;
	}

	@Override
	public void configure(
			final Job job )
			throws Exception {
		job.setMapperClass(GroupAssignmentMapReduce.GroupAssignmentMapper.class);
		job.setMapOutputKeyClass(GeoWaveInputKey.class);
		job.setMapOutputValueClass(ObjectWritable.class);
		job.setReducerClass(Reducer.class);
		job.setOutputKeyClass(GeoWaveInputKey.class);
		job.setOutputValueClass(ObjectWritable.class);
	}

	@Override
	public Class<?> getScope() {
		return GroupAssignmentMapReduce.class;
	}

	@Override
	public int run(
			final Configuration config,
			final PropertyManagement runTimeProperties )
			throws Exception {

		// Required since the Mapper uses the input format parameters to lookup
		// the adapter
		final DataStoreCommandLineOptions dataStoreOptions = (DataStoreCommandLineOptions) runTimeProperties.getProperty(StoreParam.DATA_STORE);
		GeoWaveInputFormat.setDataStoreName(
				config,
				dataStoreOptions.getFactory().getName());
		GeoWaveInputFormat.setStoreConfigOptions(
				config,
				ConfigUtils.valuesToStrings(
						dataStoreOptions.getConfigOptions(),
						dataStoreOptions.getFactory().getOptions()));
		GeoWaveInputFormat.setGeoWaveNamespace(
				config,
				dataStoreOptions.getNamespace());
		runTimeProperties.setConfig(
				new ParameterEnum[] {
					CentroidParameters.Centroid.EXTRACTOR_CLASS,
					CentroidParameters.Centroid.WRAPPER_FACTORY_CLASS,
				},
				config,
				GroupAssignmentMapReduce.class);
		NestedGroupCentroidAssignment.setParameters(
				config,
				getScope(),
				runTimeProperties);
		CentroidManagerGeoWave.setParameters(
				config,
				getScope(),
				runTimeProperties);

		NestedGroupCentroidAssignment.setZoomLevel(
				config,
				getScope(),
				zoomLevel);

		return super.run(
				config,
				runTimeProperties);
	}

	@Override
	public Collection<ParameterEnum<?>> getParameters() {
		final Set<ParameterEnum<?>> params = new HashSet<ParameterEnum<?>>();
		params.addAll(super.getParameters());

		params.addAll(Arrays.asList(new ParameterEnum<?>[] {
			StoreParameters.StoreParam.DATA_STORE,
			GlobalParameters.Global.BATCH_ID
		}));

		params.addAll(CentroidManagerGeoWave.getParameters());
		params.addAll(MapReduceParameters.getParameters());
		params.addAll(NestedGroupCentroidAssignment.getParameters());
		return params;
	}

}