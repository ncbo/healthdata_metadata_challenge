package org.healthdata.metadata.vocabulary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

import org.healthdata.metadata.HealthDataConstants;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import org.openrdf.sail.SailException;
import org.openrdf.sail.memory.MemoryStore;

/**
 * This class provides functionality to create a SKOS concept scheme from the 
 * content of an OWL ontology, by adding to the concept scheme 
 * either all the instances or all the descendants of a given class.
 * The main method of this class takes as argument:<br>
 *  - the option -inst or -hier, which specifies whether the instances or the
 *  class hierarchy of a given class should be added to the SKOS concept scheme
 *  - the input OWL ontology
 *  - the class that provides the filtering for the output
 *  - the name of the output OWL file
 *  - the name (URI) of the SKOS concept scheme in the ouptut
 *  
 * @author csnyulas
 *
 */
public class ConceptSchemeGenerator extends UtilityWithOptionalArguments {

	private static final String OPTION_INSTANCES_MODE = "-inst";
	private static final String OPTION_HIERARCHY_MODE = "-hier";
	
	private enum Mode {Instances, Hierarchy};

	private Mode operationMode;

	private MemoryStore sail;
	private SailRepository repository;
	private SailRepositoryConnection conn;

	/**
	 * @param args
	 */
	public static void main(String[] args){
		if (args == null || args.length != 5) {
			usage();
		}
		Mode opMode = parseOperationModeOption(args[0]);
		if (opMode == null) {
			usage();
		}
		String inputOWLFileName = args[1];
		String className = args[2];
		String outputOWLFileName = args[3];
		String conceptSchemeName = args[4];
		
		ConceptSchemeGenerator conceptSchemeGen = new ConceptSchemeGenerator();
		conceptSchemeGen.setOperationMode(opMode);
		
		conceptSchemeGen.generateOutput(inputOWLFileName, className, outputOWLFileName, conceptSchemeName);
	}


	private static void usage() {
		System.out.println("USAGE: ConceptSchemeGenerator -inst|-hier INPUT_OWL_FILE CLASS_NAME OUTPUT_OWL_FILE CONCEPT_SCHEME_NAME");
		System.exit(0);
	}


	private static Mode parseOperationModeOption(String arg) {
		if (arg.equals(OPTION_INSTANCES_MODE)) {
			return Mode.Instances;
		} else if (arg.equals(OPTION_HIERARCHY_MODE)) {
			return Mode.Hierarchy;
		} else {
			log.severe("Invalid argument: '" + arg + "'. It should be either '" + 
					OPTION_INSTANCES_MODE + "' or '" + OPTION_HIERARCHY_MODE + "'.");
			return null;
		}
	}

	
	public void setOperationMode(Mode mode) {
		this.operationMode = mode;
	}


	private void generateOutput(String inputOWLFileName, String className,
			String outputOWLFileName, String conceptSchemeName) {
		boolean exception = false;
		try {
			openInputOntology(inputOWLFileName);
		} catch (SailException e) {
			e.printStackTrace();
			exception = true;
		} catch (RDFParseException e) {
			e.printStackTrace();
			exception = true;
		} catch (RepositoryException e) {
			e.printStackTrace();
			exception = true;
		} catch (IOException e) {
			e.printStackTrace();
			exception = true;
		}
		if (exception) {
			log.severe("Operation will be aborted");
			return;
		}

		BufferedWriter writer;
		try {
			writer = openOutputStream(outputOWLFileName);
		} catch (IOException e) {
			e.printStackTrace();
			log.severe("Operation will be aborted");
			return;
		}
		
		try {
			if (operationMode == Mode.Instances) {
				createConceptSchemeForInstances(className, conceptSchemeName, writer);
			} else {
				createConceptSchemeForHierarchy(className, conceptSchemeName, writer);
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
			log.severe("Operation will be aborted");
			return;
		} catch (IOException e) {
			e.printStackTrace();
			log.severe("Operation will be aborted");
			return;
		}
		
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			conn.close();
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		System.out.println("Done!");
	}


	private void openInputOntology(String inputOWLFileName) throws SailException, RDFParseException, RepositoryException, IOException {
		File inputFile = new File(inputOWLFileName);
		String inputFileName = inputFile.getName();
		sail = new MemoryStore();
		sail.initialize();
		repository = new SailRepository(sail);
		conn = repository.getConnection();
		conn.add(inputFile, HealthDataConstants.HEALTHDATA_GOV_DATASET_BASE_URI + inputFileName, RDFFormat.RDFXML);
		
	}


	private void createConceptSchemeForInstances(String className,
			String conceptSchemeName, BufferedWriter writer) throws RepositoryException, IOException {
		URIImpl uriClass = new URIImpl(className);
		RepositoryResult<Statement> statements = conn.getStatements(null, RDF.TYPE, uriClass, false);
		
		writePreamble(writer, conceptSchemeName);
		String conceptSchemeStatement = "    <owl:NamedIndividual rdf:about=\"" + conceptSchemeName + "\">\n";

		while (statements.hasNext()) {
			Statement st = statements.next();
			Resource subject = st.getSubject();
			System.out.println(subject.stringValue());

			String conceptStatement = 
					  "    <owl:NamedIndividual rdf:about=\"" + subject + "\">\n"
					+ "        <rdf:type rdf:resource=\"&skos;Concept\"/>\n"
					+ "        <skos:inScheme rdf:resource=\"" + conceptSchemeName + "\"/>\n"
					+ "        <skos:topConceptOf rdf:resource=\"" + conceptSchemeName + "\"/>\n"
					//+ "        <rdfs:label>" + getTheLabel + "</rdfs:label>\n"
					+ "    </owl:NamedIndividual>\n";
			
			conceptSchemeStatement += "        <skos:hasTopConcept rdf:resource=\"" + subject + "\"/>\n";
			
			writer.write(conceptStatement);
			writer.newLine();
		}
		
		conceptSchemeStatement += "    </owl:NamedIndividual>\n";
		writer.write(conceptSchemeStatement);
		writer.newLine();
		
		writePrologue(writer);
	}


	private void createConceptSchemeForHierarchy(String className, String conceptSchemeName,
			BufferedWriter writer) {
		// TODO Auto-generated method stub
		System.out.println("Not implemented yet");
	}
	

	private void writePreamble(BufferedWriter writer, String conceptSchemeName) throws IOException {
		String text = "<?xml version=\"1.0\"?>\n"
				+ "\n"
				+ "<!DOCTYPE rdf:RDF [\n"
				+ "    <!ENTITY owl \"http://www.w3.org/2002/07/owl#\" >\n"
				+ "    <!ENTITY xsd \"http://www.w3.org/2001/XMLSchema#\" >\n"
				+ "    <!ENTITY skos \"http://www.w3.org/2004/02/skos/core#\" >\n"
				+ "    <!ENTITY rdfs \"http://www.w3.org/2000/01/rdf-schema#\" >\n"
				+ "    <!ENTITY rdf \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" >\n"
				+ "]>\n"
				+ "\n"
				+ "<rdf:RDF xmlns=\"http://purl.bioontology.org/healthdata/schemes#\"\n"
				+ "     xml:base=\"http://purl.bioontology.org/healthdata/schemes\"\n"
				+ "     xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"\n"
				+ "     xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\"\n"
				+ "     xmlns:owl=\"http://www.w3.org/2002/07/owl#\"\n"
				+ "     xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n"
				+ "     xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\">\n"
				+ "	 \n"
				+ "    <owl:Ontology rdf:about=\"http://purl.bioontology.org/healthdata/schemes\"/>\n"
				+ "\n"
				+ "    <owl:NamedIndividual rdf:about=\"" + conceptSchemeName + "\">\n"
				+ "        <rdf:type rdf:resource=\"&skos;ConceptScheme\"/>\n"
				+ "    </owl:NamedIndividual>\n";
				
		writer.write(text);
		writer.newLine();
	}

	private void writePrologue(BufferedWriter writer) throws IOException {
		String text = "</rdf:RDF>\n";
		writer.write(text);
		writer.newLine();
	}
	
}
