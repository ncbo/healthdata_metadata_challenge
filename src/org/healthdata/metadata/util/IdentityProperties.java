package org.healthdata.metadata.util;

import java.util.Properties;

public class IdentityProperties extends Properties {

	private static final long serialVersionUID = 8237655791100687585L;

	private static IdentityProperties singletonInstance = null;
	
	public static IdentityProperties getInstance() {
		if (singletonInstance == null) {
			singletonInstance = new IdentityProperties();
		}
		return singletonInstance;
	}
	
    @Override
    public synchronized boolean contains(Object value) {
    	return true;
    }
    
    @Override
    public synchronized boolean containsKey(Object key) {
    	return true;
    }
    
    @Override
    public boolean containsValue(Object value) {
    	return true;
    }
    
    @Override
    public synchronized Object get(Object key) {
    	return key;
    }
    
    @Override
    public String getProperty(String key) {
    	return key;
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
    	return key;
    }
}