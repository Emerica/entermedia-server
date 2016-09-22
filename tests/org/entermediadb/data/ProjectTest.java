package org.entermediadb.data;

import java.io.InputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.entermediadb.asset.Asset;
import org.entermediadb.asset.BaseEnterMediaTest;
import org.entermediadb.asset.Category;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.projects.BaseProjectManager;
import org.junit.Test;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.WebPageRequest;
import org.openedit.data.Searcher;
import org.openedit.hittracker.HitTracker;
import org.openedit.repository.ContentItem;
import org.openedit.util.FileUtils;

public class ProjectTest extends BaseEnterMediaTest
{
	@Test
//	public void testCollectionAssets() throws Exception
//	{
//		ProjectManager manager = (ProjectManager)getFixture().getModuleManager().getBean(getMediaArchive().getCatalogId(),"projectManager");
//		
//		WebPageRequest req = getFixture().createPageRequest("/testcatalog/index.html");
//		Searcher lsearcher = getMediaArchive().getSearcher("library");
//		Data library = lsearcher.createNewData();
//		library.setId("testlibrary");
//		library.setName("Test");
//		lsearcher.saveData(library, null);
//		
//		ProfileModule module = (ProfileModule)getFixture().getModuleManager().getBean("ProfileModule");
//		
//		module.loadUserProfile(req);
//		
//		req.getUserProfile().setProperty("last_selected_library", "testlibrary");
//		
//		Searcher lcsearcher = getMediaArchive().getSearcher("librarycollection");
//		Data collection = lcsearcher.createNewData();
//		collection.setId("testcollection");
//		collection.setName("Movie");
//		collection.setProperty("library", "testlibrary");
//		lcsearcher.saveData(collection, null);
//
//
//		int beforecount = 0;
//		Collection<UserCollection> lessfiles = manager.loadCollections(req, getMediaArchive());
//		if( lessfiles != null && lessfiles.size() > 0)
//		{
//			UserCollection hit = lessfiles.iterator().next();
//			beforecount = hit.getAssetCount();
//		}
//		
//		Searcher lcasearcher = getMediaArchive().getSearcher("librarycollectionasset");
//		Data collectionasset = lcasearcher.createNewData();
//		collectionasset.setProperty("asset", "101");
//		collectionasset.setProperty("librarycollection", "testcollection");
//		lcasearcher.saveData(collectionasset, null);
//
//		Collection<UserCollection> files = manager.loadCollections(req, getMediaArchive());
//		assertNotNull( files );
//		assertEquals( files.size(), 1);
//		UserCollection hit = files.iterator().next();
//		
//		assertEquals(beforecount + 1, hit.getAssetCount());
//		
//		
//	}
	protected void oneTimeSetup() throws Exception {
		super.oneTimeSetup();
		getMediaArchive().getSearcher("asset").reIndexAll();		
	}

	public void testSnapshotAndImportCategories(){

		MediaArchive archive = getMediaArchive();
		archive.getAssetSearcher().reIndexAll();
		
		BaseProjectManager manager = (BaseProjectManager)getFixture().getModuleManager().getBean(archive.getCatalogId(), "projectManager");

//		AssetUtilities utils = getMediaArchive().getAssetImporter().getAssetUtilities();
//		Category root = getMediaArchive().getCategoryArchive().getRootCategory();
//		String folder = "/myexportfolder";
//		utils.exportCategoryTree(getMediaArchive(),root, folder);

		
		Asset existingasset = archive.getAsset("106");
		ContentItem item = archive.getOriginalFileManager().getOriginalContent(existingasset);
		InputStream input = item.getInputStream();
		try
		{
			String md5 = DigestUtils.md5Hex( input );
			existingasset.setValue("md5hex", md5);
		}
		catch ( Exception ex)
		{
			throw new OpenEditException(ex);
		}
		finally
		{
			FileUtils.safeClose(input);
		}
		archive.saveAsset(existingasset, null);
		
		Searcher lcsearcher = getMediaArchive().getSearcher("librarycollection");
		Data collection = lcsearcher.createNewData();
		collection.setId("testcollection");
		collection.setName("Movie");
		lcsearcher.saveData(collection);
		HitTracker assets = archive.getAssetSearcher().fieldSearch("id","101");
		manager.addAssetToCollection(archive, collection.getId(), assets);
		
		//Add tree
		Category cat = archive.getCategoryArchive().createCategoryTree("/my/stuff/here");
		Asset other = archive.getAsset("102");
		other.addCategory(cat);
		archive.getCategorySearcher().saveData(cat);
		archive.saveAsset(other,null);
		manager.addCategoryToCollection(null, archive, collection.getId(), cat.getParentId());		
		
		//Import a new path
		WebPageRequest req = getFixture().createPageRequest();
		manager.snapshotAndImport(req, null, archive, collection.getId(), "importfolder");
		
		//Make sure we got the same asset as 106
		Category newrootcategory = manager.getRootCategory(archive, collection.getId());

		Data found =  archive.getAssetSearcher().query().match("category", newrootcategory.getId()).match("name", "bones.jpg").searchOne();
		assertNotNull(found);

		found =  archive.getAssetSearcher().query().match("category", newrootcategory.getId()).match("id", "106").searchOne();
		assertNotNull(found);

		found =  archive.getAssetSearcher().query().match("category", newrootcategory.getId()).match("id", "102").searchOne();
		assertNotNull(found);


		//Finally revert back to version 0
		
	}
	
	
	
	
	
	
}