package org.openedit.entermedia.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermedia.workspace.WorkspaceManager;
import org.openedit.Data;
import org.openedit.data.Reloadable;
import org.openedit.data.Searcher;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.page.Page;
import com.openedit.page.PageProperty;
import com.openedit.page.manage.PageManager;

public class MediaAdminModule extends BaseMediaModule
{
	private static final Log log = LogFactory.getLog(MediaAdminModule.class);
	protected WorkspaceManager fieldWorkspaceManager;
	protected PageManager fieldPageManager;

	public PageManager getPageManager()
	{
		return fieldPageManager;
	}

	public void setPageManager(PageManager inPageManager)
	{
		fieldPageManager = inPageManager;
	}

	public WorkspaceManager getWorkspaceManager()
	{
		return fieldWorkspaceManager;
	}

	public void setWorkspaceManager(WorkspaceManager inWorkspaceManager)
	{
		fieldWorkspaceManager = inWorkspaceManager;
	}

	public void listThemes(WebPageRequest inReq)
	{
		String skinsPath = "/themes";
		List children = getPageManager().getChildrenPaths(skinsPath, true);
		Map skins = new HashMap();

		for (Iterator iterator = children.iterator(); iterator.hasNext();)
		{
			String path = (String) iterator.next();
			Page theme = getPageManager().getPage(path);
			if (theme.isFolder() && theme.get("themename") != null)
			{
				skins.put("/themes/" + theme.getName(), theme.get("themename"));
			}
		}
		inReq.putPageValue("themes", skins);
	}

	public void changeTheme(WebPageRequest inReq) throws Exception
	{
		String layout = inReq.getRequestParameter("theme");
		if (layout == null)
		{
			return;
		}
		String path = inReq.getRequestParameter("path");
		if (path == null)
		{
			return;
		}
		// "/" + inReq.findValue("applicationid");
		Page page = getPageManager().getPage(path); // This is the root level
													// for this album
		PageProperty skin = new PageProperty("themeprefix");
		if ("default".equals(layout))
		{
			page.getPageSettings().removeProperty("themeprefix");
		}
		else
		{
			skin.setValue(layout);
			page.getPageSettings().putProperty(skin);
		}
		getPageManager().saveSettings(page);
	}

	public void deployUploadedApp(WebPageRequest inReq) throws Exception
	{
		Page uploaded = getPageManager().getPage("/WEB-INF/temp/importapp.zip");
		String catid = inReq.getRequestParameter("appcatalogid");
		String destinationid = inReq.getRequestParameter("destinationappid");
		if (destinationid.startsWith("/"))
		{
			destinationid = destinationid.substring(1);
		}
		getWorkspaceManager().deployUploadedApp(catid, destinationid, uploaded);
	}

	public void deployApp(WebPageRequest inReq) throws Exception
	{
		String appcatalogid = inReq.getRequestParameter("appcatalogid");
		Searcher searcher = getSearcherManager().getSearcher(appcatalogid, "app");

		Data site = null;
		String id = inReq.getRequestParameter("id");
		if (id == null)
		{
			site = searcher.createNewData();
		}
		else
		{
			site = (Data) searcher.searchById(id);
		}
		String frontendid = inReq.findValue("frontendid");
		if (frontendid == null)
		{
			throw new OpenEditException("frontendid was null");
		}
		String deploypath = inReq.findValue("deploypath");
		if (!deploypath.startsWith("/"))
		{
			deploypath = "/" + deploypath;
		}
		site.setProperty("deploypath", deploypath);

		String module = inReq.findValue("module");
		site.setProperty("module", module);

		String name = inReq.findValue("sitename");
		site.setName(name);

		// site.setProperty("frontendid",frontendid);

		searcher.saveData(site, inReq.getUser());
		Data frontend = getSearcherManager().getData("system", "frontend", frontendid);
		Page copyfrompage = getPageManager().getPage(frontend.get("path"));
		// Page copyfrompage =
		// getPageManager().getPage("/WEB-INF/base/manager/components/newworkspace");

		Page topage = getPageManager().getPage(deploypath);
		if (!topage.exists())
		{
			getPageManager().copyPage(copyfrompage, topage);
		}
		topage = getPageManager().getPage(topage.getPath(), true);

		topage.getPageSettings().setProperty("catalogid", appcatalogid);

		String appid = deploypath;
		if (appid.startsWith("/"))
		{
			appid = appid.substring(1);
		}
		if (appid.endsWith("/"))
		{
			appid = appid.substring(0, appid.length() - 1);
		}
		topage.getPageSettings().setProperty("applicationid", appid);
		topage.getPageSettings().setProperty("appmodule", site.get("module"));

		getPageManager().saveSettings(topage);

	}

	public void saveRows(WebPageRequest inReq) throws Exception
	{
		String catalogid = inReq.findValue("catalogid");
		Searcher searcher = getSearcherManager().getSearcher(catalogid, "catalogsettings");

		String[] fields = inReq.getRequestParameters("field");
		for (int i = 0; i < fields.length; i++)
		{
			Data existing = (Data) searcher.searchById(fields[i]);
			if (existing == null)
			{
				// log.error("No default value" + fields[i]);
				// continue;
				existing = searcher.createNewData();
				existing.setId(fields[i]);
			}
			boolean save = false;
			String[] values = inReq.getRequestParameters(fields[i] + ".value");
			if (values != null && values.length > 0)
			{
				if (values.length == 1)
				{
					if (!values[0].equals(existing.get("value")))
					{
						save = true;
						existing.setProperty("value", values[0]);
					}
				}
				else
				{
					save = true;
					StringBuffer buffer = new StringBuffer();
					for (int j = 0; j < values.length; j++)
					{
						buffer.append(values[j]);
						if (j + 1 < values.length)
						{
							buffer.append(' ');
						}
					}
					existing.setProperty("value", buffer.toString());
				}
			}
			else
			{
				if (existing.get("value") != null)
				{
					save = true;
					existing.setProperty("value", null);
				}
			}
			if (save)
			{
				searcher.saveData(existing, inReq.getUser());
			}
		}
	}

	public void saveModule(WebPageRequest inReq) throws Exception
	{
		Data module = (Data) inReq.getPageValue("data");

		String appid = inReq.findValue("applicationid");
		String catalogid = inReq.findValue("catalogid");
		getWorkspaceManager().saveModule(catalogid, appid, module);
	}

	public void reloadSettings(WebPageRequest inReq) throws Exception
	{
		String catalogid = inReq.findValue("catalogid");
		
		Collection<Searcher> tables = getSearcherManager().listLoadedSearchers(catalogid);
		
		List types = new ArrayList();
		for (Iterator iterator = tables.iterator(); iterator.hasNext();)
		{
			Searcher searcher = (Searcher) iterator.next();
			if (searcher instanceof Reloadable)
			{
				searcher.reloadSettings();
				
				types.add(searcher.getSearchType());
			}
		}
		inReq.putPageValue("tables", types);

	}
	
}
