package mil.nga.giat.geowave.cli.geoserver;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import mil.nga.giat.geowave.core.cli.annotations.GeowaveOperation;
import mil.nga.giat.geowave.core.cli.api.Command;
import mil.nga.giat.geowave.core.cli.api.OperationParams;
import mil.nga.giat.geowave.core.cli.operations.config.options.ConfigOptions;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@GeowaveOperation(name = "store", parentOperation = GeoServerSection.class)
@Parameters(commandDescription = "GeoServer store CRUD")
public class GeoServerStoreCommand implements
		Command
{
	private GeoServerRestClient geoserverClient;

	@Parameter(names = {
		"-ws",
		"--workspace"
	}, required = true, description = "Workspace Name")
	private String workspace;

	@Parameter(names = {
		"-n",
		"--name"
	}, required = true, description = "Store Name")
	private String name;

	@Parameter(names = {
		"-a",
		"--action"
	}, required = true, description = "Store Action (get, add, delete or list)")
	private String action;

	@Override
	public boolean prepare(
			OperationParams params ) {
		// validate requested action:
		boolean valid = false;

		if (action.equals("get") || 
				action.startsWith("add") ||
				action.startsWith("del")) {
			if (name != null && !name.isEmpty()) {
				valid = true;
			}
			else {
				System.err.println("You must supply a store name!");
			}
		}
		else if (action.startsWith("lis")) {
			valid = true;
		}

		if (!valid) {
			return false;
		}

		// Get the local config for GeoServer
		File propFile = (File) params.getContext().get(
				ConfigOptions.PROPERTIES_FILE_CONTEXT);
		Properties gsConfig = ConfigOptions.loadProperties(
				propFile,
				null);

		// Create a rest client
		geoserverClient = new GeoServerRestClient(
				gsConfig.getProperty("geoserver.url"),
				null, // default user
				null, // default pass
				workspace);

		// Successfully prepared
		return true;
	}

	@Override
	public void execute(
			OperationParams params )
			throws Exception {
		if (action.equals("get")) {
			getStore();
		}
		else if (action.equals("add")) {
			addStore();
		}
		else if (action.startsWith("del")) {
			deleteStore();
		}
		else {
			listStores();
		}
	}

	private void getStore() {
		Response getStoreResponse = geoserverClient.getDatastore(workspace, name);

		if (getStoreResponse.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("\nGeoServer store info for '" + name + "':");

			JSONObject jsonResponse = JSONObject.fromObject(getStoreResponse.getEntity());
			JSONObject datastore = jsonResponse.getJSONObject("dataStore");
			System.out.println(datastore.toString(2));
		}
		else {
			System.err.println("Error getting GeoServer store info for '" + name + "'; code = " + getStoreResponse.getStatus());
		}
	}

	private void listStores() {
	}

	private void addStore() {
	}

	private void deleteStore() {
	}

	public String getWorkspace() {
		return workspace;
	}

	public void setWorkspace(
			String workspace ) {
		this.workspace = workspace;
	}

	public String getName() {
		return name;
	}

	public void setName(
			String workspace ) {
		this.name = workspace;
	}

	public String getAction() {
		return action;
	}

	public void setAction(
			String action ) {
		this.action = action;
	}
}