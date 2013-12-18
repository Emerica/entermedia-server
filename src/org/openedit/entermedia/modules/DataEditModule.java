package org.openedit.entermedia.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.entermedia.upload.FileUpload;
import org.entermedia.upload.FileUploadItem;
import org.entermedia.upload.UploadRequest;
import org.openedit.Data;
import org.openedit.data.CompositeData;
import org.openedit.data.CompositeFilteredTracker;
import org.openedit.data.FilteredTracker;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
import org.openedit.data.PropertyDetailsArchive;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.entermedia.BaseCompositeData;
import org.openedit.event.WebEvent;
import org.openedit.event.WebEventListener;
import org.openedit.xml.XmlArchive;
import org.openedit.xml.XmlFile;
import org.openedit.xml.XmlSearcher;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.SearchQuery;
import com.openedit.hittracker.Term;
import com.openedit.page.Page;
import com.openedit.users.User;
import com.openedit.util.PathUtilities;

public class DataEditModule extends BaseMediaModule
{
	protected XmlArchive fieldXmlArchive;
	protected WebEventListener fieldWebEventListener;
	private static final Log log = LogFactory.getLog(DataEditModule.class);

	public Searcher loadSearcherForEdit(WebPageRequest inReq) throws Exception
	{
		org.openedit.data.Searcher searcher = loadSearcher(inReq);
		if (searcher == null)
		{
			throw new OpenEditException("No searcher found");
		}
		String paramname = inReq.getRequestParameter("paramname");
		if( paramname == null)
		{
			paramname = "id";
		}
		String id = inReq.getRequestParameter(paramname);
		if (id != null && !id.startsWith("multi"))
		{
			Object data = searcher.searchById(id);
			if( data != null)
			{
				inReq.putPageValue("data", data);
			}
		}
		inReq.putPageValue("searcher", searcher);
		inReq.putPageValue("detailsarchive", searcher.getPropertyDetailsArchive());
		inReq.putPageValue("searcherManager", getSearcherManager());

		/* This is silly, use a view or pass in $details
		List fields = new ArrayList();
		List properties = searcher.getProperties();
		if (properties != null)
		{
			for (Iterator iterator = properties.iterator(); iterator.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) iterator.next();
				PropertyDetail copy = detail.copy();
				copy.setEditable(true);
				fields.add(copy);
			}
		}
		inReq.putPageValue("details", fields);
		*/
		return searcher;
	}

	public void searchFields(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null)
		{
			HitTracker hits = searcher.fieldSearch(inReq);
			if (hits != null)
			{
				String name = inReq.findValue("hitsname");
				inReq.putPageValue(name, hits);
				inReq.putSessionValue(hits.getSessionId(), hits);
			}
		}
		inReq.putPageValue("searcher", searcher);
		
	}	
	/**
	 * @deprecated use searchFields
	 * @param inReq
	 * @throws Exception
	 */
	public void search(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null)
		{
			HitTracker hits = searcher.fieldSearch(inReq);

			if (hits == null) //this seems unexpected. Should it be a new API such as searchAll?
			{
				hits = searcher.getAllHits(inReq);

			}
			if (hits != null)
			{
				String name = inReq.findValue("hitsname");
				inReq.putPageValue(name, hits);
				inReq.putSessionValue(hits.getSessionId(), hits);
			}
		}
		inReq.putPageValue("searcher", searcher);
	}
	
	public Data createNew(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcherForEdit(inReq);
		Data data = searcher.createNewData();
		inReq.putPageValue("data", data);
		inReq.putSessionValue("tmpdata", data);
		String var = inReq.findValue("datavariable");
		if (var != null)
		{
			inReq.putSessionValue(var, data);
		}
		return data;
	}
	
	public void makeDefaultFields(WebPageRequest inReq) throws Exception
	{
		
		String fieldName = resolveSearchType(inReq);
		Searcher searcher = loadSearcher(inReq);
		PropertyDetails details = searcher.getPropertyDetailsArchive().getPropertyDetailsCached(fieldName);
		
		String catalogid = searcher.getCatalogId();
		String file = "/" + catalogid + "/data/fields/" + fieldName + ".xml";
		searcher.getPropertyDetailsArchive().savePropertyDetails(details, fieldName, inReq.getUser(),  file);
		
	}
	
	public void makeDefaultView(WebPageRequest inReq) throws Exception
	{
		

		
		XmlFile file = (XmlFile)loadView(inReq);
		
		
		String catalogid = resolveCatalogId(inReq);
		String type = resolveSearchType(inReq);
		String viewpath = inReq.getRequestParameter("viewpath");
		String path = "/" + catalogid + "/data/views/" + viewpath + ".xml";
		file.setPath(path);
		
		
		
		getXmlArchive().saveXml(file, inReq.getUser());
		
		
		
	}
	
	
	public void makeDefaultList(WebPageRequest inReq) throws Exception
	{
		
		String fieldName = resolveSearchType(inReq);
		Searcher searcher = loadSearcher(inReq);
		if(searcher instanceof XmlSearcher){
			HitTracker hits = searcher.getAllHits();
			String catalogid = searcher.getCatalogId();
			String file = "/" + catalogid + "/data/lists/" + fieldName + ".xml";
			((XmlSearcher) searcher).saveAllData(hits, inReq.getUser(), file);
		}
	}

	public void saveProperty(WebPageRequest inReq) throws Exception
	{
		String id = inReq.getRequestParameter("id");
		String fieldName = resolveSearchType(inReq);
		Searcher searcher = loadSearcher(inReq);
		PropertyDetails details = searcher.getPropertyDetailsArchive().getPropertyDetailsCached(fieldName);
		PropertyDetail detail = details.getDetail(id);

		String newid = inReq.getRequestParameter("newid");
		detail.setId(newid);

		String name = inReq.getRequestParameter("name");
		detail.setText(name);
		String externalid = inReq.getRequestParameter("externalid");
		detail.setExternalId(externalid);
		String editable = inReq.getRequestParameter("editable");
		detail.setEditable(Boolean.parseBoolean(editable));

		String index = inReq.getRequestParameter("index");
		detail.setIndex(Boolean.parseBoolean(index));

		String stored = inReq.getRequestParameter("stored");
		detail.setStored(Boolean.parseBoolean(stored));

		String keyword = inReq.getRequestParameter("keyword");
		detail.setKeyword(Boolean.parseBoolean(keyword));

		String filter = inReq.getRequestParameter("filter");
		detail.setFilter(Boolean.parseBoolean(filter));

		String internalfield = inReq.getRequestParameter("internalfield");
		detail.setProperty("internalfield", String.valueOf(Boolean.parseBoolean(internalfield)));
		
		String type = inReq.getRequestParameter("datatype");
		detail.setDataType(type);

		type = inReq.getRequestParameter("viewtype");
		detail.setViewType(type);

//		String val = inReq.getRequestParameter("listid");
//		detail.setProperty("listid", val);
//		val = inReq.getRequestParameter("listcatalogid");
//		detail.setProperty("listcatalogid", val);
//
//		val = inReq.getRequestParameter("sort");
//		detail.setProperty("sort", val);
//		val = inReq.getRequestParameter("query");
//		detail.setProperty("query", val);
//		val = inReq.getRequestParameter("foreignkeyid");
//		detail.setProperty("foreignkeyid", val);
//		val = inReq.getRequestParameter("viewtype");
//		detail.setProperty("viewtype", val);

		
		String [] fields = inReq.getRequestParameters("field");
		if( fields != null)
		{
			for (int i = 0; i < fields.length; i++) 
			{
				
				String field = fields[i];
				String value = inReq.getRequestParameter(field + ".value");
				detail.setProperty(field, value);
			}
		}
		
		searcher.getPropertyDetailsArchive().savePropertyDetails(details, fieldName, inReq.getUser());
	}

	public void addToView(WebPageRequest inReq) throws Exception
	{
		XmlFile file = (XmlFile)loadView(inReq);
		String catalogid = resolveCatalogId(inReq);
		String type = resolveSearchType(inReq);
		String viewpath = inReq.getRequestParameter("viewpath");
		String path = "/WEB-INF/data/" + catalogid + "/views/" + viewpath + ".xml";
		file.setPath(path);
		file.setElementName("property");

		String newone = inReq.getRequestParameter("newone");

		Element element = file.addNewElement();
		element.addAttribute("id", newone);
		element.clearContent();

		getXmlArchive().saveXml(file, inReq.getUser());
		//reload the archive
		Searcher searcher = loadSearcher(inReq);
		searcher.getPropertyDetailsArchive().clearCache();
	}
//TODO: Allow disable of views
	public void removeFromView(WebPageRequest inReq) throws Exception
	{
		XmlFile file = (XmlFile)loadView(inReq);
		String catalogid = resolveCatalogId(inReq);
		String type = resolveSearchType(inReq);
		String viewpath = inReq.getRequestParameter("viewpath");
		String path = "/WEB-INF/data/" + catalogid + "/views/" + viewpath + ".xml";
		file.setPath(path);
		String toremove = inReq.getRequestParameter("toremove");

		Element element = file.getElementById(toremove);
		file.deleteElement(element);

		getXmlArchive().saveXml(file, inReq.getUser());
		Searcher searcher = loadSearcher(inReq);
		searcher.getPropertyDetailsArchive().clearCache();

	}

	public PropertyDetail loadProperty(WebPageRequest inReq) throws Exception
	{
		String id = inReq.getRequestParameter("id");
		Searcher searcher = loadSearcher(inReq);

		if (searcher == null || id == null)
		{
			return null;
		}
		PropertyDetail detail = searcher.getPropertyDetailsArchive().getPropertyDetailsCached(searcher.getSearchType()).getDetail(id);

		if (detail != null)
		{
			inReq.putPageValue("property", detail);
			inReq.putPageValue("detail", detail);
		}
		return detail;
	}

	public void addProperty(WebPageRequest inReq) throws Exception
	{
		String fieldName = resolveSearchType(inReq);
		Searcher searcher = loadSearcherForEdit(inReq);
		PropertyDetails details = searcher.getPropertyDetailsArchive().getPropertyDetailsCached(fieldName);
		PropertyDetail detail = new PropertyDetail();
		String label = inReq.getRequestParameter("newproperty");
		if (label == null)
		{
			label = "New";
		}
		detail.setId(label.toLowerCase().replace(" ", ""));
		detail.setText(label);
		String catid = inReq.findValue("catalogid");
		detail.setCatalogId(catid);
		detail.setEditable(true);
		detail.setIndex(true);
		detail.setStored(true);
		details.addDetail(detail);
		
		searcher.getPropertyDetailsArchive().savePropertyDetails(details, fieldName, inReq.getUser());
		loadProperties(inReq);
		//tuan
		inReq.putPageValue("property", detail);
		inReq.putPageValue("detail", detail);
	}

	public void deleteProperty(WebPageRequest inReq) throws Exception
	{
		String id = inReq.getRequestParameter("id");
		String fieldName = resolveSearchType(inReq);
		Searcher searcher = loadSearcher(inReq);
		PropertyDetails details = searcher.getPropertyDetailsArchive().getPropertyDetailsCached(fieldName);
		details.removeDetail(id);
		searcher.getPropertyDetailsArchive().savePropertyDetails(details, fieldName, inReq.getUser());
	}

	public void saveMultiJoinData(WebPageRequest inReq) throws Exception
	{
		String save = inReq.getRequestParameter("save");
		if (!Boolean.parseBoolean(save))
		{
			return;
		}
		String catalogid = inReq.getRequestParameter("catalogid");
		String searchtype = inReq.getRequestParameter("searchtype"); // productstate

		// String fieldname = inReq.getRequestParameter("fieldname");
		// //productstate
		String[] newvalues = inReq.getRequestParameters(searchtype + ".value");

		List newvaluestosave = new ArrayList();
		if (newvalues != null)
		{
			newvaluestosave.addAll(Arrays.asList(newvalues));
		}

		Searcher joinsearcher = getSearcherManager().getSearcher(catalogid, searchtype);

		FilteredTracker multiList = new FilteredTracker();
		multiList.setSearcher(joinsearcher);
		String listexternalid = inReq.getRequestParameter("listexternalid");
		multiList.setListId(listexternalid);
		String externalvalue = inReq.getRequestParameter("fieldexternalvalue");
		multiList.setExternalValue(externalvalue);
		String externalid = inReq.getRequestParameter("fieldexternalid");
		multiList.setExternalId(externalid);
		String sourcepath = inReq.getRequestParameter("sourcepath");
		multiList.setSourcePath(sourcepath);
		// If this product is not multiselect then do this
		if ((externalid.equals("assetid") || externalid.equals("productid")) && externalvalue.startsWith("multiedit:"))
		{
			CompositeData multi = (CompositeData) inReq.getSessionValue(externalvalue);
			for (Iterator iterator = multi.iterator(); iterator.hasNext();)
			{
				Data hit = (Data) iterator.next();
				multiList.setExternalValue(hit.getId());
				multiList.setSourcePath(hit.get("sourcepath"));
				// We only want to add new records
				List toAdd = multiList.filterToAdd(newvaluestosave, inReq.getUser());
				multiList.getSearcher().saveAllData(toAdd, inReq.getUser());
			}
			String appid = inReq.findValue("applicationid");
			String saveokpage = inReq.getPageProperty("saveokpage");
			// THis is for compatibility with EnterMedia and Data-Mining
			// projects
			if (saveokpage == null)
			{
				saveokpage = "/layout/edit/saveok.html";
			}
			inReq.redirect("/" + appid + saveokpage);
			return;
		}
		else
		{
			multiList.saveRows(newvaluestosave, inReq.getUser());
		}
		// Remove conflicting data from the counterpart list
		// I.e., if we add to the available zones list, remove from the
		// unavailable zones list.
		removeCounterpartData(inReq, newvaluestosave);
	}

	public void removeCounterpartData(WebPageRequest inReq, List values)
	{
		String catalogid = inReq.getRequestParameter("catalogid");
		String counterPartSearchtype = inReq.getRequestParameter("counterpartsearchtype");
		Searcher counterPartSearcher = getSearcherManager().getSearcher(catalogid, counterPartSearchtype);

		if (counterPartSearchtype != null)
		{
			String externalid = inReq.getRequestParameter("fieldexternalid");
			String externalvalue = inReq.getRequestParameter("fieldexternalvalue");
			String listexternalid = inReq.getRequestParameter("listexternalid");

			FilteredTracker multiList = new CompositeFilteredTracker();
			multiList.setSearcher(counterPartSearcher);
			multiList.setListId(listexternalid);

			multiList.filter(externalid, externalvalue);

			multiList.deleteValues(values, inReq.getUser());
		}
	}

	public void saveData(WebPageRequest inReq) throws Exception
	{
		int count = 0;
		String[] fields = inReq.getRequestParameters("field");
		if (fields == null)
		{
			return;
		}
		String ok = inReq.findValue("save");
		if (!Boolean.parseBoolean(ok))
		{
			return;
		}

		Searcher searcher = loadSearcherForEdit(inReq);
		if (searcher != null)
		{
			Data data = null; // existing record?
			String id = inReq.getRequestParameter("id"); // Old ID
			if (id == null)
			{
				id = inReq.getRequestParameter("id.value");
			}
			if (id != null && id.startsWith("multiedit:"))
			{
				
				data = (CompositeData) inReq.getSessionValue(id);
				if( data == null)
				{
					data = loadData(inReq);
				}
			}
			if (id != null && data == null)
			{
				data = (Data) searcher.searchById(id);
			}
			if (data == null)
			{
				data = (Data) inReq.getSessionValue("tmpdata");
				inReq.removeSessionValue("tmpdata");
				if(data != null &&data.getId() == null){
					data.setId(id);
				}
			}
			// We need to do this for multiediting so that we can get better record edit logs.
			if (data instanceof CompositeData)
			{
				String val = null;
				ArrayList<String> fieldswithvalues = new ArrayList<String>();
				for(int i=0;i<fields.length;i++)
				{
					val = inReq.getRequestParameter(fields[i]+".value");
					if(val!= null && val.length() > 0)
					{
						fieldswithvalues.add(fields[i]);
					}
					String[] vals = inReq.getRequestParameters(fields[i]+".values");
					if(vals != null && vals.length > 0)
					{
						fieldswithvalues.add(fields[i]);
					}
				}
				
				CompositeData compositedata = (CompositeData) data;

				String[] newfields = new String[fieldswithvalues.size()];
				newfields = fieldswithvalues.toArray(newfields);
				searcher.updateData(inReq, newfields, compositedata);
//				for (Iterator iterator = compositedata.iterator(); iterator.hasNext();)
//				{
//					Data copy = (Data) iterator.next();
//					inReq.setRequestParameter("id", copy.getId());
//					searcher.saveDetails(inReq, newfields, copy, copy.getId());
//				}
				count = compositedata.size();
				compositedata.saveChanges();
				// should we redirect to a save ok page?
				redirectToSaveOk(inReq);
			}
			else
			{
				boolean multivalues = false;

				/* This is being used for table data */
				String externalid = inReq.getRequestParameter("fieldexternalid");
				if (externalid != null)
				{
					String externalvalue = inReq.getRequestParameter("fieldexternalvalue");
					if (externalvalue.startsWith("multiedit:"))
					{
						multivalues = true;
						CompositeData externaldata = (CompositeData) inReq.getSessionValue(externalvalue);
						// Each one of the ids of the composite data elements become the values of the external field
						for (Iterator iterator = externaldata.iterator(); iterator.hasNext();)
						{
							Data element = (Data)iterator.next();
							data = searcher.createNewData();
							data.setSourcePath(element.getSourcePath());
							inReq.setRequestParameter("id", data.getId());
							inReq.setRequestParameter(externalid + ".value", element.getId());
							searcher.saveDetails(inReq, fields, data, id);
							count++;
						}
						redirectToSaveOk(inReq);
					}
				}

				if (!multivalues)
				{
					String newid = inReq.getRequestParameter("newid");
					if (newid != null && !newid.equals(id)) // Make a copy
					{
						data = searcher.createNewData();
						data.setId(newid);
					}
					if (data == null)
					{
						data = searcher.createNewData();
						data.setId(id);
					}
					// should the data have a sourcepath set?
					if (data.getSourcePath() == null)
					{
						String sourcepath = inReq.getRequestParameter("sourcepath");
						data.setSourcePath(sourcepath);
					}
					inReq.setRequestParameter("id", data.getId());
					inReq.setRequestParameter("id.value", data.getId());
					searcher.saveDetails(inReq, fields, data, id);
					count++;
					
				}
			}
			inReq.putPageValue("data", data);

			inReq.putPageValue("savedok", Boolean.TRUE);
			
			
			if(getWebEventListener() != null)
			{
				WebEvent event = new WebEvent();
				event.setSearchType(searcher.getSearchType());
				event.setCatalogId(searcher.getCatalogId());
				event.setOperation(searcher.getSearchType() + "/saved");
				event.setProperty("dataid", data.getId());
				event.setProperty("id", data.getId());

				event.setProperty("applicationid", inReq.findValue("applicationid"));

				getWebEventListener().eventFired(event);
			}
			inReq.putPageValue("rowsedited", String.valueOf(count));
			//rowsedited="$!rowsedited}
			//<script>/${catalogid}/events/scripts/library/saved.groovy</script>
		}
	}

	public WebEventListener getWebEventListener() {
		return fieldWebEventListener;
	}


	public void setWebEventListener(WebEventListener inWebEventListener) {
		fieldWebEventListener = inWebEventListener;
	}


	//TODO: Remove this, it is not secure and can be done other ways
	protected void redirectToSaveOk(WebPageRequest inReq)
	{
		String saveokpage = inReq.getPageProperty("saveokpage");
		// this is for compatibility with Data-Mining and EnterMedia
		// projects
		if( saveokpage != null)
		{
//			if (saveokpage == null)
//			{
//				saveokpage = "/layout/edit/saveok.html";
//			}
			String appid = inReq.findValue("applicationid");
			inReq.redirect("/" + appid + saveokpage);
		}
	}
	public void deleteAll(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null && inReq.getUser() != null)
		{
			searcher.deleteAll(inReq.getUser());
		}		
	}
	public void deleteData(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null)
		{
			String[] id = inReq.getRequestParameters("id");
			int changes = 0;
			if (id != null)
			{
				for (int i = 0; i < id.length; i++)
				{
					Data data = (Data) searcher.searchById(id[i]);
					if (data != null)
					{
						searcher.delete(data, inReq.getUser());
						changes++;
					}
					if(getWebEventListener() != null)
					{
						WebEvent event = new WebEvent();
						event.setSearchType(searcher.getSearchType());
						event.setCatalogId(searcher.getCatalogId());
						event.setOperation(searcher.getSearchType() + "/deleted");
						event.setProperty("dataid", data.getId());
						event.setProperty("id", data.getId());

						event.setProperty("applicationid", inReq.findValue("applicationid"));

						getWebEventListener().eventFired(event);
					}
					
				}
			}

			
			inReq.putPageValue("rowsedited", String.valueOf(changes));
		}

	}

	public void searchAndDeleteData(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcherForEdit(inReq);
		if (searcher != null)
		{
			HitTracker hits = searcher.fieldSearch(inReq);
			if (hits != null)
			{
				for (Iterator iterator = hits.iterator(); iterator.hasNext();)
				{
					Data data = (Data) iterator.next();
					searcher.delete(data, inReq.getUser());
				}
			}
		}
	}

	public void loadPageOfSearch(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);

		if (searcher != null)
		{
			/*
			 * By the time we call the searcher's method, we expect hitsperpage
			 * to be set on the tracker.
			 */
			HitTracker  hits = searcher.loadPageOfSearch(inReq);
			inReq.putPageValue("searcher", searcher);
		}
	}



	public List loadSearchTypes(WebPageRequest inReq)
	{
		String catid = resolveCatalogId(inReq);
		PropertyDetailsArchive archive =  getSearcherManager().getPropertyDetailsArchive(catid);
		List sorted = archive.listSearchTypes();
		inReq.putPageValue("searchtypes", sorted);
		return sorted;
	}
	
	public List loadProperties(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher == null)
		{
			return null;
		}
		List all = searcher.getProperties();
		inReq.putPageValue("properties", all);
		inReq.putPageValue("details", searcher.getPropertyDetailsArchive().getPropertyDetailsCached(searcher.getSearchType()));

		return all;
	}

	public List loadFieldDetails(WebPageRequest inReq) throws Exception
	{
		PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive("system");
		List props = archive.getDataProperties("fieldproperties");
		inReq.putPageValue("properties", props);

		String id = inReq.getRequestParameter("id");
		if (id != null)
		{
			List all = loadProperties(inReq);
			for (Iterator iterator = all.iterator(); iterator.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) iterator.next();
				if (detail.getId().equals(id))
				{
					inReq.putPageValue("data", detail);
					break;
				}
			}
		}
		return props;
	}

	public List loadViewTypes(WebPageRequest inReq)
	{
		String catid = resolveCatalogId(inReq);

		PropertyDetailsArchive archive =  getSearcherManager().getPropertyDetailsArchive(catid);
		List paths = archive.listViewTypes();

		List found = new ArrayList();
		for (Iterator iterator = paths.iterator(); iterator.hasNext();)
		{
			String folder = (String) iterator.next();
			if( "CVS".equals(folder))
			{
				continue;
			}
			found.add(folder);
		}
		inReq.putPageValue("viewtypes", found);
		return found;
		
	}
	public List loadViews(WebPageRequest inReq)
	{
		String catid = resolveCatalogId(inReq);

		String type = inReq.findValue("searchtype");
		if( type== null)
		{
			return null;
		}

		PropertyDetailsArchive archive =  getSearcherManager().getPropertyDetailsArchive(catid);
		List paths = archive.listViews(type);
		
		List found = new ArrayList();
		Set fallback = new HashSet();
		for (Iterator iterator = paths.iterator(); iterator.hasNext();)
		{
			String name = (String) iterator.next();
			if( "CVS".equals(name))
			{
				continue;
			}
			Data view =	archive.getView(type, name);
			found.add(view);
		}
	
		inReq.putPageValue("views", found);
		return found;
	}

	public Data loadView(WebPageRequest inReq) throws Exception
	{
		String catid = resolveCatalogId(inReq);
		String viewid = inReq.getRequestParameter("viewpath");
		String type = resolveSearchType(inReq);

		PropertyDetailsArchive archive =  getSearcherManager().getPropertyDetailsArchive(catid);
		Data view = archive.getView(type,viewid);
		//String path = "/WEB-INF/data/" + catid + "/views/" + type + "/" + view + ".xml";
		//XmlFile file = getXmlArchive().getXml(path);

		inReq.putPageValue("view", view);
		inReq.putPageValue("viewname", viewid);

		loadProperties(inReq);
		return view;
	}

	public void addNewView(WebPageRequest inReq) throws Exception
	{
		String catid = resolveCatalogId(inReq);
		String name = inReq.getRequestParameter("newname");
		//String type = resolveSearchType(inReq);
		
		Searcher searcher = getSearcherManager().getSearcher(catid, "view");
		Data data = searcher.createNewData();
		String module = inReq.findValue("module");

		String id = PathUtilities.makeId(name);
		id = id.toLowerCase();
		if( module != null )
		{
			id = module + id; //To mak sure they are unique
		}
		data.setId(id);
		data.setName(name);
		
		data.setProperty("module", module);
		data.setProperty("systemdefined", "false" );
		data.setProperty("ordering", System.currentTimeMillis() + "" );
		
		searcher.saveData(data, inReq.getUser());
		searcher.getPropertyDetailsArchive().clearCache();
//		String path = "/WEB-INF/data/" + catid + "/views/" + type + "/" + name + ".xml";
//		XmlFile file = getXmlArchive().getXml(path, "property");
//
//		getXmlArchive().saveXml(file, inReq.getUser());
	}

	public void deleteView(WebPageRequest inReq)
	{
		String catid = resolveCatalogId(inReq);
		
		String id = inReq.getRequestParameter("id");
		Searcher searcher = getSearcherManager().getSearcher(catid, "view");
		Data data = (Data)searcher.searchById(id);
		searcher.delete(data, null);
		
		String view = inReq.getRequestParameter("viewpath");
		//String type = resolveSearchType(inReq);
		String path = "/WEB-INF/data/" + catid + "/views/" + view + ".xml";
		Page viewPage = getPageManager().getPage(path);
		if (viewPage.exists())
		{
			getPageManager().removePage(viewPage);
		}
		searcher.getPropertyDetailsArchive().clearCache();

	}
/**
 * @deprecated Not useful
 * @param inReq
 */
	public void changeViewName(WebPageRequest inReq)
	{
		String catid = resolveCatalogId(inReq);
		String viewpath = inReq.getRequestParameter("viewpath");
		String newname = inReq.getRequestParameter("newname");
		String type = resolveSearchType(inReq);
		String path = "/WEB-INF/data/" + catid + "/views/" + viewpath + ".xml";

		//This does not seem right. Do we really need this code
		String newpath = "/WEB-INF/data/" + catid + "/views/" + newname + ".xml";

		Page oldpage = getPageManager().getPage(path);
		Page newpage = getPageManager().getPage(newpath);
		getPageManager().movePage(oldpage, newpage);

		inReq.setRequestParameter("view", newname);
	}

	public XmlArchive getXmlArchive()
	{
		return fieldXmlArchive;
	}

	public void setXmlArchive(XmlArchive inXmlArchive)
	{
		fieldXmlArchive = inXmlArchive;
	}

	public void updateDataIndex(WebPageRequest inReq) throws Exception
	{
		String id = inReq.getRequestParameter("id"); // Old ID
		String searchtype = inReq.findValue("searchtype");
		String catalogId = resolveCatalogId(inReq);
		User user = inReq.getUser();
		updateIndex(id, searchtype, catalogId, user);
	}

	public void updateIndex(String id, String searchtype, String catalogId, User user)
	{
		org.openedit.data.Searcher searcher = getSearcherManager().getSearcher(catalogId, searchtype);
		if (searcher != null)
		{
			Data data = null; // existing record?
			if (id != null)
			{
				data = (Data) searcher.searchById(id);
				searcher.saveData(data, user);
			}

		}
	}

	public void toggleHitSelection(WebPageRequest inReq) throws Exception
	{
		String name = inReq.getRequestParameter("hitssessionid");
		HitTracker hits = (HitTracker) inReq.getSessionValue(name);
		if( hits != null)
		{
			String[] params = inReq.getRequestParameters("dataid");
			if( params != null)
			{
				for (int i = 0; i < params.length; i++)
				{
					String id = params[i];
					hits.toggleSelected(id);
				}
			}
		}
	}

	public void selectHits(WebPageRequest inReq) throws Exception
	{
		// loadPageOfSearch(inReq);
		String name = inReq.getRequestParameter("hitssessionid");
		HitTracker hits = (HitTracker) inReq.getSessionValue(name);
		if( hits == null )
		{
			throw new OpenEditException("Session timed out, reload page");
		}
		String action = inReq.getRequestParameter("action");
		if ("all".equals(action))
		{
			hits.selectAll();
		}
		else if ("page".equals(action))
		{
			hits.selectCurrentPage();
		}
		else if ("none".equals(action))
		{
			hits.deselectAll();
		}

	}

	public void deselectAll(WebPageRequest inReq) throws Exception
	{
		loadPageOfSearch(inReq);
		String name = inReq.findValue("hitsname");
		HitTracker hits = (HitTracker) inReq.getPageValue(name);
		hits.deselectAll();

	}

	public void changeSort(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null)
		{
			searcher.changeSort(inReq);
		}
	}

	public void changeFilters(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		if (searcher != null)
		{
			searcher.updateFilters(inReq);
		}
	}
	
	
	
	public void addChildQuery(WebPageRequest inReq) throws Exception
	{
		String querystring = inReq.getRequestParameter("childquery");
		if (querystring != null)
		{
			Searcher searcher = loadSearcher(inReq);
			if (searcher != null)
			{
				searcher.addChildQuery(inReq);
			}
		}
	}



	public void loadPicker(WebPageRequest inReq)
	{
		String catalogid = inReq.getRequestParameter("catalogid");
		Term term = (Term) inReq.getPageValue("term");
		SearchQuery query = (SearchQuery) inReq.getPageValue("query");

		PropertyDetail detail = (PropertyDetail) inReq.getPageValue("detail");
		if (detail == null)
		{
			if (term == null && query != null)
			{
				String termid = inReq.getRequestParameter("termid");
				term = query.getTermByTermId(termid);
			}
			if (term != null)
			{
				detail = term.getDetail();
			}
			else
			{
				return;
			}
		}
		String type = detail.getSearchType();

		// Dependencies
		String view = detail.getView();
		List depends = new ArrayList();
		Map searchers = new HashMap();
		Map values = new HashMap();
		StringBuffer additionals = new StringBuffer();
		for (String foreignkeyid = detail.get("foreignkeyid"); foreignkeyid != null;)
		{
			PropertyDetail next = getSearcherManager().getSearcher(detail.getListCatalogId(), type).getDetail(foreignkeyid);
			depends.add(next);
			searchers.put(foreignkeyid, getSearcherManager().getListSearcher(next));
			String value = inReq.getRequestParameter(foreignkeyid);
			if (value == null)
			{
				value = query.getInput(term.getId() + "." + foreignkeyid);
			}
			values.put(foreignkeyid, value);
			additionals.append(foreignkeyid);
			additionals.append(",");
			foreignkeyid = next.get("foreignkeyid");
		}
		if (additionals.length() > 0)
		{
			additionals.deleteCharAt(additionals.length() - 1); // last comma
		}
		Collections.reverse(depends);

		inReq.putPageValue("catalogid", catalogid);
		inReq.putPageValue("termid", term.getId());
		inReq.putPageValue("applicationid", inReq.findValue("applicationid"));
		inReq.putPageValue("detail", detail);
		inReq.putPageValue("depends", depends);
		inReq.putPageValue("searchers", searchers);
		inReq.putPageValue("values", values);
		inReq.putPageValue("additionals", additionals.toString());
		inReq.putPageValue("searcher", getSearcherManager().getListSearcher(detail));
	}

	public HitTracker loadHits(WebPageRequest inReq) throws Exception
	{
		HitTracker hits = null;
		String searchType = inReq.getRequestParameter("searchtype");
		if(searchType == null){
			searchType = inReq.findValue("searchtype");
		}
		String hitsname = inReq.findValue("hitsname");
		if (hitsname == null)
		{
			hitsname = "hits";
		}

		Searcher searcher = null;

		String catalogid = inReq.getRequestParameter("catalogid");
		if(catalogid == null){
			catalogid = inReq.findValue("catalogid");
		}
		if (catalogid == null)
		{
			catalogid = inReq.findValue("applicationid");
		}
		if (catalogid != null)
		{// for a sub searcher
			searcher = getSearcherManager().getSearcher(catalogid, searchType);
			hits = searcher.loadHits(inReq);
		}

		if (hits == null)
		{
			if(searcher != null){
				hits = searcher.loadHits(inReq, hitsname);
			}
		}
		if (hits == null)
		{
			if(searcher != null){
				hits = searcher.loadHits(inReq);
			}
		}
		inReq.putPageValue(hitsname + catalogid, hits);
		inReq.putPageValue(hitsname, hits);
		String clear = inReq.getRequestParameter("clearselection");
		if( Boolean.parseBoolean(clear))
		{
			hits.deselectAll();
		}
		return hits;
	}

	public void saveTextForView(WebPageRequest inReq) throws Exception
	{
		XmlFile file = (XmlFile)loadView(inReq);

		String label = inReq.getRequestParameter("usagelabel");
		file.setProperty("usagelabel", label);
		inReq.putPageValue("message", "saved");

		getXmlArchive().saveXml(file, inReq.getUser());
	}

	public void saveSorts(WebPageRequest inReq) throws Exception
	{

		Searcher searcher = loadSearcherForEdit(inReq);
		if (searcher != null)
		{
			String sortfield = inReq.findValue("sortfield");
			String[] ids = inReq.getRequestParameters("id");
			;
			for (int i = 0; i < ids.length; i++)
			{
				String id = ids[i];

				Data data = (Data) searcher.searchById(id);
				if (data == null)
				{
					log.info("no data found");
					continue;
				}
				data.setProperty(sortfield, String.valueOf(i));

				searcher.saveData(data, inReq.getUser());
			}

		}

	}

	public void uploadFiles(WebPageRequest inReq) throws Exception
	{
		FileUpload command = new FileUpload();
		command.setPageManager(getPageManager());
		UploadRequest properties = command.parseArguments(inReq);
		if (properties == null)
		{
			return;
		}
		if (properties.getFirstItem() == null)
		{
			return;
		}

		for (Iterator iterator = properties.getUploadItems().iterator(); iterator.hasNext();)
		{
			FileUploadItem item = (FileUploadItem) iterator.next();

			String name = item.getFieldName();
			String detailid = name.substring(name.lastIndexOf('.') + 1, name.length());
			String catalogid = inReq.getRequestParameter("catalogid");
			String searchtype = inReq.findValue("searchtype");
			String datafolder = inReq.findValue("datafolder");
			if (datafolder == null)
			{
				datafolder = "/" + catalogid + "data";
			}
			String dataid = inReq.findValue("id");
			if (dataid == null)
			{
				dataid = inReq.findValue("dataid");
			}
			Searcher searcher = getSearcherManager().getSearcher(catalogid, searchtype);
			PropertyDetail detail = searcher.getDetail(detailid);
			String folder = datafolder + "/" + detail.get("filepath") + "/" + dataid + "/";

			// <property id="headerimage" filepath="images" filetype="jpg"
			// resize="true" width="120" editable="true" viewtype="image" >Main
			// Image:</property>

			String ext = item.getName();
			String tempfile = folder + ext;
			String finalpath = folder + "/" + detail.getId() + "." + detail.get("filetype");
			properties.saveFileAs(item, tempfile, inReq.getUser());

		}

		// inIn.delete();

	}

	public void updateTempData(WebPageRequest inReq) throws Exception
	{
		String dataId = inReq.findValue("datavariable");
		Data values = (Data) inReq.getSessionValue(dataId);
		if (values == null)
		{
			values = createNew(inReq);
		}
		Searcher searcher = loadSearcher(inReq);
		String[] fields = inReq.getRequestParameters("field");
		searcher.updateData(inReq, fields, values);
		boolean ok = Boolean.parseBoolean(inReq.getRequestParameter("save"));
		if (ok)
		{
			searcher.saveData(values, inReq.getUser());
			inReq.removeSessionValue(dataId);
		}
		inReq.putPageValue("data", values);

	}

	public Data loadData(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		String variablename = inReq.findValue("pageval");
		if (variablename == null)
		{
			variablename = "data";
		}
		String id = inReq.getRequestParameter("id");
		if (id == null)			
		{
			String pagename = inReq.getPage().getName();
			id = pagename.substring(0, pagename.indexOf("."));

		}
		
		Data result = null;
		
		if( id.startsWith("multiedit:"))
		{
			//setup the session value
			//BaseCompositeData
				Data data = (CompositeData) inReq.getSessionValue(id);
				if( data == null )
				{
					String hitssessionid = id.substring("multiedit".length()  +1 );
					HitTracker hits = (HitTracker) inReq.getSessionValue(hitssessionid);
					if( hits == null)
					{
						log.error("Could not find " + hitssessionid);
						return null;
					}
					CompositeData composite = new BaseCompositeData(searcher,hits);
					composite.setId(id);
					result = composite;
				}
		}
		if( result == null)
		{
			result = (Data)searcher.searchById(id);
		}
		inReq.putPageValue(variablename, result);
		return result;

	}
	
	public Data loadDataByField(WebPageRequest inReq) throws Exception{
		log.info("loadDataByField "+inReq);
		Searcher searcher = loadSearcher(inReq);
		String field = inReq.findValue("field");
		if (field == null){
			field = "id";
		}
		String pagename = inReq.getPage().getName();
		String searchValue = pagename.substring(0, pagename.lastIndexOf("."));
		SearchQuery query = searcher.createSearchQuery();
		query.append(field, searchValue);
		HitTracker hits = searcher.search(query);
		Data result = null;
		if (hits.size() > 0){
			result = (Data) hits.first();
		}
		inReq.putPageValue("data", result);
		log.info("found "+(result == null ? "NULL" : result.getId()));
		return result;
	}

	public Object loadDataByValue(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		String variablename = inReq.findValue("pageval");
		if (variablename == null)
		{
			variablename = "data";
		}
		String id = inReq.findValue("id");
		if (id == null)			
		{
			String pagename = inReq.getPage().getName();
			id = pagename.substring(0, pagename.indexOf("."));

		}
		Object result = searcher.searchById(id);
		inReq.putPageValue(variablename, result);
		return result;

	}
	
	public void reindex(WebPageRequest inReq) throws Exception{
		Searcher searcher = loadSearcher(inReq);
		if(searcher != null){
			searcher.reIndexAll();
		}
	}
	
	public void deleteSelections(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		String name = inReq.getRequestParameter("hitssessionid");
		HitTracker hits = (HitTracker) inReq.getSessionValue(name);

		Collection todelete = hits.getSelectedHitracker();
		for (Iterator iterator = todelete.iterator(); iterator.hasNext();)
		{
			Data hit = (Data) iterator.next();
			searcher.delete(hit, inReq.getUser());
		}

	}

	public void moveFieldInView(WebPageRequest inReq) throws Exception
	{
		XmlFile file = (XmlFile)loadView(inReq);
		String catalogid = resolveCatalogId(inReq);
		String type = resolveSearchType(inReq);
		String viewpath = inReq.getRequestParameter("viewpath");
		String path = "/WEB-INF/data/" + catalogid + "/views/" + viewpath + ".xml";
		file.setPath(path);
		file.setElementName("property");

		String source = inReq.getRequestParameter("source");
		Element sourceelement = file.getElementById(source);

		String destination = inReq.getRequestParameter("destination");
		Element destinationelement = file.getElementById(destination);
		
		int sindex = file.getElements().indexOf(sourceelement);
		int dindex = file.getElements().indexOf(destinationelement);
		file.getElements().remove(sindex);
		sourceelement.setParent(null);
		if( dindex > sindex)
		{
			dindex--;
		}
		file.getElements().add(dindex,sourceelement);
		getXmlArchive().saveXml(file, inReq.getUser());
		getSearcherManager().getPropertyDetailsArchive(catalogid).clearCache();
	}
	
	
	public void sortList(WebPageRequest inReq) throws Exception
	{
		Searcher searcher = loadSearcher(inReq);
		String field = inReq.getRequestParameter("field");
		
		String [] id2 = inReq.getRequestParameters("view[]");
		ArrayList valuestosave = new ArrayList();
		for (int i = 0; i < id2.length; i++)
		{
			String dataid = id2[i];
			dataid = dataid.replace("||", "_");//This is because of how jquery UI collects the values.
			Data item = (Data)searcher.searchById(dataid);
			item.setProperty(field, String.valueOf(i));
			valuestosave.add(item);
		}
		searcher.saveAllData(valuestosave, inReq.getUser());
	}
	
	
	public SearcherManager loadSearcherManager(WebPageRequest inReq)
	{
		SearcherManager searcherManager = getSearcherManager();
		inReq.putPageValue("searcherManager",searcherManager );
		return searcherManager;
	}

	public void addValues(WebPageRequest inReq) throws Exception 
	{
		Data data = (Data)loadData(inReq);
		String inFieldName = inReq.getRequestParameter("fieldname");
		Collection existing = getValues(data,inFieldName);
		String value = inReq.getRequestParameter(inFieldName + ".value");
		if( existing == null)
		{
			existing = new ArrayList();
		}
		else
		{
			existing = new ArrayList(existing);
		}
		if( !existing.contains(value))
		{
			existing.add(value);
			setValues(data, inFieldName, existing);
			//getMediaArchive(inReq).saveAsset(data, inReq.getUser());
			Searcher searcher = loadSearcher(inReq);
			searcher.saveData(data, inReq.getUser());
		}
	}	
	public void removeValues(WebPageRequest inReq) throws Exception 
	{
		Data data = (Data)loadData(inReq);
		String inFieldName = inReq.getRequestParameter("fieldname");
		Collection existing = getValues(data,inFieldName);
		String value = inReq.getRequestParameter(inFieldName + ".value");
		if( existing == null)
		{
			existing = new ArrayList();
		}
		else
		{
			existing = new ArrayList(existing);
		}
		existing.remove(value);
		setValues(data, inFieldName, existing);
		Searcher searcher = loadSearcher(inReq);
		searcher.saveData(data, inReq.getUser());
	}

	//TODO: Move this to Data interface
	public Collection getValues(Data inData, String inPreference)
	{
		String val = inData.get(inPreference);
		
		if (val == null)
			return null;
		
		String[] vals = val.split("\\s+");

		Collection collection = Arrays.asList(vals);
		//if null check parent
		return collection;
	}
	
	public void setValues(Data inData, String inKey, Collection<String> inValues)
	{
		StringBuffer values = new StringBuffer();
		for (Iterator iterator = inValues.iterator(); iterator.hasNext();)
		{
			String detail = (String) iterator.next();
			values.append(detail);
			if( iterator.hasNext())
			{
				values.append(" ");
			}
		}
		inData.setProperty(inKey,values.toString());
	}
	
	public void loadModule(WebPageRequest inReq){
		String moduleid = inReq.findValue("module");
		if(moduleid != null){
		
			inReq.putPageValue("module", moduleid);
			
		}
	}
	
	
	public void loadCorrectViewForUser(WebPageRequest inReq ) throws Exception 
	{
		String catalogid = resolveCatalogId(inReq);
		Searcher viewsearcher = getSearcherManager().getSearcher(catalogid, "view");
		
		SearchQuery query = viewsearcher.createSearchQuery();
		
		String module = inReq.findValue("module");		
		if( module == null )
		{
			throw new OpenEditException("Module not defined");
		}
		query.addMatches("module",module);
		query.addNot("systemdefined","true");
		query.addSortBy("ordering");

		PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(catalogid);

		Data currentdata = (Data)inReq.getPageValue("data");
		if( currentdata == null )
		{
			currentdata = (Data)inReq.getPageValue("asset");
		}
		Map views = new ListOrderedMap();
		for (Iterator iterator = viewsearcher.search(query).iterator(); iterator.hasNext();)
		{
			Data view = (Data) iterator.next();
			Object permissionvalue = inReq.getPageValue("can" + view.getId() );
			if( permissionvalue == null || Boolean.parseBoolean(String.valueOf(permissionvalue)) )
			{
				String type = null;
				if( currentdata != null )
				{
					type = currentdata.get("assettype");
				}
				String path = null;
				
				if( Boolean.parseBoolean( view.get("byassettype") ) )
				{
					path = module + "/assettype/" + type + "/" + view.getId();
					if(type == null || !archive.viewExists( path ) )
					{
						path =	module + "/assettype/default/" + view.getId();
					}
				}
				else
				{
					path =	module + "/" + view.getId();
				}
				views.put(path,view);
			}
		}
		inReq.putPageValue("views", views);
	}
	
	public void saveHtmlEditorContent(WebPageRequest inReq) throws Exception{
		String content = inReq.getRequestParameter("content");
		
		
		if(content != null){
			String field = inReq.getRequestParameter("field");
			inReq.setRequestParameter(field + ".value", content);
			
		}
		inReq.setRequestParameter("save", "true");
		
		saveData(inReq);
		
	}
	
}
