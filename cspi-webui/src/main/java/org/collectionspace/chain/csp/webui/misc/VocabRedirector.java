/* Copyright 2010 University of Cambridge
 * Licensed under the Educational Community License (ECL), Version 2.0. You may not use this file except in 
 * compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at https://source.collectionspace.org/collection-space/LICENSE.txt
 */
package org.collectionspace.chain.csp.webui.misc;

import org.collectionspace.chain.csp.schema.Field;
import org.collectionspace.chain.csp.schema.Instance;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.chain.csp.schema.Spec;
import org.collectionspace.chain.csp.webui.main.Request;
import org.collectionspace.chain.csp.webui.main.WebMethod;
import org.collectionspace.chain.csp.webui.main.WebUI;
import org.collectionspace.csp.api.core.CSPRequestCache;
import org.collectionspace.csp.api.persistence.Storage;
import org.collectionspace.csp.api.ui.UIException;
import org.collectionspace.csp.api.ui.UIRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VocabRedirector implements WebMethod {
	private static final Logger log=LoggerFactory.getLogger(VocabRedirector.class);
	private Record r;
	
	public VocabRedirector(Record r) { this.r=r; }
	
	public void configure(WebUI ui, Spec spec) {}

	//old style
	private String pathFor(String in) {
		Field fd = (Field) r.getRepeatField(in);
		String weburl = fd.getAutocompleteInstance().getWebURL();
		
		return "/vocabularies/"+weburl; 
	}
	
	private JSONArray pathForAll(String fieldname) throws JSONException{
		JSONArray out = new JSONArray();

		Field fd = (Field) r.getRepeatField(fieldname);
		for(Instance autoc : fd.getAllAutocompleteInstances()){
			JSONObject instance = new JSONObject();
			instance.put("url","/vocabularies/"+autoc.getWebURL());
			instance.put("type",autoc.getID());
			instance.put("fullName",autoc.getTitle());
			out.put(instance);
		}
		
		return out;
	}
	private void redirect(CSPRequestCache cache,Storage storage,UIRequest request,String[] tail) throws UIException {
		try {
			JSONArray out = new JSONArray();
			out = pathForAll(tail[0]);
			request.sendJSONResponse(out);
		} catch (JSONException e) {
			throw new UIException("JSON building failed",e);
		}
	}
	
	public void run(Object in, String[] tail) throws UIException {
		Request q=(Request)in;
		redirect(q.getCache(),q.getStorage(),q.getUIRequest(),tail);
	}

}
