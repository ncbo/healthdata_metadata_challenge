package org.healthdata.metadata.vocabulary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class UtilityWithOptionalArguments {

	private static final String OPTION_SEPARATOR = "-s";
	private static final String OPTION_IGNORE = "-i";
	private static final String OPTION_ONTOLOGY_VERSION_ID = "-ont=";
	
	private static final String CSV_FIELD_SEPARATOR_TAB = "\t";
	private static final String CSV_FIELD_SEPARATOR_COMMA = ",";
	protected static final String DEFAULT_CSV_FIELD_SEPARATOR = CSV_FIELD_SEPARATOR_TAB;

	protected static Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	
	
	protected String csvFieldSeparator = DEFAULT_CSV_FIELD_SEPARATOR;
	protected int ignoredLinesCount = 0;
	
	
	public void setCSVFieldSeparator(String csvFieldSeparator) {
		this.csvFieldSeparator = csvFieldSeparator;
	}
	
	public void setIgnoredLinesCount(int ignoredLinesCount) {
		this.ignoredLinesCount = ignoredLinesCount;
	}
	
	
	protected static String extractSeparatorOptionFromArguments(String[] args) {
		for (String arg : args) {
			if (arg.startsWith(OPTION_SEPARATOR)) {
				String opt = arg.substring(OPTION_SEPARATOR.length());
				if (opt.toUpperCase().equals("T")) {
					return CSV_FIELD_SEPARATOR_TAB;
				}
				if (opt.toUpperCase().equals("C")) {
					return CSV_FIELD_SEPARATOR_COMMA;
				}
				log.warning("Invalid separator option '" + arg + "' will be ignored");
			}
		}
		
		return null;
	}
	
	protected static int extractIgnoreLinesOptionFromArguments(String[] args) {
		int res = 0;
		for (String arg : args) {
			if (arg.startsWith(OPTION_IGNORE)) {
				String opt = arg.substring(OPTION_IGNORE.length());
				if (opt.isEmpty()) {
					res = 1;
				}
				else {
					boolean error = false;
					try {
						Integer n = Integer.parseInt(opt);
						if (n != null) {
							res = n;
						} else {
							error = true;
						}
					} catch (NumberFormatException e) {
						error = true;
					}
					if (error) {
						log.warning("Invalid 'ignore lines' option. " +
								"The correct way to specify it, is either '-i' or '-iN', " +
								"where N is the number of lines at the beginning of the input file to be ignored.\n" +
								"The argument '" + arg + "' will be disregarded.");
					}
				}
			}
		}
		
		return res;
	}

	protected static String extractOntologyVersionIdOptionFromArguments(String[] args) {
		for (String arg : args) {
			if (arg.startsWith(OPTION_ONTOLOGY_VERSION_ID)) {
				return arg.substring(OPTION_ONTOLOGY_VERSION_ID.length());
			}
		}
		
		log.severe("Required 'Ontology version ID' option is missing. Please see usage...");
		return null;
	}


	protected BufferedReader openInputStream(String fileName) throws IOException {
		File in = new File(fileName);
		
		if ( ! in.exists() ) {
			log.severe("Input file " + fileName + " does not exist. Operation will be aborted.");
			return null;
		}

		return new BufferedReader(new FileReader(in));
	}

	
	protected BufferedWriter openOutputStream(String fileName) throws IOException {
		File out = null;
		
		if (fileName != null) {
			out = new File(fileName);
			try {
				return new BufferedWriter(new FileWriter(out));
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
				log.severe("Output file : " + fileName + " could not be created. Operation will be aborted.");
				return null;
			}
		}
		else {
			log.severe("Invalid output file name: " + fileName + ". Operation will be aborted.");
			return null;
		}
	}
	
	
	protected String readFirstValidLine(BufferedReader reader) throws IOException {
		String line = reader.readLine();
		
		//jump over ignored lines
		int i = 0;
		while (line != null && i < ignoredLinesCount) {
			line = reader.readLine();
			i++;
		}
		
		return line;
	}
	
	protected String[] parseLine(String line) {
		
		//TODO: Make sure you deal with fields that are enclosed in quotes!!!
		
		String[] parts = line.split(csvFieldSeparator);

		return parts;
	}

}
