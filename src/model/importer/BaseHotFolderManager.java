package model.importer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.scanner.AssetImporter;
import org.entermediadb.asset.scanner.HotFolderManager;
import org.entermediadb.asset.util.TimeParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.WebServer;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.page.Page;
import org.openedit.page.manage.PageManager;
import org.openedit.repository.Repository;
import org.openedit.repository.filesystem.FileRepository;
import org.openedit.repository.filesystem.XmlVersionRepository;
import org.openedit.util.DateStorageUtil;
import org.openedit.util.EmStringUtils;
import org.openedit.util.Exec;
import org.openedit.util.ExecResult;
import org.openedit.util.PathUtilities;

public class BaseHotFolderManager implements HotFolderManager
{
	private static final Log log = LogFactory.getLog(BaseHotFolderManager.class);

	protected PageManager fieldPageManager;
	protected SearcherManager fieldSearcherManager;
	protected WebServer fieldWebServer;

	public WebServer getWebServer()
	{
		return fieldWebServer;
	}




	public void setWebServer(WebServer inWebServer)
	{
		fieldWebServer = inWebServer;
	}


	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}


	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}


	public PageManager getPageManager()
	{
		return fieldPageManager;
	}


	public void setPageManager(PageManager inPageManager)
	{
		fieldPageManager = inPageManager;
	}


	public void saveMounts(String inCatalogId)
	{
		//remove any old hot folders for this catalog
		getWebServer().reloadMounts();
		List configs = new ArrayList(getPageManager().getRepositoryManager().getRepositories());
		String originalpath = "/WEB-INF/data/" + inCatalogId + "/originals";

		List newrepos = new ArrayList();
		for (Iterator iterator = configs.iterator(); iterator.hasNext();)
		{
			Repository config = (Repository) iterator.next();
			if( config.getPath().startsWith(originalpath))
			{
				getPageManager().getRepositoryManager().removeRepository(config.getPath());
			}
		}
		Collection folders = loadFolders(inCatalogId);
		for (Iterator iterator = folders.iterator(); iterator.hasNext();)
		{
			Data folder = (Data) iterator.next();
			String external = folder.get("externalpath");
			if( external != null)
			{
				String toplevelfolder =  folder.get("subfolder");
				String type = folder.get("hotfoldertype");
				
				if(type == null ||"mount".equals(type))
				{
					type = "mount";
					
				}
				String fullpath = originalpath + "/" + toplevelfolder;
				//String versioncontrol = folder.get("versioncontrol");
				Repository created = createRepo(type);
				created.setPath(fullpath);
				created.setExternalPath(external);
				created.setFilterIn(folder.get("includes"));
				created.setFilterOut(folder.get("excludes"));
				//add varliables
				
				for (Iterator iterator2 = folder.getProperties().keySet().iterator(); iterator2.hasNext();) {
					
					String key = (String) iterator2.next();
					created.setProperty(key, (String) folder.getProperties().get(key)); //
				}
				
				newrepos.add(created);
			}	
		}		
		configs = getPageManager().getRepositoryManager().getRepositories();
		configs.addAll(newrepos);
		
		
		getWebServer().saveMounts(configs);
		//getPageManager().getRepositoryManager().setRepositories(configs);
		//save the file
	}
	
	protected Repository createRepo(String inType)
	{
		Repository repo;
		if("version".equals(inType) )
		{
			repo = new XmlVersionRepository();
			repo.setRepositoryType("versionRepository");
		}
		else if( "s3".equals(inType))
		{
			repo = new XmlVersionRepository();
			repo.setRepositoryType("versionRepository");
		}
		else
		{
			repo = new FileRepository();
		}
		return repo;
	}

	protected Repository findRepoByPath(List inConfigs, String inFullpath)
	{

		for (Iterator iterator = inConfigs.iterator(); iterator.hasNext();)
		{
			Repository config = (Repository) iterator.next();
			if (config.getPath().startsWith(inFullpath)) 
			{
				return config;
			}
		}
		return null;
	}


	public Collection loadFolders(String inCatalogId)
	{
		Searcher hfsearcher = getFolderSearcher(inCatalogId);
		return hfsearcher.query().all().sort("orderingDown").sort("lastscanstart").search();
		//return hfsearcher.getAllHits();
	}


	public Searcher getFolderSearcher(String inCatalogId)
	{
		return getSearcherManager().getSearcher(inCatalogId, "hotfolder");
	}

	public Data getFolderByPathEnding(String inCatalogId, String inFolder)
	{		
		for (Iterator iterator = loadFolders(inCatalogId).iterator(); iterator.hasNext();)
		{
			Data folder = (Data) iterator.next();
			String subfolder = folder.get("subfolder");
			if(inFolder.equals(subfolder) )
			{
				return folder;
			}
			
		}
		return null;
	}


	/* (non-Javadoc)
	 * @see org.entermediadb.asset.scanner.HotFolderManager2#deleteFolder(java.lang.String, org.openedit.Data)
	 */
	@Override
	public void deleteFolder(String inCatalogId, Data inExisting)
	{
		String type = inExisting.get("hotfoldertype");
		getFolderSearcher(inCatalogId).delete(inExisting, null);
		if( "syncthing".equals(type))
		{
			updateSyncThingFolders(inCatalogId);
		}
		
	}


	/* (non-Javadoc)
	 * @see org.entermediadb.asset.scanner.HotFolderManager2#saveFolder(java.lang.String, org.openedit.Data)
	 */
	@Override
	public void saveFolder(String inCatalogId, Data inNewrow)
	{
		String type = inNewrow.get("hotfoldertype");
		if("syncthing".equals(type))
		{
			String toplevelfolder = inNewrow.get("subfolder");
			Page toplevel = getPageManager().getPage("/WEB-INF/data/" + inCatalogId + "/hotfolders/" + toplevelfolder );
			inNewrow.setProperty("externalpath",toplevel.getContentItem().getAbsolutePath() );
			getFolderSearcher(inCatalogId).saveData(inNewrow, null);
			updateSyncThingFolders(inCatalogId);
		}
		else if( "googledrive".equals(type))
		{
			String toplevelfolder = inNewrow.get("subfolder");
			Page toplevel = getPageManager().getPage("/WEB-INF/data/" + inCatalogId + "/hotfolders/" + toplevelfolder );
			inNewrow.setProperty("externalpath",toplevel.getContentItem().getAbsolutePath() );
			new File(toplevel.getContentItem().getAbsolutePath()).mkdirs();
			
			getFolderSearcher(inCatalogId).saveData(inNewrow, null);
			addGoogleFolders(inCatalogId);
		}
		else 
		{
			String toplevelfolder = inNewrow.get("subfolder");
			
			//save subfolder with the value of the end of externalpath
			if( toplevelfolder == null )
			{
				String epath =  type = inNewrow.get("externalpath");
				if(epath!= null )
				{
					epath = epath.trim();
					epath = epath.replace('\\', '/');
					if( epath.endsWith("/"))
					{
						epath = epath.substring(0,epath.length() - 1);
					}
					toplevelfolder = PathUtilities.extractDirectoryName(epath + "/junk.html");
				}	
				if( toplevelfolder == null )
				{
					toplevelfolder = inNewrow.getName();
				}	
				inNewrow.setProperty("subfolder",toplevelfolder);
			}
			getFolderSearcher(inCatalogId).saveData(inNewrow, null);
		}		
				
	}	

	/* (non-Javadoc)
	 * @see org.entermediadb.asset.scanner.HotFolderManager2#importHotFolder(org.entermediadb.asset.MediaArchive, org.openedit.Data)
	 */
	@Override
	public List<String> importHotFolder(MediaArchive inArchive, Data inFolder)
	{
		inFolder = getFolderSearcher(inArchive.getCatalogId()).loadData(inFolder);
		String base = "/WEB-INF/data/" + inArchive.getCatalogId() + "/originals";
		String name = inFolder.get("subfolder");
		String path = base + "/" + name;

//		Page local = getPageManager().getPage(path + "/");
//		if( !local.exists() )
//		{
//			getPageManager().putPage(local);
//		}
		AssetImporter importer = (AssetImporter)getWebServer().getModuleManager().getBean("assetImporter");
		
		String excludes = inFolder.get("excludes");
		if( excludes != null )
		{
			List<String> list = EmStringUtils.split(excludes);
			for (int i = 0; i < list.size(); i++)
			{
				String row = list.get(i).trim();
				if( row.startsWith("/") &&  !row.startsWith(path))
				{
					row = path + row;
				}
				list.set(i, row);
			}
			importer.setExcludeMatches(list);
		}
		
		importer.setIncludeExtensions(inFolder.get("includes"));
		String attachments = inFolder.get("attachmenttrigger");
		if( attachments != null )
		{
			Collection attachmentslist = EmStringUtils.split(attachments);
			importer.setAttachmentFilters(attachmentslist);
		}
		
		Date started = new Date();
		long sincedate = 0;
		String since = inFolder.get("lastscanstart");
		if( since != null )
		{
			sincedate = DateStorageUtil.getStorageUtil().parseFromStorage(since).getTime();
		}
		boolean skipmodcheck = false;
		if( since != null )
		{
			long now = System.currentTimeMillis();
			String mod = inArchive.getCatalogSettingValue("importing_modification_interval");
			if( mod == null)
			{
				mod = "1d";
			}
			long time = new TimeParser().parse(mod);
			sincedate = sincedate + time; //once a week
			if( sincedate > now )
			{
				skipmodcheck = true;
			}
		}
		log.info(path + " scan started. skip mod check = " + skipmodcheck );
		
		List<String> paths = importer.processOn(base, path, inArchive, skipmodcheck, null);
		if( !skipmodcheck )
		{
			inFolder.setProperty("lastscanstart", DateStorageUtil.getStorageUtil().formatForStorage(started));
			getFolderSearcher(inArchive.getCatalogId()).saveData(inFolder, null);
		}
		
		long taken = ((new Date().getTime() - started.getTime())/6000L);
		log.info(inFolder + " Imported " + paths.size() + " in " + taken + " milli-seconds" );
		
		return paths;
	}
	public void addGoogleFolders(String inCatalogId)
	{
		Collection hotfolders = loadFolders(inCatalogId);
		
		for (Iterator iterator = hotfolders.iterator(); iterator.hasNext();) {
			Data folder = (Data) iterator.next();
			String type = folder.get("hotfoldertype");
			if( "googledrive".equals(type))
			{
				continue;
			}
			String key = folder.get("accesskey");
			String email = folder.get("email");
			String externalpath = folder.get("externalpath");
			
			List<String> com = Arrays.asList("add_account","-a", key,"-p",externalpath,"-e","link");
			ExecResult result = getExec().runExec("insync-headless",com,true);
			log.info("insync-headless " + com + " =" + result.getStandardOut());
		}
		//TODO: get
	}
	protected Exec fieldExec;
	protected Exec getExec()
	{
		if( fieldExec == null)
		{
			fieldExec = (Exec) getWebServer().getModuleManager().getBean("exec");
		}
		return fieldExec;
	}
	public void updateSyncThingFolders(String inCatalogId)	
	{
		Collection hotfolders = loadFolders(inCatalogId);
		
		//TODO: get login/key information from system/systemsettings
		Data server = getSearcherManager().getData("system","systemsettings","syncthing_server_address");
		if( server == null)
		{
			return;
		}
		String serverapi = getSearcherManager().getData("system","systemsettings","syncthing_server_apikey").get("value");
		String serverdeviceid = getSearcherManager().getData("system","systemsettings","syncthing_server_deviceid").get("value");
		
		String postUrl = "http://" + server.get("value") + "/rest/system/config";
		
		try
		{
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpGet httpGet = new HttpGet(postUrl);
			httpGet.addHeader("X-API-Key", serverapi);
			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			if( response1.getStatusLine().getStatusCode() != 200 )
			{
				throw new OpenEditException("SyncThing Server error " + response1.getStatusLine().getStatusCode());
			}
			String returned = EntityUtils.toString(response1.getEntity());
			JSONObject config = (JSONObject) new JSONParser().parse(returned);

			//Save it
			//TODO: Make a map of existing device ids
			Set existingdevices = new HashSet();
			List devices = (List) config.get("devices");
			
			for (Iterator iterator = devices.iterator(); iterator.hasNext();) {
				Map device = (Map) iterator.next();
				existingdevices.add(device.get("deviceID"));
			}
			
			List allfolders = (List) config.get("folders");
			List folders = new ArrayList(allfolders);
			for (Iterator iterator = folders.iterator(); iterator.hasNext();) {
				JSONObject folder = (JSONObject) iterator.next();
				String folderid = (String) folder.get("id");
				if( folderid.startsWith("EnterMediaDB/" + inCatalogId + "/") )
				{
					allfolders.remove(folder);
				}								
			}
			//TODO: Add all the folders and devices needed
			for (Iterator iterator = hotfolders.iterator(); iterator.hasNext();) {
				Data folder = (Data) iterator.next();
				String type = folder.get("hotfoldertype");
				if( type != "syncthing")
				{
					continue;
				}
				//Add self if not already in there
				String clientdeviceid = folder.get("deviceid");
				String toplevelfolder = folder.get("subfolder");
				if( !existingdevices.contains(clientdeviceid))
				{
					JSONObject newdevice = new JSONObject();
					newdevice.put("deviceID", clientdeviceid );
					newdevice.put("addresses", Arrays.asList("dynamic")  );
					newdevice.put("certName" , "");
					newdevice.put("compression" , "metadata");
					newdevice.put("introducer", false );
					newdevice.put("name", "EnterMediaDB/" + inCatalogId + "/" + clientdeviceid.substring(0,7) );
					devices.add(newdevice);
				}
				//TODO: add the folder
				JSONObject newfolder = new JSONObject();
				//dev json = new JsonBuilder()
				newfolder.put("autoNormalize", true);
				newfolder.put("copiers", 0);
	            newfolder.put("hashers", 0);
	            newfolder.put("id", "EnterMediaDB/" + inCatalogId + "/" + toplevelfolder);
	            newfolder.put("ignoreDelete", false);
	            newfolder.put("ignorePerms", false);
	            newfolder.put("invalid","");
	            newfolder.put("maxConflicts", -1);
	            newfolder.put("minDiskFreePct", 1);
	            newfolder.put("order", "random");
	            newfolder.put("path", folder.get("externalpath"));
	            newfolder.put("pullerPauseS", 0);
	            newfolder.put("pullerSleepS", 0);
	            newfolder.put("pullers", 0);
	            newfolder.put("readOnly", false);
	            newfolder.put("rescanIntervalS", 60);
	            newfolder.put("scanProgressIntervalS", 0);
	            
	            // TODO: check integrity of this messy thing
	            JSONArray outputdevices = new JSONArray();
	            JSONObject serverdevice = new JSONObject();
	            serverdevice.put("deviceID", serverdeviceid);
	            JSONObject clientdevice = new JSONObject();
	            clientdevice.put("deviceID", clientdeviceid);
	            outputdevices.add(serverdevice);
	            outputdevices.add(clientdevice);
	            JSONObject versioning = new JSONObject();
	            versioning.put("params", new JSONObject());
	            versioning.put("type", "");
	            newfolder.put("devices", outputdevices);
				newfolder.put("versioning",  versioning);
				allfolders.add(newfolder);
			}
	
			HttpPost post = new HttpPost(postUrl);
			post.setHeader("X-API-Key", serverapi);
			post.setHeader("Content-type", "application/json");
			String json = config.toJSONString();
			StringEntity  postingString = new StringEntity(json);//convert your pojo to   json
			post.setEntity(postingString);
			
			HttpResponse  response = httpclient.execute(post);
			if( response.getStatusLine().getStatusCode() != 200 )
			{
				log.info(json);
				throw new OpenEditException("SyncThing Server post error " + response.getStatusLine().getStatusCode());
			}
			
		}
		catch( Throwable ex)
		{
			log.error(ex);
			throw new OpenEditException(ex);
		}
		
	
	}			

}
