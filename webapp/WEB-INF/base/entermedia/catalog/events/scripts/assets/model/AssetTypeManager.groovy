package assets.model;

import org.openedit.data.Searcher
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.entermedia.*
import org.openedit.entermedia.creator.*
import org.openedit.entermedia.edit.*
import org.openedit.entermedia.episode.*
import org.openedit.entermedia.modules.*
import org.openedit.entermedia.search.AssetSearcher
import org.openedit.xml.*

import com.openedit.entermedia.scripts.EnterMediaObject;
import com.openedit.hittracker.*
import com.openedit.page.*
import com.openedit.util.*

import conversions.*

public class AssetTypeManager extends EnterMediaObject {
	public void saveAssetTypes(Collection inAssets) {
		MediaArchive mediaarchive = (MediaArchive)context.getPageValue("mediaarchive");//Search for all files looking for videos

		AssetSearcher searcher = mediaarchive.getAssetSearcher();

		
		List tosave = new ArrayList();
		for (Data hit in inAssets)
		{
			Asset real = checkForEdits( mediaarchive, hit);
			if( real == null )
			{
				real = checkLibrary(mediaarchive,hit);
			}
			else
			{
				checkLibrary(mediaarchive,real);
			}
			real = checkCustomFields(mediaarchive,hit,real);
			if(real != null)
			{
				tosave.add(real);
			}
			if(tosave.size() == 100)
			{
				saveAssets(searcher, tosave);
				tosave.clear();
			}

		}
		saveAssets(searcher, tosave);
	}
	protected Asset checkCustomFields(MediaArchive inArchive, Data inHit, Asset loadedAsset)
	{
		return loadedAsset;
	}
	public Asset checkForEdits(MediaArchive inArchive,  Data hit)
	{
		String sourcepath = hit.sourcepath;
		Searcher typesearcher = inArchive.getSearcher("assettype");
		String fileformat = hit.fileformat;
		//First check to see if we have a path mask for the asset type

		HitTracker types = typesearcher.getAllHits();
		types.each{
			String paths =it.paths;
			String type = it.id;
			
			if(paths != null){
				String [] splits = paths.split(',');
				splits.each{

					if(PathUtilities.match(hit.sourcepath, it)){
						Asset real = inArchive.getAssetSearcher().loadData(hit);
						real.setProperty("assettype", type);
						return real;
					}
				}
			}
		}

		//now check extensions
		HashMap typemap = new HashMap();
		types.each{
			String extentions = it.extensions;
			String type = it.id;
			if(extentions != null){
				String[] splits = extentions.split(" ");
				splits.each{
					typemap.put(it, type)
				}
			}
		}

		String currentassettype = hit.assettype;
		String assettype = typemap.get(fileformat);
		if(assettype == null)
		{
			assettype = "none";
		}
		assettype = findCorrectAssetType(hit,assettype);
		if(!assettype.equals(currentassettype))
		{
			Asset real = inArchive.getAssetSearcher().loadData(hit);
			real.setProperty("assettype", assettype);
			return real;
		}
		return null;
	}
	public Asset checkLibrary(MediaArchive mediaarchive, Data hit)
	{
		//Load up asset if needed to change the library?
		return null;
	}
	public Asset checkLibrary(MediaArchive mediaarchive, Asset real)
	{
		//Load up asset if needed to change the library?
		return real;
	}

	public void saveAssets(Searcher inSearcher, Collection tosave)
	{
		//Do any other checks on the asset. Add to library?
		inSearcher.saveAllData(tosave, context.getUser());
	}
	public String findCorrectAssetType(Data inAssetHit, String inSuggested)
	{
		/*		String path = inAssetHit.getSourcePath().toLowercase();
		 if( path.contains("/links/") )
		 {
		 return "links";
		 }
		 if( path.contains("/press ready pdf/") || path.endsWith("_pfinal.pdf") )
		 {
		 return "printreadyfinal";
		 }
		 */		
		return inSuggested;
	}

}