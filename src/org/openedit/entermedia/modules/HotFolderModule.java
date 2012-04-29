package org.openedit.entermedia.modules;

import java.util.Collection;

import org.openedit.Data;
import org.openedit.data.Searcher;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.scanner.HotFolderManager;

import com.openedit.WebPageRequest;

public class HotFolderModule extends BaseMediaModule
{
	public void loadHotFolders(WebPageRequest inReq)  throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);		
		Collection folders = getHotFolderManager().loadFolders(archive.getCatalogId());
		inReq.putPageValue("folders", folders);
	}
	
	//TODO:Move to a super class
	public Data loadHotFolder(WebPageRequest inReq)  throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		String id = inReq.getRequestParameter("id");
		if( "new".equals(id ) )
		{
			return null;
		}
		Searcher searcher = getHotFolderManager().getFolderSearcher(archive.getCatalogId());
		Data data = (Data)searcher.searchById(id);
		return data;
	}	
	public void saveHotFolders(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		
		getHotFolderManager().saveMounts(archive.getCatalogId());
		
	}
	public void removeHotFolder(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		String id = inReq.getRequestParameter("id");
		Searcher searcher = getHotFolderManager().getFolderSearcher(archive.getCatalogId());
		Data data = (Data)searcher.searchById(id);
		searcher.delete(data, inReq.getUser());
		getHotFolderManager().saveMounts(archive.getCatalogId());
		
	}
	public void saveFolder(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);

		String[] fields = inReq.getRequestParameters("field");
		Searcher searcher = getHotFolderManager().getFolderSearcher(archive.getCatalogId());
		String id = inReq.getRequestParameter("id");
		Data data = null;
		if( id.equals("new") )
		{
			data = searcher.createNewData();
		}
		else
		{
			data = (Data)searcher.searchById(id);
		}
		searcher.updateData(inReq, fields, data);			
		
		getHotFolderManager().saveFolder(archive.getCatalogId(),data);
		
	}
	protected HotFolderManager getHotFolderManager()
	{
		return (HotFolderManager)getModuleManager().getBean("hotFolderManager");
	}

	
}
