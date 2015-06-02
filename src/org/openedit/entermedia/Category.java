/*
 * Created on Mar 2, 2004
 */
package org.openedit.entermedia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.openedit.Data;
import org.openedit.data.SaveableData;

/**
 * @author cburkey
 * 
 */
public class Category implements Data, SaveableData, Comparable<Category>
{
	protected String fieldName;
	protected String fieldId;
	protected String fieldDescription;
	protected String fieldShortDecription;
	protected int fieldItemCount;
	protected List fieldChildren;
	protected Category fieldParentCategory;
	//protected String fieldParentId;
	protected Map fieldProperties;
	protected List fieldRelatedCategoryIds;
	protected String fieldLinkedToCategoryId;
	
	public Category()
	{
	}
	
	public void sortChildren(boolean inRecursive){
		
		Collections.sort(getChildren());
		if(inRecursive)
		{
			for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) 
			{
				Category child = (Category) iterator.next();
				child.sortChildren(inRecursive);
			}
		}
	}

	public Category(String inName)
	{
		fieldName = inName;
	}

	public Category(String inId, String inName)
	{
		setId(inId);
		if (inName != null)
		{
			setName(inName.trim());
		}
	}

	public List getRelatedCategoryIds()
	{
		if (fieldRelatedCategoryIds == null)
		{
			fieldRelatedCategoryIds = new ArrayList();
		}
		return fieldRelatedCategoryIds;
	}

	public void setRelatedCategoryIds(List fieldRelatedCategoryIds)
	{
		this.fieldRelatedCategoryIds = fieldRelatedCategoryIds;
	}

	/*
	 * This is old, we now use Lucene to find the items public Collection
	 * getItems() { return getItemMap().values(); }
	 * 
	 * public Map getItemMap() { if ( fieldItemMap == null ) { fieldItemMap =
	 * new HashMap(); } return fieldItemMap; }
	 * 
	 * public int getNumItems() { return getItemMap().size(); }
	 * 
	 * public void addItem( Asset inItem ) { getItemMap().put( inItem.getId(),
	 * inItem ); }
	 * 
	 * public void removeItem( Asset inItem ) { getItemMap().remove( inItem ); }
	 * 
	 * public Asset getItem( String inSkuNumber ) { return (Asset)
	 * getItemMap().get( inSkuNumber ); }
	 */
	public String getName()
	{
		return fieldName;
	}

	public void setName(String inString)
	{
		fieldName = inString;
	}

	public String getId()
	{
		return fieldId;
	}

	public void setId(String inId)
	{
		fieldId = inId;
	}

	public String toString()
	{
		return getName();
	}

	public int getItemCount()
	{
		return fieldItemCount;
	}

	public void setItemCount(int inItemCount)
	{
		fieldItemCount = inItemCount;
	}

	public boolean isContainsItems()
	{
		return getItemCount() > 0;
	}

	/**
	 * @return Returns the children.
	 */
	public List getChildren()
	{
		if (fieldChildren == null)
		{
			fieldChildren = new ArrayList();
		}
		return fieldChildren;
	}

	/**
	 * @param children
	 *            The children to set.
	 */
	public void setChildren(List inChildren)
	{
		fieldChildren = inChildren;
		for (Iterator iter = inChildren.iterator(); iter.hasNext();)
		{
			Category cat = (Category) iter.next();
			cat.setParentCategory(this);
		}
	}

	public Category addChild(Category inNewChild)
	{
		inNewChild.setParentCategory(this);
		// I removed this to speed things up
		// for (int i = 0; i < getChildren().size(); i++)
		// {
		// Category element = (Category) getChildren().get(i);
		// if ( element.getId().equals(inNewChild.getId()))
		// {
		// getChildren().set(i, inNewChild);
		// return inNewChild;
		// }
		// }
		getChildren().add(inNewChild);
		return inNewChild;
	}

	public Category getChild(String inId)
	{
		for (Iterator iter = getChildren().iterator(); iter.hasNext();)
		{
			Category element = (Category) iter.next();
			if (element.getId().equals(inId))
			{
				return element;
			}
		}
		return null;
	}

	public void removeChild(Category inChild)
	{
		Category child = getChild(inChild.getId());
		if (child != null )
		{
			getChildren().remove(child);
			child.setParentCategory(null);
			if( child != inChild)
			{
				//tell the old parent to remove itself?
				
			}
			
		}

		inChild.setParentCategory(null);
	}

	public boolean hasParent(String inId)
	{
		Category parent = this;
		while (parent != null)
		{
			if (parent.getId().equals(inId))
			{
				return true;
			}
			parent = parent.getParentCategory();
		}
		return false;
	}

	/**
	 * @return
	 */
	public boolean hasChildren()
	{
		boolean has =  fieldChildren != null && fieldChildren.size() > 0;
		return has;
	}

	public boolean hasCatalog(String inId)
	{
		if (getId().equals(inId))
		{
			return true;
		}
		if (hasChildren())
		{
			for (Iterator iter = getChildren().iterator(); iter.hasNext();)
			{
				Category child = (Category) iter.next();
				if (child.hasCatalog(inId))
				{
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasChild(String inId)
	{
		if (hasChildren())
		{
			for (Iterator iter = getChildren().iterator(); iter.hasNext();)
			{
				Category child = (Category) iter.next();
				if (child.getId().equals(inId))
				{
					return true;
				}
			}
		}
		return false;
	}

	public boolean isAncestorOf(Category inCatalog)
	{
		for (Iterator children = getChildren().iterator(); children.hasNext();)
		{
			Category child = (Category) children.next();
			if (child == inCatalog)
			{
				return true;
			}
			else if (child.hasChildren() && child.isAncestorOf(inCatalog))
			{
				return true;
			}
		}
		return false;
	}

	public Category getParentCategory()
	{
		return fieldParentCategory;
	}

	public void setParentCategory(Category parentCatalog)
	{
		fieldParentCategory = parentCatalog;
		if (parentCatalog != null)
		{
			setParentId(parentCatalog.getId());
		}
	}

	/**
	 * Returns a list of all the ancestors of this catalog, starting at the
	 * catalog at the given level and ending at this catalog itself.
	 * 
	 * @param inStartLevel
	 *            The level at which to start listing ancestors (0 is the root,
	 *            1 is the first-level children, etc.)
	 * 
	 * @return The list of ancestors of this catalog
	 */
	public List listAncestorsAndSelf(int inStartLevel)
	{
		LinkedList result = new LinkedList();
		Category catalog = this;
		while (catalog != null)
		{
			result.addFirst(catalog);
			catalog = catalog.getParentCategory();
		}
		return result.subList(inStartLevel, result.size());
	}

	public List getChildrenInRows(int inColCount)
	{
		// Now break up the page into rows by dividing the count they wanted
		List children = getChildren();
		double rowscount = (double) children.size() / (double) inColCount;

		List rows = new ArrayList();
		for (int i = 0; i < rowscount; i++)
		{
			int start = i * inColCount;
			int end = i * inColCount + inColCount;
			List sublist = children.subList(start, Math.min(children.size(), end));
			rows.add(sublist);
		}
		return rows;
	}

	public int getLevel()
	{
		int i = 1;
		Category parent = this;
		while (parent != null)
		{
			parent = parent.getParentCategory();
			i++;
		}
		return i;
	}

	public String getDescription()
	{
		return fieldDescription;
	}

	public void setDescription(String inDescription)
	{
		fieldDescription = inDescription;
	}

	public String get(String inKey)
	{
		if (inKey.equals("id"))
		{
			return getId();
		}
		if (inKey.equals("name"))
		{
			return getName();
		}
		if (inKey.equals("parentid"))
		{
			Category parent = getParentCategory();
			if( parent != null )
			{
				return parent.getId();
			}
		}
		String val = getProperty(inKey);
		if (val != null)
		{
			return val;
		}
		Category parent = getParentCategory();
		if (parent != null)
		{
			return parent.get(inKey);
		}
		return null;
	}

	public Map getProperties()
	{
		if (fieldProperties == null)
		{
			fieldProperties = new HashMap();
		}
		return fieldProperties;
	}

	public void setProperties(Map inProperties)
	{
		fieldProperties = inProperties;
	}

	public void setProperty(String inKey, String inValue)
	{
		if (inValue != null)
		{
			if(inKey.equals("name"))
			{
				setName(inValue);
			}
			else if( "id".equals(inKey) )
			{
				setId(inValue);
			}
			else
			{
				getProperties().put(inKey, inValue);
			}
		}
		else
		{
			getProperties().remove(inKey);
		}
	}

	public void setProperty(String inKey, boolean inValue)
	{
		setProperty(inKey, String.valueOf(inValue));
	}

	public boolean isPropertyTrue(String inKey)
	{
		String val = getProperty(inKey);
		if ("true".equalsIgnoreCase(val))
		{
			return true;
		}
		return false;
	}

	public String getProperty(String inKey)
	{
		return (String) getProperties().get(inKey);
	}

	/**
	 * @param inKey
	 */
	public void removeProperty(String inKey)
	{
		getProperties().remove(inKey);
	}

	public String getShortDescription()
	{
		return fieldShortDecription;
	}

	public void setShortDescription(String inShortDecription)
	{
		fieldShortDecription = inShortDecription;
	}

	public void clearChildren()
	{
		getChildren().clear();
	}

	public Category getChildByName(String inCatName)
	{
		for (Iterator iter = getChildren().iterator(); iter.hasNext();)
		{
			Category cat = (Category) iter.next();
			if (cat.getName().equals(inCatName))
			{
				return cat;
			}
		}
		return null;
	}

	public String getLink()
	{
		String path = getProperty("path");
		if (path != null)
		{
			return path;
		}
		String root = get("categoryhome");
		if (root == null)
		{
			root = "/store/categories/";
		}
		return root + getId() + ".html";
	}

	public List getParentCategories()
	{
		List paths = new ArrayList();
		Category parent = getParentCategory();
		paths.add(this);
		while (parent != null)
		{
			paths.add(0, parent);
			parent = parent.getParentCategory();
		}
		return paths;
	}
	public List getParentCategoriesFrom(int inStartFrom)
	{
		List paths = new ArrayList();
		Category parent = getParentCategory();
		paths.add(this);
		while (parent != null)
		{
			paths.add(0, parent);
			parent = parent.getParentCategory();
		}
		//from top to end 1, 2, 3, 4 cuts 3 till 4
		if( inStartFrom <= paths.size() )
		{
			return Collections.EMPTY_LIST;
		}
		paths = paths.subList(inStartFrom, paths.size());
		
		return paths;
	}

	public void clearRelatedCategoryIds()
	{
		fieldRelatedCategoryIds = new ArrayList();

	}

	public void addRelatedCategoryId(String inId)
	{
		getRelatedCategoryIds().add(inId);

	}

	public String getLinkedToCategoryId()
	{
		return fieldLinkedToCategoryId;
	}

	public void setLinkedToCategoryId(String inLinkedToCategoryId)
	{
		fieldLinkedToCategoryId = inLinkedToCategoryId;
	}

	public String getParentId()
	{
		return get("parentid");
	}

	public void setParentId(String inParentId)
	{
		setProperty("parentid", inParentId);
	}

	public String getSourcePath()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void setSourcePath(String inSourcepath)
	{
		// TODO Auto-generated method stub
		
	}
	public int compareTo(Category c2)
	{
		if( getName() == null )
		{
			if( c2.getName() == null)
			{
				return 0;
			}
			return -1;
		}
		else if( c2.getName() == null)
		{
			return 1;
		}
		return getName().toLowerCase().compareTo(c2.getName().toLowerCase());
	}

	public boolean refresh()
	{
		boolean dirty = isPropertyTrue("dirty");
		if( dirty )
		{
			fieldChildren = null;
			setProperty("dirty", false);
			return true;
		}
		return false;
	}


	// public String getSourcePath()
	// {
	// StringBuffer out = new StringBuffer();
	// Category parent = this;
	// while( parent != null)
	// {
	// out.append(parent.getName());
	// }
	//		
	// return null;
	// }
	public void setValues(String inKey, Collection<String> inValues)
	{
		StringBuffer values = new StringBuffer();
		for (Iterator iterator = inValues.iterator(); iterator.hasNext();)
		{
			String detail = (String) iterator.next();
			values.append(detail);
			if( iterator.hasNext())
			{
				values.append(" | ");
			}
		}
		setProperty(inKey,values.toString());
	}
}
