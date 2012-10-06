package org.healthdata.metadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.healthdata.metadata.util.IdentityProperties;
import org.healthdata.metadata.util.LinkedProperties;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.BNodeImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.rdfxml.util.RDFXMLPrettyWriter;
import org.openrdf.sail.SailException;
import org.openrdf.sail.memory.MemoryStore;

/**
 * This class provides functionality to convert a single metadata file describing
 * a healthdata.org dataset to a new structure, where we can reuse more standard
 * vocabularies for the properties, and use URIs as their values whenever possible,
 * to enable a better publication of the metadata as linked data. 
 * The parameters of the conversion are specified by multiple Java properties file,
 * with one main properties file referring to other property files. Example 
 * properties file can be found in the resources folder, where main_configuration.properties
 * would be the main configuration file.
 * 
 * @author csnyulas
 *
 */
class DatasetMetadataUpdater {
	
	private static final String NS_DCAT = "http://www.w3.org/ns/dcat#";
	private static final Value URI_DATASET = new URIImpl(NS_DCAT + "Dataset");
	
	
	private static final String KEY_PRESERVE_PROPERTIES_WITH_URI_VALUES_FILE = "Preserve_Properties_With_URI_Values_File";
	private static final String KEY_PRESERVE_PROPERTIES_WITH_LITERAL_VALUES_FILE = "Preserve_Properties_With_Literal_Values_File";
	private static final String KEY_PRESERVE_PROPERTIES_WITH_BNODE_VALUES_FILE = "Preserve_Properties_With_BNode_Values_File";
	private static final String KEY_REPLACE_PROPERTIES_FILE = "Replace_Properties_Map_File";
	private static final String KEY_CONVERT_RELATIONS_TO_PROPERTIES_FILE = "Convert_dct:Relations_To_Properties_Map_File";
	private static final String KEY_PRESERVE_RELATIONS_FILE = "Preserve_dct:Relations_File";
	private static final String KEY_NEW_PROPERTIES_TO_VALUES_PROPERTIES_FILE = "New_Properties_To_Values_Map_File";
	private static final String KEY_URIS_TO_LABELS_PROPERTIES_FILE = "URIs_To_Labels_Map_File";
	private static final String KEY_SAMEAS_FILE = "SameAs_Map_File";
	
	
	private static final Resource ORIG_CONTEXT = new BNodeImpl("orig_context");
	private static final Resource NEW_CONTEXT = new BNodeImpl("new_context");
	
	private enum ValueType {URI, Literal, BNode};

	private MemoryStore sail;
	private SailRepository repository;
	private SailRepositoryConnection conn;
	private String configPropFilePathPrefix = "";
	private Map<String, String> prefixToNamespaceMap;


	public DatasetMetadataUpdater(String rdfFileName, String configPropFileName) throws SailException, RepositoryException, RDFParseException, IOException {
		File rdfFile = new File(rdfFileName);
		String configPropFileParent = new File(configPropFileName).getParent();
		if (configPropFileParent != null) {
			configPropFilePathPrefix = configPropFileParent + File.separator;
		}
		sail = new MemoryStore();
		sail.initialize();
		repository = new SailRepository(sail);
		conn = repository.getConnection();
		conn.add(rdfFile, HealthDataConstants.HEALTHDATA_GOV_DATASET_BASE_URI + rdfFileName, RDFFormat.RDFXML, ORIG_CONTEXT);
		
		addNewPrefixes();
		
		initializePrefixToNamespaceMap();
	}

	private void addNewPrefixes() throws RepositoryException {
		conn.setNamespace(HealthDataConstants.PREFIX_DBPEDIA_ONTOLOGY, HealthDataConstants.NS_DBPEDIA_ONTOLOGY);
		conn.setNamespace(HealthDataConstants.PREFIX_SCHEMA, HealthDataConstants.NS_SCHEMA);
		conn.setNamespace(HealthDataConstants.PREFIX_TIME, HealthDataConstants.NS_TIME);
	}

	private void initializePrefixToNamespaceMap() throws RepositoryException {
		prefixToNamespaceMap = new HashMap<String, String>();
		RepositoryResult<Namespace> namespaces = conn.getNamespaces();
		while (namespaces.hasNext()) {
			Namespace namespace = namespaces.next();
			prefixToNamespaceMap.put(namespace.getPrefix(), namespace.getName());
		}
	}

	
	public static void main(String[] args) {
		if (args == null || args.length < 2 || args.length > 3 ) {
			System.out.println("USAGE: DatasetMetadataUpdater RDF_INPUT_FILE [RDF_OUTPUT_FILE] CONFIG_PROPERTIES_FILE");
			return;
		}
		String rdfFileName = args[0];
		String configPropFileName = args[args.length-1];
		String resultFileName = null;
		if (args.length > 2) {
			resultFileName = args[1];
		}
		else {
			int extStartIdx = rdfFileName.lastIndexOf(".");
			if (extStartIdx > 0) {
				resultFileName = rdfFileName.substring(0, extStartIdx) + "-new" + rdfFileName.substring(extStartIdx);
			}
		}
		
		convertMetadataRdfFile(rdfFileName, resultFileName, configPropFileName);
	}

	public static void convertMetadataRdfFile(String rdfFileName,
			String resultFileName, String configPropFileName) {
		try {
			DatasetMetadataUpdater dsMetadataUpdater = new DatasetMetadataUpdater(rdfFileName, configPropFileName);
			Properties mainConfigProperties = dsMetadataUpdater.getProperties(configPropFileName);
			dsMetadataUpdater.doUpdate(mainConfigProperties);
			dsMetadataUpdater.writeToFile(resultFileName);
			dsMetadataUpdater.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	private void doUpdate(Properties configProperties) throws SailException, RepositoryException {
		RepositoryResult<Statement> statements = conn.getStatements(null, RDF.TYPE, URI_DATASET, false);
		if ( ! statements.hasNext() ) {
			System.out.println("Could not find Datasource declaration in the RDF file");
			return;
		}
		Resource dsResource = statements.next().getSubject();
		
		//this could be a viable alternative in case that listing "rdf:type" in the "to be preserved" properties file 
		//would not work, or it would have unwanted side effects:
		//conn.add(dsResource, RDF.TYPE, URI_DATASET, NEW_CONTEXT);
		
		if (configProperties.isEmpty()) {
			System.out.println("Main Configuration Properties is empty. Execution will be aborted.");
			return;
		}
		
		//step 1 & 4: 
		//step 3 (remove non URI values) is also addressed by separating the preservable properties by type of values
		copyValidProperties(getPropertiesForConfigKey(configProperties, KEY_PRESERVE_PROPERTIES_WITH_URI_VALUES_FILE), ValueType.URI, dsResource);
		copyValidProperties(getPropertiesForConfigKey(configProperties, KEY_PRESERVE_PROPERTIES_WITH_LITERAL_VALUES_FILE), ValueType.Literal, dsResource);
		copyValidProperties(getPropertiesForConfigKey(configProperties, KEY_PRESERVE_PROPERTIES_WITH_BNODE_VALUES_FILE), ValueType.BNode, dsResource);
		
		//step 1b
		replaceProperties(getPropertiesForConfigKey(configProperties, KEY_REPLACE_PROPERTIES_FILE), dsResource);

		//step 2 & 4:
		transformKeyValuesToTriples(getPropertiesForConfigKey(configProperties, KEY_CONVERT_RELATIONS_TO_PROPERTIES_FILE), dsResource);

		//step 2b:
		copyValidKeyValues(getPropertiesForConfigKey(configProperties, KEY_PRESERVE_RELATIONS_FILE), dsResource);

		//step 5:
		//dates are converted xsd:date during steps 1 & 2

		//step 5b:
		transformCoverage(dsResource);
		
		//step 6:
		addNewPropertyValues(getPropertiesForConfigKey(configProperties, KEY_NEW_PROPERTIES_TO_VALUES_PROPERTIES_FILE), dsResource);
		
		//step 7:
		addLabelsForURIs(getPropertiesForConfigKey(configProperties, KEY_URIS_TO_LABELS_PROPERTIES_FILE), dsResource);
		
		//step 8:
		addSameAsStatements(getPropertiesForConfigKey(configProperties, KEY_SAMEAS_FILE), dsResource);
		
	}

	
	//*********************************************************************//

	private void copyValidProperties(Properties propertyList, ValueType valueType, Resource dsResource) throws RepositoryException {
		Enumeration<?> keys = propertyList.propertyNames();
		while (keys.hasMoreElements()){
			String key = keys.nextElement().toString();
			//copy triples involving the current property that have dsResource as their subject
			RepositoryResult<Statement> statements = conn.getStatements(dsResource, new URIImpl(convertNameToAbsoluteURI(key)), null, false, ORIG_CONTEXT);
			while (statements.hasNext()) {
				Statement statement = statements.next();
				Value value = statement.getObject();
				if (valueType == ValueType.URI && value instanceof URI) {
					conn.add(statement, NEW_CONTEXT);
				}
				else if (valueType == ValueType.Literal && value instanceof Literal) {
					statement = fixDateObjectIfNecessary(statement);
					conn.add(statement, NEW_CONTEXT);
				}
				else if (valueType == ValueType.BNode && value instanceof BNode) {
					conn.add(statement, NEW_CONTEXT);
					copyBNode((BNode)value);
				}
				else {
					assert false : "The object of statement '" + statement + "' is not of the expected type: " + valueType;
				}
			}
			
			//copy triples involving the current property that have dsResource as their object
			statements = conn.getStatements(null, new URIImpl(convertNameToAbsoluteURI(key)), dsResource, false, ORIG_CONTEXT);
			conn.add(statements, NEW_CONTEXT);
		}
	}


	//*********************************************************************//
	
	private void replaceProperties(Properties propertiesToPropertiesMap, Resource dsResource) throws RepositoryException {
		
		Enumeration<?> keys = propertiesToPropertiesMap.propertyNames();
		while (keys.hasMoreElements()){
			String key = keys.nextElement().toString();
			String value = propertiesToPropertiesMap.getProperty(key);

			//separate multiple property names in the key (the second property name being a property on the BNode that is the value of the first property)
			String[] keyParts = splitMapKey(key);
			String oldPropertyName = keyParts[0];
			String oldSubPropertyName = (keyParts.length > 1 ? keyParts[1] : null);
			//separate new property name from file name of the value-to-URI map
			String[] valueParts = splitMapValue(value);
			String newPropertyName = valueParts[0];
			Properties propertyValueToURIMap = null;
			if (valueParts.length > 1) {
				propertyValueToURIMap = getProperties(valueParts[1]);
			}
			
			transformProperty(dsResource, oldPropertyName, oldSubPropertyName ,newPropertyName, propertyValueToURIMap);
		}
	}

	private void transformProperty(Resource dsResource, String oldPropertyName, String oldSubPropertyName, 
			String newPropertyName, Properties valueToURIMap) throws RepositoryException {
		RepositoryResult<Statement> statements = conn.getStatements(dsResource, new URIImpl(convertNameToAbsoluteURI(oldPropertyName)), null, false, ORIG_CONTEXT);
		if ( ! statements.hasNext() ) {
			System.out.println("Could not find property '" + oldPropertyName + "' in the RDF file");
			return;
		}

		URIImpl newPropURI = new URIImpl(convertNameToAbsoluteURI(newPropertyName));

		//repeat for all possible values of the oldProperty
		while (statements.hasNext()) {
			Statement statement = statements.next();
			Value object = statement.getObject();
			
			//if simple property to property conversion
			if (oldSubPropertyName == null) {
				if (object instanceof URI || object instanceof Literal) { 
					Value newValue = convertValue(valueToURIMap, object);
					conn.add(dsResource, newPropURI, newValue, NEW_CONTEXT);
				}
				else {	//object is a BNode
					System.out.println("Warning: The property '" + oldPropertyName + "' has a BNode property value. " +
							"We replace the property with '" + newPropertyName + "' and keep the BNode property value. " +
									"This may not be what you want!");
					conn.add(dsResource, newPropURI, object, NEW_CONTEXT);
					copyBNode((BNode)object);
				}
			}
			else {	//i.e. in case we have a subproperty
				URIImpl oldSubPropURI = new URIImpl(convertNameToAbsoluteURI(oldSubPropertyName));
				
				if (object instanceof BNode) {
					RepositoryResult<Statement> bNodeStatements = conn.getStatements((BNode)object, oldSubPropURI, null, false, ORIG_CONTEXT);
					if ( ! bNodeStatements.hasNext() ) {
						System.out.println("Could not find sub-property '" + oldSubPropertyName + "' in the RDF file");
						return;
					}
					
					while (bNodeStatements.hasNext()) {
						Statement bNodeStatement = bNodeStatements.next();
						Value bNodeObject = bNodeStatement.getObject();
						Value newValue = convertValue(valueToURIMap, bNodeObject);
						conn.add(dsResource, newPropURI, newValue, NEW_CONTEXT);
					}
				}
				else {
					System.out.println("The statement '" + statement + "' will be ignored, because the value of the '" + oldPropertyName + "' property is not a BNode, " +
							"and it does not have a subproperty '" + oldSubPropertyName + "'.");
				}
			}
		}
	}
	
	
	//*********************************************************************//
	
	private void transformKeyValuesToTriples(Properties relationsToPropertiesMap, 
			Resource dsResource) throws SailException, RepositoryException {
		
		Enumeration<?> keys = relationsToPropertiesMap.propertyNames();
		while (keys.hasMoreElements()){
			String key = keys.nextElement().toString();
			String value = relationsToPropertiesMap.getProperty(key);

			// remove suffixes from keys (of form " (N)", where N is a number) that were added to ensure uniquness of keys
			Pattern pattern = Pattern.compile("(\\s*\\(\\d+\\))$");
			Matcher matcher = pattern.matcher(key);
			if (matcher.find()) {
				key = matcher.replaceFirst("");
			}
			
			//separate property name from file name of the value-to-URI map
			String[] valueParts = splitMapValue(value);
			String propertyName = valueParts[0];
			Properties propertyValueToURIMap = null;
			if (valueParts.length > 1) {
				propertyValueToURIMap = getProperties(valueParts[1]);
			}
			
			transformKeyValueToTriple(dsResource, key, propertyName, propertyValueToURIMap);
		}
	}
	
	private void transformKeyValueToTriple(Resource dsResource, 
			String key, String newProperty, Properties valueToURIMap) throws SailException, RepositoryException {
		
		Value object = getRelationValueForKey(key);
		if (object == null) {
			return;
		}
		Value newValue = convertValue(valueToURIMap, object);
		
		Statement newStatement = new StatementImpl(dsResource, new URIImpl(newProperty), newValue);
		newStatement = fixDateObjectIfNecessary(newStatement);
		
		conn.add(newStatement, NEW_CONTEXT);
		conn.commit();

	}

	private Value getRelationValueForKey(String key) throws RepositoryException {
		RepositoryResult<Statement> statements;
		Resource relation = getRelationBNode(key);
		if (relation == null) {
			return null;
		}
		statements = conn.getStatements(relation, RDF.VALUE, null, true);
		if ( ! statements.hasNext() ) {
			System.out.println("Could not find rdf:value on the dct:relation with label '" + key + "' in the RDF file");
			return null;
		}
		Value object = statements.next().getObject();
		return object;
	}

	private Resource getRelationBNode(String key) throws RepositoryException {
		RepositoryResult<Statement> statements = conn.getStatements(null, RDFS.LABEL, new LiteralImpl(key), true);
		
		Resource relation = null;
		while (statements.hasNext()) {
			relation = statements.next().getSubject();
			RepositoryResult<Statement> relStatements = conn.getStatements(null, new URIImpl(HealthDataConstants.URI_PROP_DCT_RELATION), relation, false, ORIG_CONTEXT);
			if (relStatements.hasNext()) {
				break;
			}
		}
		if (relation == null) {
			System.out.println("Could not find dct:relation with label '" + key + "' in the RDF file");
		}
		
		return relation;
	}
	
	
	//*********************************************************************//
	
	private void copyValidKeyValues(Properties propertyList, 
			Resource dsResource) throws SailException, RepositoryException {
		
		Enumeration<?> keys = propertyList.propertyNames();
		while (keys.hasMoreElements()){
			String key = keys.nextElement().toString();
			copyValidKeyValue(dsResource, key);
		}
	}

	private void copyValidKeyValue(Resource dsResource, 
			String key) throws SailException, RepositoryException {
		
		Resource relation = getRelationBNode(key);
		if (relation == null) {
			return;
		}

		RepositoryResult<Statement> relationStatements = conn.getStatements(dsResource, 
				new URIImpl(HealthDataConstants.URI_PROP_DCT_RELATION), relation, false, ORIG_CONTEXT);
		conn.add(relationStatements, NEW_CONTEXT);
		copyBNode((BNode)relation);
	}

	
	//*********************************************************************//

	private void transformCoverage(Resource dsResource) throws RepositoryException {
		Value covStart = getRelationValueForKey(HealthDataConstants.KEY_COVERAGE_PERIOD_START);
		Value covEnd = getRelationValueForKey(HealthDataConstants.KEY_COVERAGE_PERIOD_END);
		if (covStart == null && covEnd == null) {
			System.out.println("Can't convert coverage, because necessary input " +
					"(\"" + HealthDataConstants.KEY_COVERAGE_PERIOD_START + 
					"\" and \"" + HealthDataConstants.KEY_COVERAGE_PERIOD_END + 
					"\") is missing from RDF file");
			return;
		}
		if (covStart == null || covEnd == null) {
			System.out.println("Converted coverage will be incomplete, because necessary input is missing from RDF file. " +
					"\"" + HealthDataConstants.KEY_COVERAGE_PERIOD_START + "\": " + covStart + 
					", \"" + HealthDataConstants.KEY_COVERAGE_PERIOD_END + "\": " + covEnd + ".");
			return;
		}
		/*
  dct:temporal [
    a dct:PeriodOfTime, time:Interval;
    time:hasBeginning [  
      a time:Instant;
      time:inXSDDateTime "2011-01-01T00:00:00-05:00"^^xsd:dateTime.
	];
	time:hasEnd [
	  a time:Instant;
	  time:inXSDDateTime "2011-12-31T23:59:59-05:00"^^xsd:dateTime.
	].
  ];  
		 */
		ValueFactory factory = conn.getValueFactory();
		BNode bNodeCoverage = factory.createBNode();
		conn.add(dsResource, new URIImpl(HealthDataConstants.URI_PROP_DCT_TEMPORAL), bNodeCoverage, NEW_CONTEXT);
		conn.add(bNodeCoverage, RDF.TYPE, new URIImpl(HealthDataConstants.URI_CLASS_DCT_PERIOD_OF_TIME), NEW_CONTEXT);
		conn.add(bNodeCoverage, RDF.TYPE, new URIImpl(HealthDataConstants.URI_CLASS_TIME_INTERVAL), NEW_CONTEXT);
		
		if (covStart != null) {
			BNode bNodeBeginning = factory.createBNode();
			conn.add(bNodeCoverage, new URIImpl(HealthDataConstants.URI_PROP_TIME_HAS_BEGINNING), bNodeBeginning, NEW_CONTEXT);
			conn.add(bNodeBeginning, RDF.TYPE, new URIImpl(HealthDataConstants.URI_CLASS_TIME_INSTANT), NEW_CONTEXT);
			conn.add(bNodeBeginning, new URIImpl(HealthDataConstants.URI_PROP_TIME_IN_XSD_DATETIME), 
					new LiteralImpl(convertDateToXsdDate(covStart.stringValue())), NEW_CONTEXT);
		}

		if (covEnd != null) {
			BNode bNodeEnd = factory.createBNode();
			conn.add(bNodeCoverage, new URIImpl(HealthDataConstants.URI_PROP_TIME_HAS_END), bNodeEnd, NEW_CONTEXT);
			conn.add(bNodeEnd, RDF.TYPE, new URIImpl(HealthDataConstants.URI_CLASS_TIME_INSTANT), NEW_CONTEXT);
			conn.add(bNodeEnd, new URIImpl(HealthDataConstants.URI_PROP_TIME_IN_XSD_DATETIME), 
					new LiteralImpl(convertDateToXsdDate(covEnd.stringValue())), NEW_CONTEXT);
		}
	}
	

	
	//*********************************************************************//
	
	private void addNewPropertyValues(Properties newPropertyValueMap, Resource dsResource) throws RepositoryException {
		Enumeration<?> keys = newPropertyValueMap.propertyNames();
		while (keys.hasMoreElements()){
			String key = keys.nextElement().toString();
			String value = newPropertyValueMap.getProperty(key);
			if (value == null || value.isEmpty()) {
				System.out.println("There is no value specified for property '" + key + "' in the '" + KEY_NEW_PROPERTIES_TO_VALUES_PROPERTIES_FILE + "' configuration file. Entry will be ignored.");
				continue;
			}
			Value object = null;
			try {
				if ( ! value.contains("^^") && ! value.startsWith("\"")) {
					object = new URIImpl(value);
				}
			}
			catch (IllegalArgumentException e) {
				//value is not a well formed URI. Do nothing here, we deal with this case bellow
			}
			if (object == null) {
				object = new LiteralImpl(value);
			}
			conn.add(dsResource, new URIImpl(convertNameToAbsoluteURI(key)), object, NEW_CONTEXT);
		}
	}

	
	//*********************************************************************//
	
	private void addLabelsForURIs(Properties uriToLabelMap, Resource dsResource) throws RepositoryException {
		ArrayList<URI> listOfURIValues = getLisOfURIPropertyValues(dsResource);
		
		for (URI uri : listOfURIValues){
			String key = uri.stringValue();
			String value = uriToLabelMap.getProperty(key);
			if (value == null || value.isEmpty()) {
				System.out.println("There is no value specified for property '" + key + "' in the '" + KEY_URIS_TO_LABELS_PROPERTIES_FILE + "' configuration file. Entry will be ignored.");
				continue;
			}
			conn.add(new URIImpl(convertNameToAbsoluteURI(key)), RDFS.LABEL, new LiteralImpl(value), NEW_CONTEXT);
		}
	}

	private ArrayList<URI> getLisOfURIPropertyValues(Resource dsResource)
			throws RepositoryException {
		ArrayList<URI> listOfURIValues = new ArrayList<URI>();
		RepositoryResult<Statement> statements = conn.getStatements(dsResource, null, null, false, NEW_CONTEXT);
		while (statements.hasNext()) { 
			Statement statement = statements.next();
			Value object = statement.getObject();
			if (object instanceof URI) {
				listOfURIValues.add((URI) object);
			}
		}
		return listOfURIValues;
	}

	
	//*********************************************************************//
	
	private void addSameAsStatements(Properties sameAsMap, Resource dsResource) throws RepositoryException {
		ArrayList<URI> listOfURIValues = getLisOfURIPropertyValues(dsResource);
		
		for (URI uri : listOfURIValues){
			String key = uri.stringValue();
			String value = sameAsMap.getProperty(key);
			if (value == null || value.isEmpty()) {
				//System.out.println("There is no value specified for property '" + key + "' in the '" + KEY_SAMEAS_FILE + "' configuration file. Entry will be ignored.");
				continue;
			}
			conn.add(new URIImpl(convertNameToAbsoluteURI(key)), OWL.SAMEAS, new URIImpl(convertNameToAbsoluteURI(value)), NEW_CONTEXT);
		}
	}

	//*********************************************************************//
	
	private void writeToFile(String resultFileName) throws RepositoryException, RDFHandlerException, IOException {
		File outputFile = new File(resultFileName);
		OutputStream os = new FileOutputStream(outputFile);
		RDFXMLPrettyWriter writer = new RDFXMLPrettyWriter(os);
		//in order the following statement to compile the sesame-rio-rdfxml-3.0-alpha1.jar must be included at the top of the CLASSPATH
		//writer.setBaseURI(HealthDataConstants.HEALTHDATA_GOV_DATASET_BASE_URI);
		conn.export(writer, NEW_CONTEXT);
		os.close();
	}
	
	
	private void close() throws SailException, RepositoryException {
		conn.close();
		sail.shutDown();
	}

	
	//**************************** Utility functions *****************************************//
	
	private Properties getPropertiesForConfigKey(Properties configProperties, String key) {
		String propertiesFileName = configProperties.getProperty(key);
		
		if (propertiesFileName == null) {
			System.out.println("WARNING: There was no value specified for the key: '" + key + "' in the configuration properties files!");
			return null;
		}
		else {
			return getProperties(propertiesFileName);
		}
	}
	
	
	private Properties getProperties(String propertiesFileName) {
		Properties properties = new LinkedProperties();
		try {
			if (HealthDataConstants.MAP_LITERAL_TO_URI.equals(propertiesFileName)) {
				properties = IdentityProperties.getInstance();
			}
			else {
				//if it is not fully specified, use the same path as for the main configuration file 
				if ( new File(propertiesFileName).getParent() == null ) {
					propertiesFileName = configPropFilePathPrefix + propertiesFileName;
				}
				File propertiesFile = new File(propertiesFileName);
				if (propertiesFile.exists()) {
					FileReader reader = new FileReader(propertiesFile);
					properties.load(reader);
					reader.close();
				}
				else {
					System.out.println("WARNING: '" + propertiesFile + "' could not be found. Metadata update will not work as excpected.");
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return properties;
	}
	
	
	private void copyBNode(BNode bNode) throws RepositoryException {
		RepositoryResult<Statement> bNodeStatements = conn.getStatements(bNode, null, null, false, ORIG_CONTEXT);
		conn.add(bNodeStatements, NEW_CONTEXT);
	}
	
	
	private String convertNameToAbsoluteURI(String id) {		
		String uri = id;
		if (id.contains(":")) {
			String possiblePrefix = id.substring(0, id.indexOf(":"));
			String possibleNs = prefixToNamespaceMap.get(possiblePrefix);
			if (possibleNs != null) {
				uri = possibleNs + id.substring(possiblePrefix.length() + 1); //copy substring after prefix and the ":" character
			}
		}
		return uri ;
	}


	private Value convertValue(Properties valueToURIMap, Value currValue) {
		Value newValue = currValue;
		if (valueToURIMap != null) {
			String uriForValue = valueToURIMap.getProperty(currValue.stringValue());
			if (uriForValue != null) {
				newValue = new URIImpl(uriForValue);
			}
		}
		return newValue;
	}

	
	private Statement fixDateObjectIfNecessary(Statement stm) {
		String strObject = stm.getObject().stringValue();
		if (strObject.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d")) { 
			Literal dateObject = new LiteralImpl(convertDateToXsdDate(strObject));
			return new StatementImpl(stm.getSubject(), stm.getPredicate(), dateObject);
		}
		else if (strObject.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d")) {
			String[] dateParts = strObject.split(" ");
			Literal dateObject = new LiteralImpl(convertDateToXsdDate(dateParts[0], dateParts[1]));
			return new StatementImpl(stm.getSubject(), stm.getPredicate(), dateObject);
		}
		else {
			return stm;
		}
	}
	
	
	private String convertDateToXsdDate(String strDate) {
		return convertDateToXsdDate(strDate, "00:00:00");
	}
	
	private String convertDateToXsdDate(String strDate, String strTime) {
		return "\"" + strDate + "T" + strTime + "\"^^xsd:datetime";
	}
	
	
	private String[] splitMapKey(String value) {
		return value.split("\\s*\\>\\s*");
	}
	
	
	private String[] splitMapValue(String value) {
		return value.split("\\s*\\|\\s*");
	}
	
}