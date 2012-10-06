package org.healthdata.metadata;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * This class provides functionality to convert all metadata files describing
 * a healthdata.org dataset using the {@link DatasetMetadataUpdater}. This class is
 * supposed to work with the files previously downloaded by {@link Downloader}.
 * 
 * @author csnyulas
 *
 */
public class Updater {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length != 3) {
			System.out.println("USAGE: HealthdataMetadataUpdater DIR_FOR_OLD_METADATA_FILES DIR_FOR_NEW_METADATA_FILES CONFIG_PROPERTIES_FILE");
			return;
		}
		String oldMetadataDir = args[0];
		String newMetadataDir = args[1];
		String configPropFileName = args[2];
		
		Updater hdUpdMngr = new Updater();
		hdUpdMngr.updateAllMetadataFiles(oldMetadataDir, newMetadataDir, configPropFileName);
	}

	
	public void updateAllMetadataFiles(String oldMetadataDir, String newMetadataDir, 
			String configPropFileName) {
		Downloader hdMetadataDownloader = new Downloader();

		try {
			List<String> datasetIds = hdMetadataDownloader.getListOfDatasets(new URL(HealthDataConstants.HUB_HEALTHDATA_GOV_API_2_REST_DATASET));
			System.out.println(datasetIds);
			for (String datasetId : datasetIds) {
				System.out.println("Processing: " + datasetId);
				String oldRDFFileName = hdMetadataDownloader.createAbsoluteRDFFileName(oldMetadataDir, datasetId);
				String newRDFFileName = hdMetadataDownloader.createAbsoluteRDFFileName(newMetadataDir, datasetId);
				DatasetMetadataUpdater.convertMetadataRdfFile(oldRDFFileName, newRDFFileName, configPropFileName);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
}
