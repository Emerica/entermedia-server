package importing

import org.entermediadb.asset.Asset
import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.scanner.MetaDataReader
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.page.Page

public void init()
{
		MediaArchive archive = context.getPageValue("mediaarchive");//Search for all files looking for videos
		Searcher searcher = archive.getAssetSearcher();
		//HitTracker assets = searcher.getAllHits();
		HitTracker assets = searcher.query().match("category","index").not("editstatus","7").sort("id").search();
		assets.enableBulkOperations();
		String ids = context.getRequestParameter("assetids");
		if( ids != null )
		{
			String[] assetids = ids.split(",");
			assets.setSelections(Arrays.asList( assetids) );
			assets.setShowOnlySelected(true);
		}

		List assetsToSave = new ArrayList();
		MetaDataReader reader = moduleManager.getBean("metaDataReader");
		for (Data hit in assets)
		{
			Asset asset = archive.getAssetBySourcePath(hit.get("sourcepath"));

			if( asset != null)
			{
				Page content = archive.getOriginalDocument( asset );
				reader.populateAsset(archive, content.getContentItem(), asset);
				assetsToSave.add(asset);
				if(assetsToSave.size() == 1000)
				{
					archive.saveAssets( assetsToSave );
					assetsToSave.clear();
					log.info("saved 1000 metadata readings");
				}
			}
		}
		archive.saveAssets assetsToSave;
		log.info("metadata reading complete");
}

init();