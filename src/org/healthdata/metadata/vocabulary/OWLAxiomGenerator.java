package org.healthdata.metadata.vocabulary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * This class provides functionality to generate OWL axioms from the content of 
 * a CSV (or TSV) file based on a given template. For each line in the input 
 * CSV file, the content of the template file will be copied in the output file
 * replacing all the placeholders of the form %$N%  - where N=1,2,3,... - with a 
 * value from the column 1,2,3,... <br>
 * This functionality is extremely useful, for example in creating OWL vocabularies 
 * from the content of CSV files.
 * 
 * @author csnyulas
 *
 */
public class OWLAxiomGenerator extends UtilityWithOptionalArguments {


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length < 3) {
			usage();
		}
		String separatorOption = extractSeparatorOptionFromArguments(args);
		int ignoreLinesOption = extractIgnoreLinesOptionFromArguments(args);
		String inputCsvFileName = args[args.length - 3];
		String templateFileName = args[args.length - 2];
		String outputOWLFileName = args[args.length - 1];
		
		OWLAxiomGenerator owlAxiomGen = new OWLAxiomGenerator();
		if (separatorOption != null) {
			owlAxiomGen.setCSVFieldSeparator(separatorOption);
		}
		owlAxiomGen.setIgnoredLinesCount(ignoreLinesOption);
		try {
			owlAxiomGen.generateOutput(inputCsvFileName, templateFileName, outputOWLFileName);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}


	private static void usage() {
		System.out.println("USAGE: OWLStatementGenerator [-sC|-sT|-sc|-st] [-i[N]] INPUT_CSV_FILE TEMPLATE_FILE OUTPUT_OWL_FILE");
		System.exit(0);
	}

	
	private void generateOutput(String inputCsvFileName, String templateFileName,
			String outputOWLFileName) throws IOException {
		
		//open input & output files
		BufferedReader reader;
		BufferedReader templReader;
		BufferedWriter writer;
		try {
			reader = openInputStream(inputCsvFileName);
			templReader = openInputStream(templateFileName);
			writer = openOutputStream(outputOWLFileName);
		}
		catch (IOException ioe) {
			throw ioe;
		}
		if (reader == null || templReader == null || writer == null) {
			throw new IOException("Failure to open all the three files");
		}
		
		//read template
		String template = "";
		String line = templReader.readLine();
		while (line != null) {
			template += line;
			line = templReader.readLine();
			
			//add a new line character after all lines, except the last one
			if (line != null) {
				template += "\n";
			}
		}
		templReader.close();
		
		//read and process input line by line
		line = readFirstValidLine(reader);
		while (line != null) {
			processLine(line, template, writer);
			line = reader.readLine();
		}
		
		writer.close();
		reader.close();
		System.out.println("Done!");
	}


	private void processLine(String line, String template, BufferedWriter writer) throws IOException {
		String[] parts = parseLine(line);
		
		//1. replace place holders in template with values from input
		String res = template;
		for (int i = 0; i < parts.length; i++) {
			res = res.replaceAll("%\\$" + (i + 1) + "%", parts[i]);
		}
		
		//2. write line to output
		writer.write(res);
		writer.newLine();
	}



}
