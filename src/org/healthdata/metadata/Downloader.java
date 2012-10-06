package org.healthdata.metadata;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;

/**
 * This class provides functionality to download all metadata files describing
 * a healthdata.org dataset.
 * 
 * @author csnyulas
 *
 */
public class Downloader {

	
	private static final boolean FORCE_UPDATE_OPTION = true;
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length != 1 ) {
			System.out.println("USAGE: HealthdataMetadataUpdater DIR_FOR_METADATA_FILES");
			return;
		}
		String workingDir = args[0];
		
		Downloader hdMetadataDownloader = new Downloader();
		hdMetadataDownloader.downloadMetadataFiles(workingDir);
	}

	
	public void downloadMetadataFiles(String workingDir) {
		try {
			List<String> datasetIds = getListOfDatasets(new URL(HealthDataConstants.HUB_HEALTHDATA_GOV_API_2_REST_DATASET));
			System.out.println(datasetIds);
			for (String datasetId : datasetIds) {
				System.out.println("Processing: " + datasetId);
				String datasetDownloadUrl = HealthDataConstants.HUB_HEALTHDATA_GOV_DATASET_PREFIX + datasetId + HealthDataConstants.RDF_FILE_EXTENSION;
				File metadataFile = new File(createAbsoluteRDFFileName(workingDir, datasetId));
				downloadFile(datasetDownloadUrl, metadataFile, FORCE_UPDATE_OPTION);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public String createAbsoluteRDFFileName(String dirName, String fileName) {
		return dirName + (dirName.endsWith(File.separator) ? "" : File.separator) + fileName + HealthDataConstants.RDF_FILE_EXTENSION;
	}

	
	public void downloadFile(String datasetDownloadUrl, File file, boolean forceUpdate) {
		try {
			if ( ! file.exists() || forceUpdate) {
				InputStream is = getInputStream(new URL(datasetDownloadUrl));
				FileWriter writer = new FileWriter(file);
				IOUtils.copy(is, writer);
				is.close();
				writer.close();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	private static InputStream getInputStream(URL url) throws IOException{
		if(url.getProtocol().equals("http")) {
			URLConnection conn;
			conn = url.openConnection();
			conn.setRequestProperty("Accept", "application/rdf+xml");
			conn.addRequestProperty("Accept", "text/xml");
			conn.addRequestProperty("Accept", "*/*");
			return conn.getInputStream();
		}
		else {
			return url.openStream();
		}
	}


	public List<String> getListOfDatasets(URL conceptURL) throws IOException {
		InputStream is = null;
		try {
			is = getInputStream(conceptURL);
		} catch (IOException e) {
			//log.log(Level.WARNING, "IO Exception when accessing HealthData hub. URL: " + conceptURL, e);
			System.out.println("IO Exception when accessing HealthData hub. URL: " + conceptURL);
			return null;
		}
		if (is == null) { return null; }
//		Success success =  (Success) xstream.fromXML(is);
//		if (success == null) { return null; }
//		return success.getData().getClassBeanList();

		StringWriter writer = new StringWriter();
		IOUtils.copy(is, writer);
		is.close();
		writer.close();
		
		String response = writer.toString();
		return parseJsonList(response);
	}

	
	private List<String> parseJsonList(String response) {
		if (response.startsWith("[")) {
			response = response.substring(1);
		}
		if (response.endsWith("]")) {
			response = response.substring(0, response.length()-1);
		}
		response = response.replaceAll("\"", "");
		return Arrays.asList(response.split(",[ ]*"));
	}

}
