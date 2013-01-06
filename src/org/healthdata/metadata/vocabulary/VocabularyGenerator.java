package org.healthdata.metadata.vocabulary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

import org.ncbo.stanford.bean.search.Page;
import org.ncbo.stanford.bean.search.SearchBean;
import org.ncbo.stanford.bean.search.SearchResultListBean;
import org.ncbo.stanford.util.BioPortalServerConstants;
import org.ncbo.stanford.util.BioPortalUtil;
import org.ncbo.stanford.util.BioportalSearch;
import org.ncbo.stanford.util.HTMLUtil;

/**
 * This class provides functionality to identify vocabulary entries from an ontology
 * in BioPortal for a given list of terms. The main method takes a CSV (or TSV) file,
 * containing terms organized in columns, and a BioPortal (virtual) ontology id, and 
 * genertaes a new CSV file, with the content of the input file, plus an extra column, 
 * which contains term URIs from the given BioPortal ontology that best matches the terms 
 * in the last column in the input file.
 * 
 * @author csnyulas
 *
 */
public class VocabularyGenerator extends UtilityWithOptionalArguments {

	private String ontVersionId;

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length < 3) {
			usage();
		}
		String separatorOption = extractSeparatorOptionFromArguments(args);
		int ignoreLinesOption = extractIgnoreLinesOptionFromArguments(args);
		String ontVersionIdOption = extractOntologyVersionIdOptionFromArguments(args);
		if (ontVersionIdOption == null) {
			usage();
		}
		String inputCsvFileName = args[args.length - 2];
		String outputCsvFileName = args[args.length - 1];
		
		VocabularyGenerator vocabGen = new VocabularyGenerator();
		vocabGen.setOntologyVersionId(ontVersionIdOption);
		if (separatorOption != null) {
			vocabGen.setCSVFieldSeparator(separatorOption);
		}
		vocabGen.setIgnoredLinesCount(ignoreLinesOption);
		try {
			vocabGen.generateOutput(inputCsvFileName, outputCsvFileName);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}


	private static void usage() {
		System.out.println("USAGE: VocabularyGenerator [-sC|-sT|-sc|-st] [-i[N]] -ont=BP_ONT_VERSION_ID INPUT_CSV_FILE OUTPUT_CSV_FILE");
		System.exit(0);
	}

	
	public void setOntologyVersionId(String ontVersionIdOption) {
		this.ontVersionId = ontVersionIdOption;
		
	}

	
	private void generateOutput(String inputCsvFileName,
			String outputCsvFileName) throws IOException {
		
		//open input & output files
		BufferedReader reader;
		BufferedWriter writer;
		try {
			reader = openInputStream(inputCsvFileName);
			writer = openOutputStream(outputCsvFileName);
		}
		catch (IOException ioe) {
			throw ioe;
		}
		if (reader == null || writer == null) {
			throw new IOException("Failure to open both input and output files");
		}
		
		//read and process input line by line
		String line = readFirstValidLine(reader);
		while (line != null) {
			processLine(line, writer);
			line = reader.readLine();
		}
		
		writer.close();
		reader.close();
		System.out.println("Done!");
	}
	
	
	private void processLine(String line, BufferedWriter writer) throws IOException {
		String[] parts = parseLine(line);
		String last = parts[parts.length - 1];

		//1. find individual
		String vocabURI = getBPSearchResult(ontVersionId, last);
		
		//2. write line to output
		writer.write(line + csvFieldSeparator + vocabURI);
		writer.newLine();
	}


	private String getBPSearchResult(String ontVersionId, String searchTerm) throws MalformedURLException, IOException {
		if (searchTerm == null || searchTerm.trim().length() == 0) {
			return null;
		}
		
		String searchText = searchTerm;
		try {
			searchText = HTMLUtil.encodeURI(searchTerm.replaceAll("/", " "));
		} catch (UnsupportedEncodingException e) {
			return null;
		}

		BioportalSearch sd = new BioportalSearch();
		String urlStr = BioPortalServerConstants.BP_REST_BASE
				+ BioPortalServerConstants.SEARCH_REST + "/" + searchText + "?"
				+ "ontologyids=" + ontVersionId 
				// + "&objecttypes=class&maxnumhits=20"
				;
		urlStr = BioPortalUtil.getUrlWithDefaultSuffix(urlStr);
		Page p = sd.getSearchResults(new URL(urlStr));
		SearchResultListBean data = p.getContents();
		
		SearchBean candidateRes = null;
		boolean first = true;
		for (SearchBean searchBean : data.getSearchResultList()) {
			if (searchTerm.equals(searchBean.getContents())) {
				System.out.println(searchTerm + ": "
						+ searchBean.getConceptId() + " "
						+ searchBean.getPreferredName() + " "
						+ searchBean.getContents());
				return searchBean.getConceptId();
			} else if (first) {
				candidateRes = searchBean;
				first = false;
			} else {
				candidateRes = selectBestMatch(searchTerm, candidateRes, searchBean);
			}

		}

		if (candidateRes == null) {
			return null;
		} else {
			System.out.println("WARNING: " + searchTerm + ": "
					+ candidateRes.getConceptId() + " "
					+ candidateRes.getPreferredName() + " "
					+ candidateRes.getContents());
			return candidateRes.getConceptId();
		}
	}
	
	private SearchBean selectBestMatch(String searchTerm, SearchBean currCandidateBean,
			SearchBean newSearchBean) {
		String currContent = currCandidateBean.getContents();
		String newContent = newSearchBean.getContents();
		
		String capCurrContent = currContent.toUpperCase();
		String capNewContent = newContent.toUpperCase();
		String capSearchTerm = searchTerm.toUpperCase();
		//if the shorter content contains the search term, return that search bean. (This case includes exact match)
		if (currContent.length() <= newContent.length() && currContent.contains(searchTerm)) {
			return currCandidateBean;
		}
		if (newContent.length() <= currContent.length() && newContent.contains(searchTerm)) {
			return newSearchBean;
		}
		//if there is a case insensitive match with the content, return that search bean.
		if (currContent.length() < newContent.length() && capCurrContent.equals(capSearchTerm)) {
			return currCandidateBean;
		}
		if (newContent.length() < currContent.length() && capNewContent.equals(capSearchTerm)) {
			return newSearchBean;
		}
		//if any of the content (i.e. even the longer one) contains the search term, return that search bean.
		if (currContent.contains(searchTerm)) {
			return currCandidateBean;
		}
		if (newContent.contains(searchTerm)) {
			return newSearchBean;
		}
		//if there is a case insensitive match with any of the content (i.e. even the longer one), return that search bean.
		if (capCurrContent.contains(capSearchTerm)) {
			return currCandidateBean;
		}
		if (capNewContent.contains(capSearchTerm)) {
			return newSearchBean;
		}
		//TODO add more logic
		// split search term by separators and see which search bean content contains more parts from the search term
		
		// also use record type
		
		return currCandidateBean;
	}
	
}
