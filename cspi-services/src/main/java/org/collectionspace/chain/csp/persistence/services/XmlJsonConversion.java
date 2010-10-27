/* Copyright 2010 University of Cambridge
 * Licensed under the Educational Community License (ECL), Version 2.0. You may not use this file except in 
 * compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at https://source.collectionspace.org/collection-space/LICENSE.txt
 */
package org.collectionspace.chain.csp.persistence.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.collectionspace.chain.csp.persistence.services.vocab.URNProcessor;
import org.collectionspace.chain.csp.schema.Field;
import org.collectionspace.chain.csp.schema.FieldSet;
import org.collectionspace.chain.csp.schema.Group;
import org.collectionspace.chain.csp.schema.Instance;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.chain.csp.schema.Repeat;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlJsonConversion {
	private static final Logger log=LoggerFactory.getLogger(XmlJsonConversion.class);
	private static void addFieldToXml(Element root,Field field,JSONObject in) throws JSONException {
		String value=in.optString(field.getID());
		Element element=root.addElement(field.getServicesTag());
		element.addText(value);
	}
	
	//XXX could refactor this and addRepeatToXML as this is what happens in the middle of addRepeatToXML
	private static void addGroupToXml(Element root, Group group, JSONObject in, String section) throws JSONException, UnderlyingStorageException{
		Element element=root;

		if(group.hasServicesParent()){
			for(String path : group.getServicesParent()){
				if(path !=null){
					element=element.addElement(path);
				}
			}
		}

		Object value=null;
		value=in.opt(group.getID());
		
		if(value==null || ((value instanceof String) && StringUtils.isBlank((String)value)))
			return;
		if(value instanceof String) { // And sometimes the services ahead of the UI
			JSONObject next=new JSONObject();
			next.put(group.getID(),value);
			value=next;
		}
		if(!(value instanceof JSONObject))
			throw new UnderlyingStorageException("Bad JSON in repeated field: must be string or object for group field not an array - that would a repeat field");
		JSONObject object=(JSONObject)value;
		

		Element groupelement=element;

			groupelement=element.addElement(group.getServicesTag());
			Object one_value=object;
			if(one_value==null || ((one_value instanceof String) && StringUtils.isBlank((String)one_value)))
			{ //do nothing 
				
			}
			else if(one_value instanceof String) {
				// Assume it's just the first entry (useful if there's only one)
				FieldSet[] fs=group.getChildren();
				if(fs.length<1){ //do nothing 
					
				}
				else{
					JSONObject d1=new JSONObject();
					d1.put(fs[0].getID(),one_value);
					addFieldSetToXml(groupelement,fs[0],d1,section);
				}
			} else if(one_value instanceof JSONObject) {
				for(FieldSet fs : group.getChildren())
					addFieldSetToXml(groupelement,fs,(JSONObject)one_value,section);
			}
		element=groupelement;
	}
	
	private static void addRepeatToXml(Element root,Repeat repeat,JSONObject in,String section) throws JSONException, UnderlyingStorageException {

		Element element=root;
		if(repeat.hasServicesParent()){
			for(String path : repeat.getServicesParent()){
				if(path !=null){
					element=element.addElement(path);
				}
			}
		}
		else if(!repeat.getXxxServicesNoRepeat()) {	// Sometimes the UI is ahead of the services layer
			element=root.addElement(repeat.getServicesTag());
		}
		Object value=null;
		if(repeat.getXxxUiNoRepeat()) { // and sometimes the app ahead of the services
			FieldSet[] children=repeat.getChildren();
			if(children.length==0)
				return;
			addFieldSetToXml(element,children[0],in,section);
			return;
		} else {
			value=in.opt(repeat.getID());			
		}		
		
		if(value==null || ((value instanceof String) && StringUtils.isBlank((String)value)))
			return;
		if(value instanceof String) { // And sometimes the services ahead of the UI
			JSONArray next=new JSONArray();
			next.put(value);
			value=next;
		}
		if(!(value instanceof JSONArray))
			throw new UnderlyingStorageException("Bad JSON in repeated field: must be string or array for repeatable field");
		JSONArray array=(JSONArray)value;
		
		//reorder the list if it has a primary
		//XXX this will be changed when service layer accepts non-initial values as primary
		if(repeat.hasPrimary()){
			Stack<Object> orderedarray = new Stack<Object>();
			for(int i=0;i<array.length();i++) {
				Object one_value=array.get(i);
				if(one_value instanceof JSONObject) {
					if(((JSONObject) one_value).has("_primary")){
						if(((JSONObject) one_value).getBoolean("_primary")){
							orderedarray.add(0, one_value);
							continue;
						}
					}					
				}
				orderedarray.add(one_value);
			}
			JSONArray newarray = new JSONArray();
			int j=0;
			for(Object obj : orderedarray){
				newarray.put(j, obj);
				j++;
			}
			array = newarray;
		}
		Element repeatelement=element;
		for(int i=0;i<array.length();i++) {
			if(repeat.hasServicesParent()){
				repeatelement=element.addElement(repeat.getServicesTag());
			}
			Object one_value=array.get(i);
			if(one_value==null || ((one_value instanceof String) && StringUtils.isBlank((String)one_value)))
				continue;
			if(one_value instanceof String) {
				// Assume it's just the first entry (useful if there's only one)
				FieldSet[] fs=repeat.getChildren();
				if(fs.length<1)
					continue;
				JSONObject d1=new JSONObject();
				d1.put(fs[0].getID(),one_value);
				addFieldSetToXml(repeatelement,fs[0],d1,section);
			} else if(one_value instanceof JSONObject) {
				for(FieldSet fs : repeat.getChildren())
					addFieldSetToXml(repeatelement,fs,(JSONObject)one_value,section);
			}
		}
		element=repeatelement;
	}

	private static void addFieldSetToXml(Element root,FieldSet fs,JSONObject in,String section) throws JSONException, UnderlyingStorageException {
		if(!section.equals(fs.getSection()))
			return;
		if(fs instanceof Field)
			addFieldToXml(root,(Field)fs,in);
		else if(fs instanceof Group){
			addGroupToXml(root,(Group)fs,in,section);
		}
		else if(fs instanceof Repeat){
			addRepeatToXml(root,(Repeat)fs,in,section);
		}
	}
	
	public static Document convertToXml(Record r,JSONObject in,String section) throws JSONException, UnderlyingStorageException {
		Document doc=DocumentFactory.getInstance().createDocument();
		String[] parts=r.getServicesRecordPath(section).split(":",2);
		String[] rootel=parts[1].split(",");
		Element root=doc.addElement(new QName(rootel[1],new Namespace("ns2",rootel[0])));
		for(FieldSet f : r.getAllServiceFields()) {
			addFieldSetToXml(root,f,in,section);
		}
		String test = doc.asXML();
		//log.debug(doc.asXML());
		return doc;
	}
	
	@SuppressWarnings("unchecked")
	private static void addFieldToJson(JSONObject out,Element root,Field f) throws JSONException {
		List nodes=root.selectNodes(f.getServicesTag());
		if(nodes.size()==0)
			return;
		// XXX just add first
		Element el=(Element)nodes.get(0);
		addExtraToJson(out, el, f);
		out.put(f.getID(),el.getText());
	}
	
	//Fields that have an autocomplete tag, should also have a sibling with the de-urned version of the urn to display nicely
	private static void addExtraToJson(JSONObject out,Element  el, Field f) throws JSONException{
		if(f.hasAutocompleteInstance()){
			String deurned = getDeURNedValue(f, el.getText());
			if(deurned !=""){
				out.put("de-urned-"+f.getID(), deurned);
			}
		}
	}

	private static String getDeURNedValue(Field f, String urn) throws JSONException {
		//add a field with the de-urned version of the urn
		if(urn.isEmpty() || urn == null){
			return "";
		}
		//support multiassign of autocomplete instances
		for ( Instance ins : f.getAllAutocompleteInstances() ){
			if(ins !=null){ // this authority hasn't been implemented yet
				String urnsyntax = ins.getRecord().getURNSyntax();
				URNProcessor urnp = new URNProcessor(urnsyntax);
				try {
					return urnp.deconstructURN(urn,false)[5];
				} catch (ExistException e) {
					continue;
				} catch (UnderlyingStorageException e) {
					continue;
				}
			}
			
		}
		return "";
	}

	private static void buildFieldList(List<String> list,FieldSet f) {
		if(f instanceof Repeat){
			list.add(f.getID());
			for(FieldSet a : ((Repeat)f).getChildren()){
				//if(a instanceof Repeat)
				//	list.add(a.getID());
				if(a instanceof Field){
					list.add(a.getID());
				}
				buildFieldList(list,a);
			}
		}
		if(f instanceof Field){
			list.add(f.getID());
		}
	}
	
	/* Repeat syntax is challenging for dom4j */
	private static List<Map<String, List <Element> >> extractRepeats(Element container,FieldSet f) {
		List<Map<String,List<Element>>> out=new ArrayList<Map<String,List<Element>>>();
		// Build index so that we can see when we return to the start
		List<String> fields=new ArrayList<String>();
		List<Element> repeatdatatypestuff = new ArrayList<Element>();
		buildFieldList(fields,f);
		Map<String,Integer> field_index=new HashMap<String,Integer>();

		for(int i=0;i<fields.size();i++){
			field_index.put(fields.get(i),i);
		}
		// Iterate through
		Map<String, List <Element> > member=null;
		int prev=Integer.MAX_VALUE;
		for(Object node : container.selectNodes("*")) {

			if(!(node instanceof Element))
				continue;
			Integer next=field_index.get(((Element)node).getName());
			if(next==null)
				continue;
			if(next<prev) {
				// Must be a new instance
				if(member!=null)
					out.add(member);
				member=new HashMap<String, List <Element> >();
				repeatdatatypestuff = new ArrayList<Element>();
			}
			prev=next;
			repeatdatatypestuff.add((Element)node);
			member.put(((Element)node).getName(),repeatdatatypestuff);
		}
		if(member!=null)
			out.add(member);
		
		return out;
	}
	

	private static List<String> FieldListFROMConfig(FieldSet f) {
		List<String> children = new ArrayList<String>();
		
		if(f instanceof Repeat){
			for(FieldSet a : ((Repeat)f).getChildren()){
				children.add(a.getID());
			}
		}
		if(f instanceof Group){
			for(FieldSet a : ((Group)f).getChildren()){
				children.add(a.getID());
			}
		}
		if(f instanceof Field){
			
		}
		return children;
	}
	
	/* Repeat syntax is challenging for dom4j */
	private static JSONArray extractRepeatData(Element container,FieldSet f) throws JSONException {
		
		List<Map<String,List<Element>>> out=new ArrayList<Map<String,List<Element>>>();
		JSONArray newout = new JSONArray();
		// Build index so that we can see when we return to the start
		List<String> fields = FieldListFROMConfig(f);
		
		Map<String,Integer> field_index=new HashMap<String,Integer>();
		
		for(int i=0;i<fields.size();i++){
			field_index.put(fields.get(i),i);
		}
		JSONObject test = new JSONObject();
		JSONArray testarray = new JSONArray();
		// Iterate through
		Integer prev=Integer.MAX_VALUE;
		for(Object node : container.selectNodes("*")) {
			if(!(node instanceof Element))
				continue;
			Integer next=field_index.get(((Element)node).getName());
			if(next==null)
				continue;
			if(next!=prev) {
				// Must be a new instance
				if(test.length()>0){
					newout.put(test);
				}
				test = new JSONObject();
				testarray = new JSONArray();
			}
			prev=next;
			testarray.put((Element)node);
			test.put(((Element)node).getName(), testarray);
		}
		if(test.length()>0){
			newout.put(test);
		}
		return newout;
	}
	
	
	@SuppressWarnings("unchecked")
	private static JSONArray addRepeatedNodeToJson(Element container,Repeat f) throws JSONException {
		JSONArray node = new JSONArray();

		JSONArray elementlist=extractRepeatData(container,f);

		JSONObject siblingitem = new JSONObject();
		for(int i=0;i<elementlist.length();i++){
			JSONObject element = elementlist.getJSONObject(i);


			for(FieldSet fs : f.getChildren()) {
				Iterator rit=element.keys();
				while(rit.hasNext()) {
					String key=(String)rit.next();

					if(!fs.getID().equals(key)){
						continue;
					}
					Object value = element.get(key);
					JSONArray arrvalue = (JSONArray)value;

					if(fs instanceof Field) {
						for(int j=0;j<arrvalue.length();j++){
							JSONObject repeatitem = new JSONObject();
							//XXX remove when service layer supports primary tags
							if(f.hasPrimary() && j==0){
								repeatitem.put("_primary",true);
							}
							Element child = (Element)arrvalue.get(j);
							addExtraToJson(repeatitem,child, (Field)fs);
							if(f.asSibling()){
								siblingitem.put(fs.getID(), child.getText());
							}
							else{
								repeatitem.put(fs.getID(), child.getText());
								node.put(repeatitem);
							}
						}
					}
					else if(fs instanceof Repeat){
						for(int j=0;j<arrvalue.length();j++){
							Object repeatcontainer = arrvalue.get(j);
							Element rpcontainer=(Element)repeatcontainer;
							JSONObject repeatitem = new JSONObject();
							JSONArray repeatdata = addRepeatedNodeToJson(rpcontainer,(Repeat)fs);
							
							if(f.asSibling()){
								siblingitem.put(fs.getID(), repeatdata);
							}
							else{
								repeatitem.put(fs.getID(), repeatdata);
								node.put(repeatitem);
							}
						}
					}
				}
			}
		}
		 
		if(f.asSibling()){
			node.put(siblingitem);
		}
		return node;
	}
	
	private static void addGroupToJson(JSONObject out, Element root, Group f) throws JSONException{
		String nodeName = f.getServicesTag();
		if(f.hasServicesParent()){
			nodeName = f.getfullID();
			//XXX hack because of weird repeats in accountroles permroles etc
			if(f.getServicesParent().length==0){
				nodeName = f.getID();
			}
		}
		List nodes=root.selectNodes(nodeName);
		if(nodes.size()==0)
			return;
		
		
		JSONArray node = new JSONArray();
		// Only first element is important in group container
		int pos = 0;
		for(Object repeatcontainer : nodes){
			pos++;
			Element container=(Element)repeatcontainer;
			JSONArray repeatitem = addRepeatedNodeToJson(container,f);
			JSONObject repeated = repeatitem.getJSONObject(0);
			out.put(f.getID(), repeated);
		}
		
	}
	@SuppressWarnings("unchecked")
	private static void addRepeatToJson(JSONObject out,Element root,Repeat f) throws JSONException {
		if(f.getXxxServicesNoRepeat()) {
			FieldSet[] fields=f.getChildren();
			if(fields.length==0)
				return;
			JSONArray members=new JSONArray();
			JSONObject data=new JSONObject();
			addFieldSetToJson(data,root,fields[0]);
			members.put(data);
			out.put(f.getID(),members);
			return;
		}
		String nodeName = f.getServicesTag();
		if(f.hasServicesParent()){
			nodeName = f.getfullID();
			//XXX hack because of weird repeats in accountroles permroles etc
			if(f.getServicesParent().length==0){
				nodeName = f.getID();
			}
		}
		List nodes=root.selectNodes(nodeName);
		if(nodes.size()==0)
			return;
		
		
		JSONArray node = new JSONArray();
		// Only first element is important in container
		//except when we have repeating items
		int pos = 0;
		for(Object repeatcontainer : nodes){
			pos++;
			Element container=(Element)repeatcontainer;
			if(f.asSibling()){
				JSONArray repeatitem = addRepeatedNodeToJson(container,f);
				JSONArray temp = new JSONArray();
				if(!out.has(f.getID())){
					out.put(f.getID(), temp);
				}
				for(int arraysize=0 ;arraysize< repeatitem.length(); arraysize++){
					JSONObject repeated = repeatitem.getJSONObject(arraysize);

					if(f.hasPrimary() && pos==1){
						repeated.put("_primary",true);
					}
					out.getJSONArray(f.getID()).put(repeated);
				}
			}
			else{
				JSONArray repeatitem = addRepeatedNodeToJson(container,f);
				out.put(f.getID(), repeatitem);
			}
		}
	}
	
	private static void addFieldSetToJson(JSONObject out,Element root,FieldSet fs) throws JSONException {
		if(fs instanceof Field)
			addFieldToJson(out,root,(Field)fs);
		else if(fs instanceof Group)
			addGroupToJson(out,root,(Group)fs);
		else if(fs instanceof Repeat)
			addRepeatToJson(out,root,(Repeat)fs);
	}
	
	public static void convertToJson(JSONObject out,Record r,Document doc) throws JSONException {
		Element root=doc.getRootElement();
		for(FieldSet f : r.getAllServiceFields()) {
			addFieldSetToJson(out,root,f);
		}
	}

	public static JSONObject convertToJson(Record r,Document doc) throws JSONException {
		JSONObject out=new JSONObject();
		convertToJson(out,r,doc);
		return out;
	}
}
