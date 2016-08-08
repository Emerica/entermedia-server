package org.entermediadb.elasticsearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.openedit.Data;
import org.openedit.MultiValued;
import org.openedit.data.BaseData;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
import org.openedit.data.SaveableData;
import org.openedit.data.SearchData;
import org.openedit.modules.translations.LanguageMap;

public class SearchHitData extends BaseData implements Data, MultiValued, SaveableData,SearchData {
	protected Map fieldSearchData;
	protected long fieldVersion;
	protected SearchHit fieldSearchHit;
	protected PropertyDetails fieldPropertyDetails;
	private static final Log log = LogFactory.getLog(SearchHitData.class);

	public SearchHitData(SearchHit inHit, PropertyDetails inPropertyDetails) {
		setSearchHit(inHit);
		setPropertyDetails(inPropertyDetails);
	}

	public SearchHitData() {

	}

	public SearchHit getSearchHit() {
		return fieldSearchHit;
	}

	public void setSearchHit(SearchHit inSearchHit) {
		fieldSearchHit = inSearchHit;
		setId(inSearchHit.getId());
		setVersion(inSearchHit.getVersion());

	}

	public long getVersion() {
		return fieldVersion;
	}

	public void setVersion(long inVersion) {
		fieldVersion = inVersion;
	}

	public PropertyDetails getPropertyDetails() {
		return fieldPropertyDetails;
	}

	public void setPropertyDetails(PropertyDetails inPropertyDetails) {
		fieldPropertyDetails = inPropertyDetails;
	}

	public Map getSearchData() {
		if (fieldSearchData == null && getSearchHit() != null) {
			fieldSearchData = getSearchHit().getSource();
		}
		return fieldSearchData;
	}

	public void setSearchData(Map inSearchHit) {
		fieldSearchData = inSearchHit;
	}

	@Override
	public void setProperty(String inId, String inValue) {
		// TODO Auto-generated method stub
		super.setProperty(inId, inValue);
	}

	@Override
	public Collection<String> getValues(String inPreference) {
		Object result = getValue(inPreference);
		if (result == null) {
			return null;
		}
		if (result instanceof Collection) {
			return (Collection) result;
		}
		ArrayList one = new ArrayList(1);
		one.add(result);
		return one;
	}

	@Override
	public Object getValue(String inId) {
		if (inId == null) {
			return null;
		}
		Object svalue = super.getValue(inId);
		if (svalue != null) {
			return svalue;
		}
		svalue = getFromDb(inId);

		return svalue;
	}

	protected Object getFromDb(String inId) {
		if (inId.equals(".version")) {
			if (getVersion() > -1) {
				return String.valueOf(getVersion());
			}
			return null;
		}
		//log.info(getSearchHit().getSourceAsString());
		
		String key = inId;
		Object value = null;
		PropertyDetail detail = getPropertyDetails().getDetail(inId);
		if (detail != null && detail.isMultiLanguage()) {
			key = key + "_int";
		}

		if (getSearchHit() != null) {
			SearchHitField field = getSearchHit().field(key);
			if (field != null) {
				value = field.getValue();
			}
		}
		if (value == null && getSearchData() != null) {
			value = getSearchData().get(key);
			if (value instanceof Map) {
				Map map = (Map)value;
				if(map.isEmpty()){
					value = null;
				}
			}
		}
		if (value == null) {

			if (detail != null) {
				String legacy = detail.get("legacy");
				if (legacy != null) {
					value = getValue(legacy);
				}

				if (value == null && getSearchData() != null) {
					value = getSearchData().get(inId);
				}
			}
		}

		if (value != null && detail != null && detail.isMultiLanguage()) {
			if (value instanceof Map) {
				LanguageMap map = new LanguageMap((Map) value);
				value = map;

			}
			if (value instanceof String) {
				LanguageMap map = new LanguageMap();
				map.put("en", value);
				value = map;
			}
		}

		if (detail != null && "name".equals(inId) && !detail.isMultiLanguage() && value instanceof Map) {
			LanguageMap map = new LanguageMap((Map) value);

			value = map.get("en");
		}

		return value;
	}

	public Iterator keys() {
		return getProperties().keySet().iterator();
	}

	public Map getProperties() {
		Map all = new HashMap();
		for (Iterator iterator = getSearchHit().getSource().keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			String val = get(key);
			all.put(key, val);
		}
		String version = get(".version");
		if (version != null) {
			all.put(".version", version);
		}
		if (fieldMap != null) {
			all.putAll(super.getProperties());
		}

		return all;
	}

	public String toString() {
		if (getName() != null) {
			return getName();
		} else {
			return getId();
		}
	}
}
