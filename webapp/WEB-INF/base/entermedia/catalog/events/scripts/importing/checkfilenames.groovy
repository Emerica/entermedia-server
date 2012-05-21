package importing;

import com.openedit.page.Page 
import org.openedit.data.Searcher 
import org.openedit.entermedia.Asset 
import org.openedit.entermedia.MediaArchive 
import org.openedit.*;

import com.openedit.WebPageRequest;
import com.openedit.hittracker.*;
import org.openedit.entermedia.creator.*;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.CatalogConverter;
import org.openedit.entermedia.Category;
import org.openedit.entermedia.ConvertStatus;
import org.openedit.entermedia.MediaArchive;
import org.openedit.xml.XmlArchive;
import org.openedit.xml.XmlFile;

import com.openedit.util.*;
import org.openedit.repository.*;
import com.openedit.users.*;
import com.openedit.OpenEditException;
import com.openedit.page.manage.PageManager;
import org.openedit.entermedia.scanner.AssetImporter;

public void init()
{
	MediaArchive archive = context.getPageValue("mediaarchive");
	
		
		LongPathFinder finder = new LongPathFinder();
		
		finder.setPageManager(archive.getPageManager());

		String assetRoot = "/WEB-INF/data/" + archive.getCatalogId() + "/originals/";
		finder.setRootPath(assetRoot);
		finder.process();
		context.putPageValue("longpaths",finder.folders);		
		context.putPageValue("badpaths",finder.badfiles);		
}

	class LongPathFinder extends  PathProcessor
		{
			List folders = new ArrayList();
			List badfiles = new ArrayList();
			FileUtils util = new FileUtils();
			
			public void processDir(ContentItem inContent)
			{
				String path = inContent.getAbsolutePath();
				if( path.length() > 240 )
				{
					folders.add(path);
				}
			}
			public  void processFile(ContentItem inContent, User inUser) 
			{ 
				if (!util.isLegalFilename(inContent.getPath())) 
				{
					badfiles.add(inContent.getAbsolutePath() );
				}
			
			}
			
		}	

init();
